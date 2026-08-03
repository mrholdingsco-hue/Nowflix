import { NextRequest, NextResponse } from "next/server";
import { requireSession } from "@/lib/apiGuard";
import { extractPlaylistId } from "@/lib/playlist";
import { lookupPlaylist } from "@/lib/youtube";
import { processAndUpload } from "@/lib/storage";
import { getPart, updatePart, deletePart, deleteThumbnailByUrl } from "@/lib/db";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 30; // playlist verify + image resize/upload

/** Edit a part: title, playlist (re-verified), is_active toggle, optional new thumbnail. */
export async function PATCH(req: NextRequest, { params }: { params: { id: string } }) {
  const guard = await requireSession();
  if (guard) return guard;

  const existing = await getPart(params.id);
  if (!existing) return NextResponse.json({ error: "파트를 찾을 수 없어요." }, { status: 404 });

  const form = await req.formData();
  const patch: Record<string, unknown> = {};

  if (form.has("title")) {
    const title = String(form.get("title") ?? "").trim();
    if (!title) return NextResponse.json({ error: "파트 이름을 입력해주세요." }, { status: 400 });
    patch.title = title;
  }

  if (form.has("isActive")) {
    patch.is_active = String(form.get("isActive")) === "true";
  }

  if (form.has("playlistUrl")) {
    const url = String(form.get("playlistUrl") ?? "");
    const playlistId = extractPlaylistId(url);
    if (!playlistId) {
      return NextResponse.json({ error: "유튜브 재생목록 주소가 올바르지 않아요." }, { status: 400 });
    }
    if (playlistId !== existing.playlist_id) {
      const check = await lookupPlaylist(playlistId);
      if (!check.ok) {
        return NextResponse.json({ error: check.message }, { status: check.reason === "not_found" ? 404 : 502 });
      }
    }
    patch.playlist_id = playlistId;
  }

  const image = form.get("image");
  if (image instanceof File && image.size > 0) {
    const processed = await processAndUpload(image, existing.id);
    if (!processed.ok) return NextResponse.json({ error: processed.error }, { status: 400 });
    patch.thumbnail_url = processed.url!;
  }

  if (Object.keys(patch).length === 0) {
    return NextResponse.json({ ok: true, part: existing });
  }

  const updated = await updatePart(params.id, patch);
  return NextResponse.json({ ok: true, part: updated });
}

/** Delete a part and its thumbnail. Client requires a typed confirmation before calling. */
export async function DELETE(req: NextRequest, { params }: { params: { id: string } }) {
  const guard = await requireSession();
  if (guard) return guard;

  const existing = await getPart(params.id);
  if (!existing) return NextResponse.json({ error: "파트를 찾을 수 없어요." }, { status: 404 });

  await deleteThumbnailByUrl(existing.thumbnail_url);
  await deletePart(params.id);
  return NextResponse.json({ ok: true });
}
