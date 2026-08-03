package kr.prism.nowflix.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The idle-return rules, driven by a hand-cranked fake clock so time can be advanced at will.
 * Covers scenarios 1-9 of the STEP 6 brief; scenario 10 (queue/scroll reset) lives in
 * [kr.prism.nowflix.KioskNavStateTest].
 */
class IdleReturnMachineTest {

    private var nowMs = 0L
    private fun machine(idleSeconds: Int = 120) =
        IdleReturnMachine(clock = { nowMs }, idleReturnSeconds = idleSeconds)

    // 1. Home screen, 120s elapsed -> return.
    @Test
    fun browsingReturnsAfterTheIdleTimeout() {
        val m = machine()
        nowMs = 120_000
        assertTrue(m.isIdleExpired())
    }

    // 2. Touch at 119s resets the timer; a full 120s is needed again afterwards.
    @Test
    fun aTouchResetsTheIdleTimer() {
        val m = machine()
        nowMs = 119_000
        m.onTouch()
        assertFalse(m.isIdleExpired())          // just touched
        nowMs = 238_000                          // 119s after the touch
        assertFalse(m.isIdleExpired())
        nowMs = 239_000                          // 120s after the touch
        assertTrue(m.isIdleExpired())
    }

    // 3. List screen (also "browsing"), 120s elapsed -> return.
    @Test
    fun listScreenReturnsAfterTheIdleTimeout() {
        val m = machine()
        m.setPlaying(false) // on the list, not playing
        nowMs = 120_000
        assertTrue(m.isIdleExpired())
    }

    // 4. Playing, 300s, no touch, 0 auto-advances -> never returns on time.
    @Test
    fun playingNeverReturnsOnElapsedTimeAlone() {
        val m = machine()
        m.setPlaying(true)
        nowMs = 300_000
        assertFalse(m.isIdleExpired())
    }

    // 5. Playing, one auto-advance -> no return.
    @Test
    fun oneAutoAdvanceDoesNotReturn() {
        val m = machine()
        m.setPlaying(true)
        assertFalse(m.onAutoAdvance())
    }

    // 6. Playing, two auto-advances in a row -> return.
    @Test
    fun twoAutoAdvancesInARowReturn() {
        val m = machine()
        m.setPlaying(true)
        assertFalse(m.onAutoAdvance())
        assertTrue(m.onAutoAdvance())
    }

    // 7. One auto-advance then a touch -> count back to 0; one more auto-advance still no return.
    @Test
    fun aTouchClearsTheAutoAdvanceRun() {
        val m = machine()
        m.setPlaying(true)
        assertFalse(m.onAutoAdvance())
        m.onTouch()
        assertFalse(m.onAutoAdvance())
    }

    // 8. A user-driven "next" never counts as an auto-advance.
    @Test
    fun manualNextNeverCountsTowardTheAutoAdvanceRun() {
        val m = machine()
        m.setPlaying(true)
        m.onManualNext()
        m.onManualNext()
        assertFalse(m.onAutoAdvance())          // still only the first auto-advance
    }

    // 9. idle_return_seconds = 60 -> return at 60s.
    @Test
    fun idleTimeoutHonorsTheConfiguredSeconds() {
        val m = machine(idleSeconds = 60)
        nowMs = 59_000
        assertFalse(m.isIdleExpired())
        nowMs = 60_000
        assertTrue(m.isIdleExpired())
    }

    @Test
    fun changingIdleSecondsAtRuntimeTakesEffect() {
        val m = machine(idleSeconds = 120)
        m.idleReturnSeconds = 60
        nowMs = 60_000
        assertTrue(m.isIdleExpired())
    }
}
