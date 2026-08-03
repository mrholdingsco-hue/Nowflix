// Guard for API route handlers: return null when authenticated, or a 401 response to
// return early when not. Every mutating route calls this first.
import "server-only";
import { NextResponse } from "next/server";
import { hasValidSession } from "./session";

export async function requireSession(): Promise<NextResponse | null> {
  if (await hasValidSession()) return null;
  return NextResponse.json({ error: "로그인이 필요해요." }, { status: 401 });
}
