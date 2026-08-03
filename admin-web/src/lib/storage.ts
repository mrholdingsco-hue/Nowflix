// Server-only image processing: validate, resize to 900px tall, upload to the thumbnails
// bucket, return the public URL. sharp runs only here (server route handlers).
import "server-only";
import sharp from "sharp";
import { validateUpload, TARGET_HEIGHT } from "./image";
import { uploadThumbnail } from "./db";

export interface ProcessedUpload {
  ok: boolean;
  url?: string;
  error?: string;
}

/**
 * Validate + resize + upload a thumbnail. `objectName` is a stable id-based path (no
 * user text) so we never trust the original filename. Always writes JPEG to keep the
 * bucket consistent and small.
 */
export async function processAndUpload(
  file: File,
  objectName: string,
): Promise<ProcessedUpload> {
  const valid = validateUpload({ mime: file.type, size: file.size });
  if (!valid.ok) return { ok: false, error: valid.error };

  const input = Buffer.from(await file.arrayBuffer());

  let output: Buffer;
  try {
    output = await sharp(input)
      .rotate() // honour EXIF orientation before resizing
      .resize({ height: TARGET_HEIGHT, withoutEnlargement: true })
      .jpeg({ quality: 82 })
      .toBuffer();
  } catch {
    return { ok: false, error: "이미지를 처리하지 못했어요. 다른 파일로 다시 시도해주세요." };
  }

  const path = `${objectName}.jpg`;
  try {
    const url = await uploadThumbnail(path, output, "image/jpeg");
    // Cache-bust so a replaced image shows immediately despite the CDN cache.
    return { ok: true, url: `${url}?v=${output.length}` };
  } catch {
    return { ok: false, error: "이미지를 저장하지 못했어요. 잠시 후 다시 시도해주세요." };
  }
}
