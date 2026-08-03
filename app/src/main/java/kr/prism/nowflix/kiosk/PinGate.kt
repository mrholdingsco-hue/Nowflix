package kr.prism.nowflix.kiosk

/** Outcome of a single PIN attempt. */
sealed interface PinResult {
    /** Correct PIN — caller opens the admin screen. */
    data object Accepted : PinResult

    /** Wrong PIN. [remaining] attempts left before the temporary lockout kicks in. */
    data class Rejected(val remaining: Int) : PinResult

    /** Too many wrong tries. No input accepted until [secondsLeft] elapses. */
    data class LockedOut(val secondsLeft: Int) : PinResult
}

/**
 * The admin-PIN escape gate. Pure and clock-injected so the lockout timing is unit-tested by
 * hand-cranking [clock]; the same class runs in the app with a monotonic clock.
 *
 * Rules (STEP 7 escape path #1):
 *  - [submit] hashes the entered PIN and compares it to [expectedHash].
 *  - [MAX_ATTEMPTS] consecutive wrong PINs -> locked for [LOCKOUT_MS]. This bounds brute force
 *    but is NEVER permanent: once the window passes, the counter resets and input works again.
 *  - A correct PIN clears the failure counter.
 *
 * The caller resolves [expectedHash] via [effectiveHash], which falls back to the built-in
 * [DEFAULT_ADMIN_PIN_HASH] when neither remote nor cached settings carry a hash (first run,
 * no network, no cache) — so the kiosk can always be unlocked with the default PIN 000000.
 */
class PinGate(private val clock: () -> Long) {

    private var failCount = 0
    private var lockedUntil = 0L

    fun submit(pin: String, expectedHash: String): PinResult {
        val now = clock()
        if (now < lockedUntil) {
            return PinResult.LockedOut(secondsLeftFrom(now))
        }

        return if (Sha256.hex(pin) == expectedHash) {
            failCount = 0
            lockedUntil = 0L
            PinResult.Accepted
        } else {
            failCount++
            if (failCount >= MAX_ATTEMPTS) {
                lockedUntil = now + LOCKOUT_MS
                failCount = 0 // fresh window after the lockout expires — never permanent
                PinResult.LockedOut(secondsLeftFrom(now))
            } else {
                PinResult.Rejected(remaining = MAX_ATTEMPTS - failCount)
            }
        }
    }

    /** Live lockout state for the UI (e.g. to keep the countdown ticking without a submit). */
    fun lockedSecondsLeft(): Int {
        val now = clock()
        return if (now < lockedUntil) secondsLeftFrom(now) else 0
    }

    private fun secondsLeftFrom(now: Long): Int {
        val ms = lockedUntil - now
        return ((ms + 999) / 1000).toInt() // round up so "1s left" never shows as 0
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L

        /** Built-in last-resort admin hash. Default PIN is 000000. */
        val DEFAULT_ADMIN_PIN_HASH: String = Sha256.hex("000000")

        /** Remote/cached hash if present, else the built-in default. */
        fun effectiveHash(settingsHash: String): String =
            settingsHash.ifBlank { DEFAULT_ADMIN_PIN_HASH }
    }
}
