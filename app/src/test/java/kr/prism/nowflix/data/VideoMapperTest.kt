package kr.prism.nowflix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoMapperTest {

    private fun valid(id: String) = PlaylistItem(
        snippet = Snippet(
            title = "Good $id",
            publishedAt = "2022-02-02T00:00:00Z",
            resourceId = ResourceId(id),
            thumbnails = Thumbnails(medium = Thumb("https://t/$id/medium")),
        ),
        contentDetails = ContentDetails(videoId = id, videoPublishedAt = "2023-03-03T00:00:00Z"),
    )

    private fun unavailable(title: String, id: String) = PlaylistItem(
        // Deleted/private items come back with the marker title and no thumbnails.
        snippet = Snippet(title = title, resourceId = ResourceId(id), thumbnails = Thumbnails()),
        contentDetails = ContentDetails(videoId = id),
    )

    @Test
    fun dropsDeletedPrivateAndEmptyThumbnailItems() {
        val items = listOf(
            valid("ok1"),
            unavailable("Deleted video", "d1"),
            unavailable("Private video", "p1"),
            unavailable("Still has a title but no thumb", "nt1"),
            valid("ok2"),
        )

        val videos = VideoMapper.toVideos(items)

        assertEquals(2, videos.size)
        assertEquals(listOf("ok1", "ok2"), videos.map { it.videoId })
    }

    @Test
    fun mapsAllFieldsAndPrefersVideoPublishedAt() {
        val v = VideoMapper.toVideo(valid("z"))!!

        assertEquals("z", v.videoId)
        assertEquals("Good z", v.title)
        assertEquals("https://t/z/medium", v.thumbnailUrl)
        assertEquals("2023-03-03T00:00:00Z", v.publishedAt) // contentDetails.videoPublishedAt wins
    }

    @Test
    fun fallsBackToSnippetPublishedAtAndHigherThumbnail() {
        val item = PlaylistItem(
            snippet = Snippet(
                title = "H",
                publishedAt = "2020-01-01T00:00:00Z",
                resourceId = ResourceId("h"),
                thumbnails = Thumbnails(high = Thumb("https://t/h/high")), // no medium
            ),
            contentDetails = ContentDetails(videoId = "h", videoPublishedAt = null),
        )

        val v = VideoMapper.toVideo(item)!!

        assertEquals("2020-01-01T00:00:00Z", v.publishedAt)
        assertEquals("https://t/h/high", v.thumbnailUrl)
    }

    @Test
    fun dropsItemWithNoVideoId() {
        val item = PlaylistItem(
            snippet = Snippet(title = "no id", thumbnails = Thumbnails(medium = Thumb("u"))),
        )
        assertNull(VideoMapper.toVideo(item))
    }
}
