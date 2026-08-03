// Server-only login/password service. Combines the pure rate-limit logic with the
// bcrypt check and the web_auth row. Never leaks whether the password or the lock was
// the reason beyond what the operator needs.
import "server-only";
import bcrypt from "bcryptjs";
import { getWebAuth, updateWebAuth } from "./db";
import { lockStatus, registerFailure, registerSuccess } from "./rateLimit";

export type LoginResult =
  | { ok: true; isDefault: boolean }
  | { ok: false; locked: boolean; secondsLeft: number };

function toState(row: { failed_count: number; locked_until: string | null }) {
  return {
    failedCount: row.failed_count,
    lockedUntil: row.locked_until ? new Date(row.locked_until).getTime() : null,
  };
}

/** Attempt a login. Enforces the 5-fail/60s lock and returns the outcome. */
export async function attemptLogin(password: string): Promise<LoginResult> {
  const now = Date.now();
  const row = await getWebAuth();
  const state = toState(row);

  const status = lockStatus(state, now);
  if (status.locked) {
    return { ok: false, locked: true, secondsLeft: status.secondsLeft };
  }

  const passwordOk = await bcrypt.compare(password, row.password_hash);
  if (!passwordOk) {
    const next = registerFailure(state, now);
    await updateWebAuth({
      failed_count: next.failedCount,
      locked_until: next.lockedUntil ? new Date(next.lockedUntil).toISOString() : null,
    });
    const after = lockStatus(next, now);
    return { ok: false, locked: after.locked, secondsLeft: after.secondsLeft };
  }

  // Success — clear the brute-force state.
  const cleared = registerSuccess();
  await updateWebAuth({ failed_count: cleared.failedCount, locked_until: null });
  return { ok: true, isDefault: row.is_default };
}

export type ChangePasswordResult =
  | { ok: true }
  | { ok: false; error: string };

/** Change the web login password after re-checking the current one. */
export async function changePassword(
  current: string,
  next: string,
): Promise<ChangePasswordResult> {
  const row = await getWebAuth();

  const currentOk = await bcrypt.compare(current, row.password_hash);
  if (!currentOk) {
    return { ok: false, error: "현재 비밀번호가 일치하지 않아요." };
  }
  if (next.length < 8) {
    return { ok: false, error: "새 비밀번호는 8자 이상으로 정해주세요." };
  }
  if (next === current) {
    return { ok: false, error: "기존과 다른 비밀번호로 정해주세요." };
  }

  const hash = await bcrypt.hash(next, 10);
  await updateWebAuth({ password_hash: hash, is_default: false });
  return { ok: true };
}

/** Whether the login password is still the shipped default (drives the change banner). */
export async function isPasswordDefault(): Promise<boolean> {
  const row = await getWebAuth();
  return row.is_default;
}
