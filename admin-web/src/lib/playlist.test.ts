import { describe, it, expect } from "vitest";
import { extractPlaylistId } from "./playlist";

const REAL = "PLfxolKs8oDR66iswGEXMQz10w1seo8O80";

describe("extractPlaylistId", () => {
  it("returns a bare id unchanged", () => {
    expect(extractPlaylistId(REAL)).toBe(REAL);
  });

  it("trims surrounding whitespace", () => {
    expect(extractPlaylistId(`  ${REAL}  `)).toBe(REAL);
  });

  it("extracts list= from a playlist URL", () => {
    expect(extractPlaylistId(`https://www.youtube.com/playlist?list=${REAL}`)).toBe(REAL);
  });

  it("extracts list= from a watch URL with other params", () => {
    const url = `https://www.youtube.com/watch?v=abc123XYZ00&list=${REAL}&index=2`;
    expect(extractPlaylistId(url)).toBe(REAL);
  });

  it("handles a URL without a scheme", () => {
    expect(extractPlaylistId(`youtube.com/playlist?list=${REAL}`)).toBe(REAL);
  });

  it("ignores unrelated query params (v= is not a playlist)", () => {
    expect(extractPlaylistId("https://www.youtube.com/watch?v=abc123XYZ00")).toBeNull();
  });

  it("returns null for empty / junk input", () => {
    expect(extractPlaylistId("")).toBeNull();
    expect(extractPlaylistId("   ")).toBeNull();
    expect(extractPlaylistId("not a playlist")).toBeNull();
  });

  it("rejects a too-short bare token", () => {
    expect(extractPlaylistId("PL123")).toBeNull();
  });

  it("rejects a list= value with illegal characters", () => {
    expect(extractPlaylistId("https://youtube.com/playlist?list=abc!!def@@ghi##")).toBeNull();
  });
});
