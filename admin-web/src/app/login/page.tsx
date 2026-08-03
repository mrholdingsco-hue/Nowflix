"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button, Card, Field, Input, Banner, Spinner } from "@/components/ui";

export default function LoginPage() {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch("/api/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password }),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error ?? "로그인에 실패했어요.");
        setLoading(false);
        return;
      }
      // On a first login with the default password, land on settings with the change prompt.
      router.push(data.mustChangePassword ? "/settings?changePw=1" : "/parts");
    } catch {
      setError("연결에 문제가 생겼어요. 잠시 후 다시 시도해주세요.");
      setLoading(false);
    }
  }

  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <div className="mx-auto mb-3 inline-flex h-11 items-center rounded-xl bg-ink px-4 text-lg font-extrabold tracking-tight text-white">
            NOWFLIX
          </div>
          <h1 className="text-xl font-bold text-ink">로비 태블릿 관리자</h1>
          <p className="mt-1 text-sm text-subtle">병원 담당자 전용 페이지예요.</p>
        </div>

        <Card>
          <form onSubmit={onSubmit} className="space-y-4">
            <Field label="비밀번호">
              <Input
                type="password"
                autoComplete="current-password"
                autoFocus
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="비밀번호를 입력하세요"
              />
            </Field>

            {error && <Banner tone="danger">{error}</Banner>}

            <Button type="submit" className="w-full" disabled={loading || !password}>
              {loading ? <Spinner /> : "로그인"}
            </Button>
          </form>
        </Card>

        <p className="mt-4 text-center text-xs text-subtle">
          처음 접속하셨다면 로그인 후 비밀번호를 꼭 변경해주세요.
        </p>
      </div>
    </main>
  );
}
