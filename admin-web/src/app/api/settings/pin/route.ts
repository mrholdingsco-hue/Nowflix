import { NextRequest, NextResponse } from "next/server";
import { createHash } from "crypto";
import { requireSession } from "@/lib/apiGuard";
import { updateSettings } from "@/lib/db";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * Change the in-app admin PIN. Stored as SHA-256 hex (lowercase) to match the app's
 * PinGate, which hashes the entered PIN with SHA-256 and compares against this value.
 */
export async function POST(req: NextRequest) {
  const guard = await requireSession();
  if (guard) return guard;

  const { pin } = (await req.json().catch(() => ({}))) as { pin?: string };
  if (!pin || !/^\d{6}$/.test(pin)) {
    return NextResponse.json({ error: "PIN은 숫자 6자리로 정해주세요." }, { status: 400 });
  }

  const hash = createHash("sha256").update(pin, "utf8").digest("hex");
  await updateSettings({ admin_pin_hash: hash });
  return NextResponse.json({ ok: true });
}
