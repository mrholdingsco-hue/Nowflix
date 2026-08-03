import { NextRequest, NextResponse } from "next/server";
import { requireSession } from "@/lib/apiGuard";
import { changePassword } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Change the web login password (requires the current one). */
export async function POST(req: NextRequest) {
  const guard = await requireSession();
  if (guard) return guard;

  const { current, next } = (await req.json().catch(() => ({}))) as {
    current?: string;
    next?: string;
  };
  if (!current || !next) {
    return NextResponse.json({ error: "현재 비밀번호와 새 비밀번호를 모두 입력해주세요." }, { status: 400 });
  }

  const result = await changePassword(current, next);
  if (!result.ok) {
    return NextResponse.json({ error: result.error }, { status: 400 });
  }
  return NextResponse.json({ ok: true });
}
