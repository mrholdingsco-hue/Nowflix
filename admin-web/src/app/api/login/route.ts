import { NextRequest, NextResponse } from "next/server";
import { attemptLogin } from "@/lib/auth";
import { createSessionToken, setSessionCookie } from "@/lib/session";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(req: NextRequest) {
  const { password } = (await req.json().catch(() => ({}))) as { password?: string };
  if (!password) {
    return NextResponse.json({ error: "비밀번호를 입력해주세요." }, { status: 400 });
  }

  const result = await attemptLogin(password);
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
