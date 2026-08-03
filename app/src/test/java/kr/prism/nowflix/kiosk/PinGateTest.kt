package kr.prism.nowflix.kiosk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PIN escape gate, driven by a hand-cranked fake clock so the 30s lockout is verified without
 * real waiting. Covers STEP 7 requirements: correct/incorrect PIN, 5-fail -> 30s lockout,
 * auto-unlock after the window, and the built-in default-hash fallback.
 */
class PinGateTest {

    private var nowMs = 0L
    private fun gate() = PinGate(clock = { nowMs })
    private val hash = Sha256.hex("739104")

    @Test
    fun correctPinIsAccepted() {
        val g = gate()
        assertEquals(PinResult.Accepted, g.submit("739104", hash))
    }

    @Test
    fun wrongPinIsRejectedWithDecreasingAttempts() {
        val g = gate()
        assertEquals(PinResult.Rejected(4), g.submit("000000", hash))
        assertEquals(PinResult.Rejected(3), g.submit("111111", hash))
    }

    @Test
    fun fiveConsecutiveFailuresLockForThirtySeconds() {
        val g = gate()
        repeat(4) { g.submit("000000", hash) }
        val fifth = g.submit("000000", hash)
        assertTrue(fifth is PinResult.LockedOut)
        assertEquals(30, (fifth as PinResult.LockedOut).secondsLeft)

        // Even a correct PIN is refused while locked.
        val duringLock = g.submit("739104", hash)
        assertTrue(duringLock is PinResult.LockedOut)
    }

    @Test
    fun lockoutIsTemporaryNotPermanent() {
        val g = gate()
        repeat(5) { g.submit("000000", hash) } // at t=0 -> locked until t=30_000

        nowMs = 15_000
        assertEquals(15, g.lockedSecondsLeft()) // 15s in

        nowMs = 29_999
        assertTrue(g.submit("739104", hash) is PinResult.LockedOut) // still 1ms short

        nowMs = 30_000
        assertEquals(0, g.lockedSecondsLeft())
        assertEquals(PinResult.Accepted, g.submit("739104", hash)) // unlocked, correct PIN works
    }

    @Test
    fun correctPinResetsTheFailureCounter() {
        val g = gate()
        repeat(4) { g.submit("000000", hash) }
        assertEquals(PinResult.Accepted, g.submit("739104", hash)) // resets
        // A fresh wrong attempt should show 4 remaining again, not tip into lockout.
        assertEquals(PinResult.Rejected(4), g.submit("000000", hash))
    }

    @Test
    fun defaultHashUnlocksWithPin000000WhenNoSettingsHash() {
        val g = gate()
        val effective = PinGate.effectiveHash("") // blank remote/cache -> built-in default
        assertEquals(PinGate.DEFAULT_ADMIN_PIN_HASH, effective)
        assertEquals(PinResult.Accepted, g.submit("000000", effective))
    }

    @Test
    fun settingsHashTakesPrecedenceOverDefault() {
        assertEquals(hash, PinGate.effectiveHash(hash))
    }

    @Test
    fun secondsLeftRoundsUp() {
        val g = gate()
        repeat(5) { g.submit("000000", hash) }
        nowMs = 29_500 // 500ms left
        assertEquals(1, g.lockedSecondsLeft()) // never reports 0 while still locked
    }
}
