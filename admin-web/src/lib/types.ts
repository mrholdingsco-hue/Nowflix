// Shared client/server row shapes (no server-only imports, safe in client components).
export interface Part {
  id: string;
  position: number;
  title: string;
  thumbnail_url: string | null;
  playlist_id: string;
  is_active: boolean;
}

export interface PartStatus {
  id: string;
  title: string;
  isActive: boolean;
  playlistId: string;
  videoCount: number | null;
  ok: boolean;
  warning: string | null;
}
