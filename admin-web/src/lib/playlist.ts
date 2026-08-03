// Pure helpers for turning whatever the operator pastes into a bare YouTube playlist id.
// We never store a full URL — only the `list=` value (or a bare id) — so a stray tracking
// param or a `watch?v=...&list=...` URL can't corrupt the parts table.

// A real playlist id: PL / UU / FL / OL / RD ... — alphanumeric, dash, underscore.
// We validate the shape but stay permissive on length (YouTube ids vary).
const PLAYLIST_ID_RE = /^[A-Za-z0-9_-]{12,64}$/;

/**
 * Extract a playlist id from a pasted URL or a bare id.
 * Returns the id, or null if nothing that looks like a playlist id is present.
 */
export function extractPlaylistId(input: string): string | null {
  const trimmed = (input ?? "").trim();
  if (!trimmed) return null;

  // Case 1: a bare id was pasted (no scheme, no query).
  if (!trimmed.includes("/") && !trimmed.includes("?") && !trimmed.includes("=")) {
    return PLAYLIST_ID_RE.test(trimmed) ? trimmed : null;
  }

  // Case 2: a URL (or anything with a query string). Pull the `list` param.
  let listValue: string | null = null;
  try {
    const url = new URL(trimmed.includes("://") ? trimmed : `https://${trimmed}`);
    listValue = url.searchParams.get("list");
  } catch {
    listValue = null;
  }

  // Fallback: regex the raw string for `list=...` if URL parsing missed it.
  if (!listValue) {
    const m = trimmed.match(/[?&]list=([A-Za-z0-9_-]+)/);
    listValue = m ? m[1] : null;
  }

  if (!listValue) return null;
  return PLAYLIST_ID_RE.test(listValue) ? listValue : null;
}
