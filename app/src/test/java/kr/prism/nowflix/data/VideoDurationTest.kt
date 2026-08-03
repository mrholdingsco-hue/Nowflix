package kr.prism.nowflix.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDurationTest {

    @Test
    fun parsesMinutesAndSeconds() {
        assertEquals(229L, VideoDuration.parseSeconds("PT3M49S"))
    }

    @Test
    fun parsesHoursMinutesSeconds() {
        assertEquals(3733L, VideoDuration.parseSeconds("PT1H2M13S"))
    }

    @Test
    fun parsesSecondsOnlyAndMinutesOnly() {
        assertEquals(45L, VideoDuration.parseSeconds("PT45S"))
        assertEquals(600L, VideoDuration.parseSeconds("PT10M"))
    }

    @Test
    fun parseIsZeroForBlankOrGarbage() {
        assertEquals(0L, VideoDuration.parseSeconds(null))
        assertEquals(0L, VideoDuration.parseSeconds(""))
        assertEquals(0L, VideoDuration.parseSeconds("not-a-duration"))
    }

    @Test
    fun formatsUnderOneHourAsMinuteSecond() {
        assertEquals("3:49", VideoDuration.format(229L))
        assertEquals("0:05", VideoDuration.format(5L))
        assertEquals("10:00", VideoDuration.format(600L))
    }

    @Test
    fun formatsOneHourPlusAsHourMinuteSecond() {
        assertEquals("1:02:13", VideoDuration.format(3733L))
        assertEquals("2:00:00", VideoDuration.format(7200L))
    }
}
