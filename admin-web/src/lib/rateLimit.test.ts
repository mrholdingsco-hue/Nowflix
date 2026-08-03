import { describe, it, expect } from "vitest";
import {
  lockStatus,
  registerFailure,
  registerSuccess,
  MAX_FAILURES,
  LOCK_MS,
  type AttemptState,
} from "./rateLimit";

const T0 = 1_000_000;
const clean: AttemptState = { failedCount: 0, lockedUntil: null };

describe("registerFailure", () => {
  it("increments the counter below the threshold without locking", () => {
    let s = clean;
    for (let i = 1; i < MAX_FAILURES; i++) {
      s = registerFailure(s, T0);
      expect(s.failedCount).toBe(i);
      expect(s.lockedUntil).toBeNull();
    }
  });

  it("locks for 60s exactly on the 5th consecutive failure", () => {
    let s = clean;
    for (let i = 0; i < MAX_FAILURES; i++) s = registerFailure(s, T0);
    expect(s.lockedUntil).toBe(T0 + LOCK_MS);
    expect(s.failedCount).toBe(0); // counter resets for the next window
  });
});

describe("lockStatus", () => {
  it("reports not-locked for a clean state", () => {
    expect(lockStatus(clean, T0)).toEqual({ locked: false, secondsLeft: 0 });
  });

  it("reports locked with remaining seconds during the lock window", () => {
    const locked: AttemptState = { failedCount: 0, lockedUntil: T0 + LOCK_MS };
    expect(lockStatus(locked, T0)).toEqual({ locked: true, secondsLeft: 60 });
    expect(lockStatus(locked, T0 + 30_000).secondsLeft).toBe(30);
  });

  it("reports not-locked once the lock has expired", () => {
    const locked: AttemptState = { failedCount: 0, lockedUntil: T0 + LOCK_MS };
    expect(lockStatus(locked, T0 + LOCK_MS + 1)).toEqual({ locked: false, secondsLeft: 0 });
  });
});

describe("registerSuccess", () => {
  it("clears the counter and lock", () => {
    expect(registerSuccess()).toEqual(clean);
  });
});
