// Server-only env access. Importing this from a client component would throw at build,
// which is exactly the guard we want: none of these may ever reach the browser.
import "server-only";

function required(name: string): string {
  const v = process.env[name];
  if (!v) throw new Error(`Missing required env var: ${name}`);
  return v;
}

export const env = {
  supabaseUrl: () => required("SUPABASE_URL"),
  supabaseAnonKey: () => required("SUPABASE_ANON_KEY"),
  serviceRoleKey: () => required("SUPABASE_SERVICE_ROLE_KEY"),
  youtubeApiKey: () => required("YOUTUBE_API_KEY"),
  sessionSecret: () => required("SESSION_SECRET"),
};
