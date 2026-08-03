package kr.prism.nowflix.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewCountTest {

    @Test
    fun underTenThousandShowsExactGroupedNumber() {
        assertEquals("조회수 1,234회", ViewCount.format(1234L))
        assertEquals("조회수 6,900회", ViewCount.format(6900L))
        assertEquals("조회수 9,999회", ViewCount.format(9999L))
        assertEquals("조회수 0회", ViewCount.format(0L))
    }

    @Test
    fun tenThousandToUnderHundredThousandShowsOneDecimalMan() {
        assertEquals("조회수 6.9만회", ViewCount.format(69_000L))
        assertEquals("조회수 1만회", ViewCount.format(10_000L))   // trailing .0 dropped
        assertEquals("조회수 1.2만회", ViewCount.format(12_300L))
        assertEquals("조회수 9.9만회", ViewCount.format(99_000L))
    }

    @Test
    fun hundredThousandPlusShowsWholeMan() {
        assertEquals("조회수 10만회", ViewCount.format(100_000L))
        assertEquals("조회수 69만회", ViewCount.format(690_000L))
        assertEquals("조회수 690만회", ViewCount.format(6_900_000L))
    }
}
