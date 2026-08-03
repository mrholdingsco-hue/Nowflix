import { describe, it, expect } from "vitest";
import { validateUpload, MAX_UPLOAD_BYTES } from "./image";

describe("validateUpload", () => {
  it("accepts a normal JPG", () => {
    expect(validateUpload({ mime: "image/jpeg", size: 1_000_000 })).toEqual({ ok: true });
  });

  it("accepts a normal PNG", () => {
    expect(validateUpload({ mime: "image/png", size: 500_000 })).toEqual({ ok: true });
  });

  it("rejects a non image/* type", () => {
    const r = validateUpload({ mime: "application/pdf", size: 1000 });
    expect(r.ok).toBe(false);
    expect(r.error).toContain("JPG");
  });

  it("rejects gif/webp (only jpg/png allowed)", () => {
    expect(validateUpload({ mime: "image/gif", size: 1000 }).ok).toBe(false);
    expect(validateUpload({ mime: "image/webp", size: 1000 }).ok).toBe(false);
  });

  it("rejects an empty file", () => {
    expect(validateUpload({ mime: "image/png", size: 0 }).ok).toBe(false);
  });

  it("accepts a file exactly at the 5MB limit", () => {
    expect(validateUpload({ mime: "image/png", size: MAX_UPLOAD_BYTES }).ok).toBe(true);
  });

  it("rejects a file just over the 5MB limit", () => {
    const r = validateUpload({ mime: "image/png", size: MAX_UPLOAD_BYTES + 1 });
    expect(r.ok).toBe(false);
    expect(r.error).toContain("5MB");
  });
});
