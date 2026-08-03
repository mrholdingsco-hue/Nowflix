// httpOnly session cookie, signed as a short JWT (jose). Single-account tool: the token
// just proves "someone logged in with the current password" and carries an expiry.
import "server-only";
import { SignJWT, jwtVerify } from "jose";
import { cookies } from "next/headers";
import { env } from "./env";

export const SESSION_COOKIE = "nf_admin";
const MAX_AGE_SECONDS = 8 * 60 * 60; // 8 hours

function secretKey(): Uint8Array {
  return new TextEncoder().encode(env.sessionSecret());
}

/** Create a signed session token (expires in 8h). */
export async function createSessionToken(): Promise<string> {
  return new SignJWT({ role: "admin" })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setExpirationTime(`${MAX_AGE_SECONDS}s`)
    .sign(secretKey());
}

/** Set the httpOnly session cookie on the response. */
export async function setSessionCookie(token: string): Promise<void> {
  cookies().set(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: MAX_AGE_SECONDS,
  });
}

export async function clearSessionCookie(): Promise<void> {
  cookies().delete(SESSION_COOKIE);
}

/** True if the current request carries a valid, unexpired session. */
export async function hasValidSession(): Promise<boolean> {
  const token = cookies().get(SESSION_COOKIE)?.value;
  if (!token) return false;
  try {
    await jwtVerify(token, secretKey());
    return true;
  } catch {
    return false;
  }
}
