// Server-side upload rules for part thumbnails. The pure `validateUpload` is unit-tested;
// the actual resize (sharp) lives in storage.ts and runs only after validation passes.

export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024; // 5 MB
export const ALLOWED_MIME = ["image/jpeg", "image/png"] as const;
export const TARGET_HEIGHT = 900; // resize to 900px tall (poster orientation)

export interface UploadMeta {
  mime: string;
  size: number; // bytes
}

export interface ValidationResult {
  ok: boolean;
  error?: string; // Korean, operator-facing
}

/** Validate a proposed thumbnail upload by MIME type and size. Pure. */
export function validateUpload(meta: UploadMeta): ValidationResult {
  if (!ALLOWED_MIME.includes(meta.mime as (typeof ALLOWED_MIME)[number])) {
    return { ok: false, error: "JPG 또는 PNG 이미지만 올릴 수 있어요." };
  }
  if (meta.size <= 0) {
    return { ok: false, error: "이미지 파일이 비어 있어요." };
  }
  if (meta.size > MAX_UPLOAD_BYTES) {
    return { ok: false, error: "이미지 용량이 너무 커요. 5MB 이하로 올려주세요." };
  }
  return { ok: true };
}
