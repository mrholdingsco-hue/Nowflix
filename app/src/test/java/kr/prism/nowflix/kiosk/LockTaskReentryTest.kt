package kr.prism.nowflix.kiosk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FALLBACK re-entry decision. The key property is that repeated onResume calls can never spin
 * into an infinite startLockTask loop — the interval guard rate-limits authorized re-entries.
 */
class LockTaskReentryTest {

    private var nowMs = 0L
    private fun reentry(interval: Long = 2_000L) =
        LockTaskReentry(clock = { nowMs }, minIntervalMs = interval)

    @Test
    fun reentersWhenEscapedAndAdminClosed() {
        val r = reentry()
        assertTrue(r.shouldReenter(isLockTaskActive = false, adminOpen = false))
    }

    @Test
    fun doesNotReenterWhileStillPinned() {
        val r = reentry()
        assertFalse(r.shouldReenter(isLockTaskActive = true, adminOpen = false))
    }

    @Test
    fun doesNotReenterWhileAdminOpen() {
        val r = reentry()
        assertFalse(r.shouldReenter(isLockTaskActive = false, adminOpen = true))
    }

    @Test
    fun intervalGuardPreventsRapidReentryLoop() {
        val r = reentry(interval = 2_000L)
        // First escape at t=0 authorizes a re-entry.
        assertTrue(r.shouldReenter(isLockTaskActive = false, adminOpen = false))
        // Immediate follow-up onResume (still escaped) must NOT re-enter — this is the loop guard.
        nowMs = 100
        assertFalse(r.shouldReenter(isLockTaskActive = false, adminOpen = false))
        nowMs = 1_999
        assertFalse(r.shouldReenter(isLockTaskActive = false, adminOpen = false))
        // Once the interval elapses, a genuine later escape is allowed again.
        nowMs = 2_000
        assertTrue(r.shouldReenter(isLockTaskActive = false, adminOpen = false))
    }

    @Test
    fun manyRapidCallsAuthorizeAtMostOncePerInterval() {
        val r = reentry(interval = 2_000L)
        var authorized = 0
        repeat(100) {
            nowMs += 10 // 100 calls over ~1s, all inside one interval window
            if (r.shouldReenter(isLockTaskActive = false, adminOpen = false)) authorized++
        }
        assertTrue("expected a single authorized re-entry, got $authorized", authorized == 1)
    }
}
