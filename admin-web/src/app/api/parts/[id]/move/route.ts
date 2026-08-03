import { NextRequest, NextResponse } from "next/server";
import { requireSession } from "@/lib/apiGuard";
import { listParts, updatePart } from "@/lib/db";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Move a part up/down by swapping its `position` with the adjacent part. */
export async function POST(req: NextRequest, { params }: { params: { id: string } }) {
  const guard = await requireSession();
  if (guard) return guard;

  const { direction } = (await req.json().catch(() => ({}))) as { direction?: "up" | "down" };
  if (direction !== "up" && direction !== "down") {
    return NextResponse.json({ error: "방향이 올바르지 않아요." }, { status: 400 });
  }

  const parts = await listParts(); // ordered by position asc
  const idx = parts.findIndex((p) => p.id === params.id);
  if (idx === -1) return NextResponse.json({ error: "파트를 찾을 수 없어요." }, { status: 404 });

  const swapIdx = direction === "up" ? idx - 1 : idx + 1;
  if (swapIdx < 0 || swapIdx >= parts.length) {
    return NextResponse.json({ ok: true, noop: true }); // already at the edge
  }

  const a = parts[idx];
  const b = parts[swapIdx];
  // Swap positions. Two independent single-row updates; positions are just an ordering key.
  await updatePart(a.id, { position: b.position });
  await updatePart(b.id, { position: a.position });

  return NextResponse.json({ ok: true });
}
