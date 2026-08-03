package kr.prism.nowflix.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class RelativeDateTest {

    private val now = OffsetDateTime.parse("2026-08-03T12:00:00Z")

    private fun ago(iso: String) = RelativeDate.format(iso, now)

    @Test
    fun years() {
        assertEquals("6년 전", ago("2020-01-01T00:00:00Z"))
        assertEquals("2년 전", ago("2024-01-01T00:00:00Z"))
    }

    @Test
    fun monthsAndWeeks() {
        assertEquals("3개월 전", ago("2026-05-01T12:00:00Z"))
        assertEquals("2주 전", ago("2026-07-18T12:00:00Z"))
    }

    @Test
    fun daysHoursMinutes() {
        assertEquals("5일 전", ago("2026-07-29T12:00:00Z"))
        assertEquals("3시간 전", ago("2026-08-03T09:00:00Z"))
        assertEquals("10분 전", ago("2026-08-03T11:50:00Z"))
    }

    @Test
    fun justNowForSecondsAndFuture() {
        assertEquals("방금 전", ago("2026-08-03T11:59:40Z"))
        assertEquals("방금 전", ago("2026-08-04T00:00:00Z")) // future clamps to now
    }

    @Test
    fun blankOnEmptyOrUnparseable() {
        assertEquals("", ago(""))
        assertEquals("", ago("not-a-date"))
    }
}
