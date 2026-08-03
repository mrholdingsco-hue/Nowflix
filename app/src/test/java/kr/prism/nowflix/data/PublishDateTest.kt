package kr.prism.nowflix.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PublishDateTest {

    @Test
    fun formatsIsoInstantAsDottedDate() {
        assertEquals("2023.05.01", PublishDate.format("2023-05-01T09:00:00Z"))
    }

    @Test
    fun handlesExplicitOffsetInstant() {
        assertEquals("2022.01.01", PublishDate.format("2022-01-01T00:00:00+00:00"))
    }

    @Test
    fun blankOnEmptyInput() {
        assertEquals("", PublishDate.format(""))
    }

    @Test
    fun blankOnUnparseableInput() {
        assertEquals("", PublishDate.format("not-a-date"))
    }
}
