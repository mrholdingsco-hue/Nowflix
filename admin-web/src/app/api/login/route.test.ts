// 2026-08-25 장애 회귀 방지: DB 가 죽었을 때 로그인 API 가 비밀번호 불일치(401)와
// 구분되는 503 + 한국어 안내를 주고, 영문 원문·스택을 노출하지 않는지 확인한다.
import { describe, expect, it, vi, beforeEach } from "vitest";

const attemptLogin = vi.fn();
vi.mock("@/lib/auth", () => ({ attemptLogin: (...a: unknown[]) => attemptLogin(...a) }));
vi.mock("@/lib/session", () => ({
  createSessionToken: async () => "token",
  setSessionCookie: async () => undefined,
}));

import { POST } from "./route";

function req(body: unknown) {
  return new Request("http://localhost/api/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  }) as never;
}

describe("POST /api/login", () => {
  beforeEach(() => {
    attemptLogin.mockReset();
  });

  it("DB 장애는 503 + 한국어 안내로 응답한다", async () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    attemptLogin.mockImplementation(async () => {
      throw new TypeError("fetch failed");
    });

    const res = await POST(req({ password: "whatever" }));
    const body = await res.json();

    expect(res.status).toBe(503);
    expect(body.error).toBe("일시적인 서버 문제입니다. 잠시 후 다시 시도해주세요.");
    // 영문 오류/스택이 그대로 새어나가지 않아야 한다.
    expect(JSON.stringify(body)).not.toMatch(/fetch failed|TypeError|at /);
  });

  it("비밀번호 불일치는 401 로 구분된다", async () => {
    attemptLogin.mockResolvedValue({ ok: false, locked: false, secondsLeft: 0 });

    const res = await POST(req({ password: "wrong" }));
    expect(res.status).toBe(401);
    expect((await res.json()).error).toBe("비밀번호가 올바르지 않아요.");
  });

  it("정상 로그인은 200", async () => {
    attemptLogin.mockResolvedValue({ ok: true, isDefault: true });

    const res = await POST(req({ password: "right" }));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ ok: true, mustChangePassword: true });
  });
});
