// Thin server-only Supabase data layer using PostgREST + Storage REST directly with the
// service_role key. All writes to parts/settings/web_auth go through here — the service
// role bypasses RLS, and this module never runs in the browser.
import "server-only";
import { env } from "./env";

const SVC = () => env.serviceRoleKey();
const BASE = () => env.supabaseUrl();

function restHeaders(extra?: Record<string, string>): Record<string, string> {
  const key = SVC();
  return {
    apikey: key,
    Authorization: `Bearer ${key}`,
    "Content-Type": "application/json",
    ...extra,
  };
}

// ---------------------------------------------------------------------------
// Types mirroring the DB rows we touch.
// ---------------------------------------------------------------------------
export interface PartRow {
  id: string;
  position: number;
  title: string;
  thumbnail_url: string | null;
  playlist_id: string;
  is_active: boolean;
}

export interface SettingsRow {
  id: number;
  idle_return_seconds: number;
  admin_pin_hash: string | null;
  header_text: string | null;
}

export interface WebAuthRow {
  id: number;
  password_hash: string;
  is_default: boolean;
  failed_count: number;
  locked_until: string | null;
}

// ---------------------------------------------------------------------------
// Generic PostgREST helpers
// ---------------------------------------------------------------------------
async function rest(path: string, init: RequestInit & { headers?: Record<string, string> } = {}) {
  const res = await fetch(`${BASE()}/rest/v1/${path}`, {
    ...init,
    headers: restHeaders(init.headers),
    cache: "no-store",
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`PostgREST ${res.status} on ${path}: ${body}`);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

// ---------------------------------------------------------------------------
// parts
// ---------------------------------------------------------------------------
export async function listParts(): Promise<PartRow[]> {
  return (await rest("parts?select=*&order=position.asc")) as PartRow[];
}

export async function getPart(id: string): Promise<PartRow | null> {
  const rows = (await rest(`parts?id=eq.${id}&select=*`)) as PartRow[];
  return rows[0] ?? null;
}

export async function maxPosition(): Promise<number> {
  const rows = (await rest("parts?select=position&order=position.desc&limit=1")) as PartRow[];
  return rows[0]?.position ?? 0;
}

export async function insertPart(row: {
  position: number;
  title: string;
  thumbnail_url: string | null;
  playlist_id: string;
  is_active: boolean;
}): Promise<PartRow> {
  const rows = (await rest("parts", {
    method: "POST",
    headers: { Prefer: "return=representation" },
    body: JSON.stringify(row),
  })) as PartRow[];
  return rows[0];
}

export async function updatePart(
  id: string,
  patch: Partial<Pick<PartRow, "title" | "thumbnail_url" | "playlist_id" | "is_active" | "position">>,
): Promise<PartRow> {
  const rows = (await rest(`parts?id=eq.${id}`, {
    method: "PATCH",
    headers: { Prefer: "return=representation" },
    body: JSON.stringify(patch),
  })) as PartRow[];
  return rows[0];
}

export async function deletePart(id: string): Promise<void> {
  await rest(`parts?id=eq.${id}`, { method: "DELETE" });
}

// ---------------------------------------------------------------------------
// settings (single row, id=1)
// ---------------------------------------------------------------------------
export async function getSettings(): Promise<SettingsRow> {
  const rows = (await rest("settings?id=eq.1&select=*")) as SettingsRow[];
  return rows[0];
}

export async function updateSettings(
  patch: Partial<Pick<SettingsRow, "idle_return_seconds" | "admin_pin_hash" | "header_text">>,
): Promise<SettingsRow> {
  const rows = (await rest("settings?id=eq.1", {
    method: "PATCH",
    headers: { Prefer: "return=representation" },
    body: JSON.stringify(patch),
  })) as SettingsRow[];
  return rows[0];
}

// ---------------------------------------------------------------------------
// web_auth (single row, id=1) — password hash + brute-force state
// ---------------------------------------------------------------------------
export async function getWebAuth(): Promise<WebAuthRow> {
  const rows = (await rest("web_auth?id=eq.1&select=*")) as WebAuthRow[];
  // 행이 비어 있으면 DB 상태 자체가 잘못된 것이다. 여기서 명시적으로 던져야 호출부(로그인)가
  // "비밀번호 불일치"가 아니라 "서버 문제"로 처리할 수 있다.
  if (!rows[0]) throw new Error("web_auth row (id=1) not found");
  return rows[0];
}

export async function updateWebAuth(
  patch: Partial<Pick<WebAuthRow, "password_hash" | "is_default" | "failed_count" | "locked_until">>,
): Promise<void> {
  await rest("web_auth?id=eq.1", { method: "PATCH", body: JSON.stringify(patch) });
}

// ---------------------------------------------------------------------------
// Storage (thumbnails bucket)
// ---------------------------------------------------------------------------
export function publicThumbnailUrl(path: string): string {
  return `${BASE()}/storage/v1/object/public/thumbnails/${path}`;
}

export async function uploadThumbnail(path: string, bytes: Buffer, contentType: string): Promise<string> {
  const key = SVC();
  const res = await fetch(`${BASE()}/storage/v1/object/thumbnails/${path}`, {
    method: "POST",
    headers: {
      apikey: key,
      Authorization: `Bearer ${key}`,
      "Content-Type": contentType,
      "x-upsert": "true",
      "cache-control": "3600",
    },
    body: new Uint8Array(bytes),
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Storage upload ${res.status}: ${await res.text()}`);
  }
  return publicThumbnailUrl(path);
}

/**
 * Delete a thumbnail given its public URL. Best-effort: a missing object is not an error
 * (we still want the part delete to succeed). Only touches objects in our bucket.
 */
export async function deleteThumbnailByUrl(url: string | null): Promise<void> {
  if (!url) return;
  const marker = "/storage/v1/object/public/thumbnails/";
  const idx = url.indexOf(marker);
  if (idx === -1) return; // not one of ours (e.g. bundled/null) — nothing to delete
  // Strip any cache-bust query (?v=...) so we target the real object key.
  const path = url.slice(idx + marker.length).split("?")[0];
  if (!path) return;
  const key = SVC();
  await fetch(`${BASE()}/storage/v1/object/thumbnails/${path}`, {
    method: "DELETE",
    headers: { apikey: key, Authorization: `Bearer ${key}` },
    cache: "no-store",
  }).catch(() => undefined);
}
