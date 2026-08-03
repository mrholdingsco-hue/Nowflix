// Pure brute-force guard for the single-account web login.
// Policy: after MAX_FAILURES consecutive wrong passwords, lock for LOCK_MS.
// State lives in the DB (web_auth.failed_count / locked_until) so it survives across
// serverless instances; these functions just compute the next state — no I/O, so they
// are unit-testable with an injected `now`.

export const MAX_FAILURES = 5;
export const LOCK_MS = 60_000; // 60 seconds

export interface AttemptState {
  failedCount: number;
  lockedUntil: number | null; // epoch millis, or null
}

export interface LockStatus {
  locked: boolean;
  secondsLeft: number; // 0 when not locked
}

/** Is the account currently locked, and for how many more seconds? */
export function lockStatus(state: AttemptState, now: number): LockStatus {
  if (state.lockedUntil != null && state.lockedUntil > now) {
    return { locked: true, secondsLeft: Math.ceil((state.lockedUntil - now) / 1000) };
  }
  return { locked: false, secondsLeft: 0 };
}

/**
 * Apply a wrong-password attempt. Increments the counter; once it reaches
 * MAX_FAILURES the account locks for LOCK_MS and the counter resets so the next
 * window starts clean after the lock expires.
 */
export function registerFailure(state: AttemptState, now: number): AttemptState {
  const failedCount = state.failedCount + 1;
  if (failedCount >= MAX_FAILURES) {
    return { failedCount: 0, lockedUntil: now + LOCK_MS };
  }
  return { failedCount, lockedUntil: null };
}

/** A correct password clears everything. */
export function registerSuccess(): AttemptState {
  return { failedCount: 0, lockedUntil: null };
}
