package kr.prism.nowflix.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerProgressTest {

    private val eps = 0.0001f

    @Test
    fun fractionIsPlayedOverTotalClampedToUnitRange() {
        assertEquals(0.5f, PlayerProgress.fraction(currentSeconds = 100f, durationSeconds = 200f), eps)
        assertEquals(0f, PlayerProgress.fraction(currentSeconds = 0f, durationSeconds = 200f), eps)
        assertEquals(1f, PlayerProgress.fraction(currentSeconds = 200f, durationSeconds = 200f), eps)
        // Overshoot (a late onCurrentSecond tick) never exceeds a full bar.
        assertEquals(1f, PlayerProgress.fraction(currentSeconds = 250f, durationSeconds = 200f), eps)
    }

    @Test
    fun fractionIsZeroWhenDurationUnknown() {
        assertEquals(0f, PlayerProgress.fraction(currentSeconds = 30f, durationSeconds = 0f), eps)
        assertEquals(0f, PlayerProgress.fraction(currentSeconds = 30f, durationSeconds = -5f), eps)
    }

    @Test
    fun seekSecondsMapsBarFractionBackToVideoTime() {
        assertEquals(0f, PlayerProgress.seekSeconds(fraction = 0f, durationSeconds = 229f), eps)
        assertEquals(114.5f, PlayerProgress.seekSeconds(fraction = 0.5f, durationSeconds = 229f), eps)
        assertEquals(229f, PlayerProgress.seekSeconds(fraction = 1f, durationSeconds = 229f), eps)
    }

    @Test
    fun seekSecondsClampsOutOfRangeFractions() {
        assertEquals(0f, PlayerProgress.seekSeconds(fraction = -0.3f, durationSeconds = 229f), eps)
        assertEquals(229f, PlayerProgress.seekSeconds(fraction = 1.4f, durationSeconds = 229f), eps)
        assertEquals(0f, PlayerProgress.seekSeconds(fraction = 0.5f, durationSeconds = 0f), eps)
    }

    @Test
    fun labelIsCurrentOverTotalAsClocks() {
        assertEquals("1:28 / 3:49", PlayerProgress.label(currentSeconds = 88f, durationSeconds = 229f))
        assertEquals("0:00 / 3:49", PlayerProgress.label(currentSeconds = 0f, durationSeconds = 229f))
        // Hour-long video keeps the H:MM:SS shape on both sides.
        assertEquals("1:02:13 / 1:30:00", PlayerProgress.label(currentSeconds = 3733f, durationSeconds = 5400f))
    }

    @Test
    fun labelClampsCurrentToTheVideoLength() {
        // A current time past the end still reads as the end, never beyond it.
        assertEquals("3:49 / 3:49", PlayerProgress.label(currentSeconds = 400f, durationSeconds = 229f))
    }
}
