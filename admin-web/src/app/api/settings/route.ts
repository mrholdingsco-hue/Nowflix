import { NextRequest, NextResponse } from "next/server";
import { requireSession } from "@/lib/apiGuard";
import { getSettings, updateSettings } from "@/lib/db";
import { IDLE_MIN, IDLE_MAX } from "@/lib/constants";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  const guard = await requireSession();
  if (guard) return guard;
  return NextResponse.json({ settings: await getSettings() });
}

/** Update the return-to-home time and header text. */
export async function PATCH(req: NextRequest) {
  const guard = await requireSession();
  if (guard) return guard;

  const body = (await req.json().catch(() => ({}))) as {
    idleReturnSeconds?: number;
    headerText?: string;
  };
  const patch: Record<string, unknown> = {};

  if (body.idleReturnSeconds !== undefined) {
    const n = Number(body.idleReturnSeconds);
    if (!Number.isInteger(n) || n < IDLE_MIN || n > IDLE_MAX) {
      return NextResponse.json(
        { error: `메인 복귀 시간은 ${IDLE_MIN}초에서 ${IDLE_MAX}초 사이로 정해주세요.` },
        { status: 400 },
      );
    }
    patch.idle_return_seconds = n;
  }

  if (body.headerText !== undefined) {
    const text = String(body.headerText).trim();
    if (text.length > 60) {
      return NextResponse.json({ error: "헤더 문구는 60자 이하로 정해주세요." }, { status: 400 });
    }
    patch.header_text = text;
  }

  if (Object.keys(patch).length === 0) {
    return NextResponse.json({ error: "변경할 내용이 없어요." }, { status: 400 });
  }

  const updated = await updateSettings(patch);
  return NextResponse.json({ ok: true, settings: updated });
}
