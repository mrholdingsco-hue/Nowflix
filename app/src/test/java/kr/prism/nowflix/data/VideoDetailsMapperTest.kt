package kr.prism.nowflix.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDetailsMapperTest {

    @Test
    fun mapsDurationAndViewCount() {
        val items = listOf(
            VideoResource(
                id = "a",
                contentDetails = VideoContentDetails(duration = "PT3M49S"),
                statistics = VideoStatistics(viewCount = "69000"),
            ),
        )

        val details = VideoDetailsMapper.toDetails(items)

        assertEquals(1, details.size)
        assertEquals(VideoDetail("a", durationSeconds = 229L, viewCount = 69_000L), details.single())
    }

    @Test
    fun defaultsMissingFieldsToZeroAndDropsItemsWithNoId() {
        val items = listOf(
            VideoResource(id = "b"), // no contentDetails/statistics
            VideoResource(id = "", statistics = VideoStatistics(viewCount = "5")), // no id -> dropped
        )

        val details = VideoDetailsMapper.toDetails(items)

        assertEquals(listOf(VideoDetail("b", 0L, 0L)), details)
    }
}
