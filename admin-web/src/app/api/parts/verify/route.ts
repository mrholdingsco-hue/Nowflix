import { NextRequest, NextResponse } from "next/server";
import { requireSession } from "@/lib/apiGuard";
import { extractPlaylistId } from "@/lib/playlist";
import { lookupPlaylist } from "@/lib/youtube";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Verify a pasted playlist URL exists before the operator commits to saving it. */
export async function POST(req: NextRequest) {
  const guard = await requireSession();
  if (guard) return guard;

  const { url } = (await req.json().catch(() => ({}))) as { url?: string };
  const playlistId = extractPlaylistId(url ?? "");
  if (!playlistId) {
    return NextResponse.json(
      { error: "유튜브 재생목록 주소가 올바르지 않아요. list= 가 포함된 주소를 붙여넣어 주세요." },
      { status: 400 },
    );
  }

  const result = await lookupPlaylist(playlistId);
  if (!result.ok) {
    return NextResponse.json({ error: result.message }, { status: result.reason === "not_found" ? 404 : 502 });
  }

  return NextResponse.json({
    playlistId: result.info.playlistId,
    title: result.info.title,
    itemCount: result.info.itemCount,
  });
}
