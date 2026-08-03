package kr.prism.nowflix.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistPaginatorTest {

    private fun item(id: String) = PlaylistItem(
        snippet = Snippet(
            title = "video $id",
            resourceId = ResourceId(id),
            thumbnails = Thumbnails(medium = Thumb("https://t/$id")),
        ),
        contentDetails = ContentDetails(videoId = id, videoPublishedAt = "2023-01-01T00:00:00Z"),
    )

    /** Serves canned pages in order and records which pageTokens it was asked for. */
    private class PagedSource(private val pages: List<PlaylistItemsResponse>) : PlaylistItemsSource {
        val tokensSeen = mutableListOf<String?>()
        var calls = 0
        override suspend fun fetchPage(playlistId: String, pageToken: String?): PlaylistItemsResponse {
            tokensSeen += pageToken
            return pages[calls++]
        }
    }

    @Test
    fun concatenatesThreePagesFollowingNextPageTokens() = runTest {
        // 85-video playlist arriving as 50 + 50 + 20 across three pages.
        val page1 = PlaylistItemsResponse(items = (1..50).map { item("a$it") }, nextPageToken = "P2")
        val page2 = PlaylistItemsResponse(items = (1..50).map { item("b$it") }, nextPageToken = "P3")
        val page3 = PlaylistItemsResponse(items = (1..20).map { item("c$it") }, nextPageToken = null)
        val source = PagedSource(listOf(page1, page2, page3))

        val all = PlaylistPaginator.fetchAll(source, "PL")

        assertEquals(120, all.size)
        assertEquals(3, source.calls)
        assertEquals(listOf<String?>(null, "P2", "P3"), source.tokensSeen)
    }

    @Test
    fun stopsAfterSinglePageWhenNoNextToken() = runTest {
        val source = PagedSource(
            listOf(PlaylistItemsResponse(items = (1..35).map { item("x$it") }, nextPageToken = null)),
        )

        val all = PlaylistPaginator.fetchAll(source, "PL")

        assertEquals(35, all.size)
        assertEquals(1, source.calls)
    }

    @Test
    fun treatsBlankNextTokenAsEnd() = runTest {
        val source = PagedSource(
            listOf(PlaylistItemsResponse(items = listOf(item("y")), nextPageToken = "")),
        )

        val all = PlaylistPaginator.fetchAll(source, "PL")

        assertEquals(1, all.size)
        assertEquals(1, source.calls)
    }
}
