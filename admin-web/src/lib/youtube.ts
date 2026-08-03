// Server-only YouTube Data API v3 access: verify a playlist exists and read its live
// video count + title. Used both when adding/editing a part and on the status screen.
import "server-only";
import { env } from "./env";

export interface PlaylistInfo {
  playlistId: string;
  title: string;
  itemCount: number;
}

export type PlaylistLookup =
  | { ok: true; info: PlaylistInfo }
  | { ok: false; reason: "not_found" | "error"; message: string };

/**
 * Look up a playlist via playlists.list. A private or deleted playlist returns an empty
 * `items` array (reason=not_found) so callers can block the save / warn the operator.
 */
export async function lookupPlaylist(playlistId: string): Promise<PlaylistLookup> {
  const key = env.youtubeApiKey();
  const url =
    `https://www.googleapis.com/youtube/v3/playlists` +
    `?part=snippet,contentDetails&id=${encodeURIComponent(playlistId)}&key=${key}`;

  let res: Response;
  try {
    res = await fetch(url, { cache: "no-store" });
  } catch {
    return { ok: false, reason: "error", message: "유튜브에 연결하지 못했어요. 잠시 후 다시 시도해주세요." };
  }

  if (!res.ok) {
    return { ok: false, reason: "error", message: "유튜브 확인 중 문제가 생겼어요. 잠시 후 다시 시도해주세요." };
  }

  const data = (await res.json()) as {
    items?: Array<{
      snippet?: { title?: string };
      contentDetails?: { itemCount?: number };
    }>;
  };

  const item = data.items?.[0];
  if (!item) {
    return {
      ok: false,
      reason: "not_found",
      message: "재생목록을 찾을 수 없어요. 비공개이거나 삭제되었을 수 있어요. 주소를 다시 확인해주세요.",
    };
  }

  return {
    ok: true,
    info: {
      playlistId,
      title: item.snippet?.title ?? "(제목 없음)",
      itemCount: item.contentDetails?.itemCount ?? 0,
    },
  };
}
