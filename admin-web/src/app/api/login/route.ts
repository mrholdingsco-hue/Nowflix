import { NextRequest, NextResponse } from "next/server";
import { attemptLogin, type LoginResult } from "@/lib/auth";
import { createSessionToken, setSessionCookie } from "@/lib/session";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(req: NextRequest) {
  const { password } = (await req.json().catch(() => ({}))) as { password?: string };
  if (!password) {
    return NextResponse.json({ error: "비밀번호를 입력해주세요." }, { status: 400 });
  }

  // attemptLogin 은 비밀번호 불일치를 예외가 아니라 결과값으로 돌려준다. 여기서 잡히는 예외는
  // Supabase 연결 실패 같은 서버 측 문제뿐이므로(2026-08-25 무료 플랜 자동 일시정지 장애),
  // 401 과 구분해 503 으로 응답한다. 영문 원문·스택은 서버 로그에만 남긴다.
  let result: LoginResult;
  try {
    result = await attemptLogin(password);
  } catch (err) {
    console.error("[login] 로그인 처리 실패 (DB 연결 문제로 추정):", err);
    return NextResponse.json(
      { error: "일시적인 서버 문제입니다. 잠시 후 다시 시도해주세요." },
      { status: 503 },
    );
  }

  if (!result.ok) {
    if (result.locked) {
      return NextResponse.json(
        { error: `비밀번호를 5번 틀렸어요. ${result.secondsLeft}초 후에 다시 시도해주세요.`, secondsLeft: result.secondsLeft },
        { status: 429 },
      );
    }
    return NextResponse.json({ error: "비밀번호가 올바르지 않아요." }, { status: 401 });
  }

  const token = await createSessionToken();
  await setSessionCookie(token);
  return NextResponse.json({ ok: true, mustChangePassword: result.isDefault });
}
