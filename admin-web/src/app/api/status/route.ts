import { NextResponse } from "next/server";
import { requireSession } from "@/lib/apiGuard";
import { listParts } from "@/lib/db";
import { lookupPlaylist } from "@/lib/youtube";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 30; // up to N parallel YouTube lookups

export interface PartStatus {
  id: string;
  title: string;
  isActive: boolean;
  playlistId: string;
  videoCount: number | null; // null when the playlist can't be read
  ok: boolean;
  warning: string | null;
}

/** Live per-part playlist health: video counts + a warning if a playlist went missing. */
export async function GET() {
  const guard = await requireSession();
  if (guard) return guard;

  const parts = await listParts();
  const statuses: PartStatus[] = await Promise.all(
    parts.map(async (p) => {
      const result = await lookupPlaylist(p.playlist_id);
      if (result.ok) {
        return {
          id: p.id,
          title: p.title,
          isActive: p.is_active,
          playlistId: p.playlist_id,
          videoCount: result.info.itemCount,
          ok: true,
          warning:
            result.info.itemCount === 0
              ? "재생목록에 영상이 없어요. 태블릿에서 빈 목록으로 보일 수 있어요."
              : null,
        };
      }
      return {
        id: p.id,
        title: p.title,
        isActive: p.is_active,
        playlistId: p.playlist_id,
        videoCount: null,
        ok: false,
        warning:
          result.reason === "not_found"
            ? "재생목록을 찾을 수 없어요. 유튜브에서 비공개로 바뀌었거나 삭제된 것 같아요. 태블릿이 빈 화면이 될 수 있어요."
            : "지금은 재생목록 상태를 확인하지 못했어요. 잠시 후 다시 확인해주세요.",
      };
    }),
  );

  return NextResponse.json({ statuses });
}
