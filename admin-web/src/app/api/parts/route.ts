import { NextRequest, NextResponse } from "next/server";
import { requireSession } from "@/lib/apiGuard";
import { extractPlaylistId } from "@/lib/playlist";
import { lookupPlaylist } from "@/lib/youtube";
import { processAndUpload } from "@/lib/storage";
import { insertPart, maxPosition, updatePart, listParts } from "@/lib/db";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 30; // playlist verify + image resize/upload

export async function GET() {
  const guard = await requireSession();
  if (guard) return guard;
  return NextResponse.json({ parts: await listParts() });
}

/** Add a part: verify playlist -> insert -> (optional) upload thumbnail -> attach. */
export async function POST(req: NextRequest) {
  const guard = await requireSession();
  if (guard) return guard;

  const form = await req.formData();
  const title = String(form.get("title") ?? "").trim();
  const url = String(form.get("playlistUrl") ?? "");
  const image = form.get("image");

  if (!title) {
    return NextResponse.json({ error: "파트 이름을 입력해주세요." }, { status: 400 });
  }

  const playlistId = extractPlaylistId(url);
  if (!playlistId) {
    return NextResponse.json(
      { error: "유튜브 재생목록 주소가 올바르지 않아요." },
      { status: 400 },
    );
  }

  // Re-verify server-side so a stale/invalid playlist can never be saved.
  const check = await lookupPlaylist(playlistId);
  if (!check.ok) {
    return NextResponse.json({ error: check.message }, { status: check.reason === "not_found" ? 404 : 502 });
  }

  const position = (await maxPosition()) + 1;
  const part = await insertPart({
    position,
    title,
    thumbnail_url: null,
    playlist_id: playlistId,
    is_active: true,
  });

  // Attach a thumbnail if provided. A bad image fails the upload but keeps the part
  // (operator can add the image later); we surface the reason.
  if (image instanceof File && image.size > 0) {
    const processed = await processAndUpload(image, part.id);
    if (!processed.ok) {
      return NextResponse.json({ ok: true, part, imageWarning: processed.error });
    }
    const updated = await updatePart(part.id, { thumbnail_url: processed.url! });
    return NextResponse.json({ ok: true, part: updated });
  }

  return NextResponse.json({ ok: true, part });
}
