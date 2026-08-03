package kr.prism.nowflix.data

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlaylistRepositoryTest {

    private fun vid(id: String) = Video(id, "title $id", "https://t/$id", "2023-01-01T00:00:00Z")

    private fun pageItem(id: String) = PlaylistItem(
        snippet = Snippet(
            title = "title $id",
            resourceId = ResourceId(id),
            thumbnails = Thumbnails(medium = Thumb("https://t/$id")),
        ),
        contentDetails = ContentDetails(videoId = id, videoPublishedAt = "2023-01-01T00:00:00Z"),
    )

    private val throwingSource = object : PlaylistItemsSource {
        override suspend fun fetchPage(playlistId: String, pageToken: String?) =
            throw IOException("network down")
    }

    private fun sourceReturning(vararg ids: String) = object : PlaylistItemsSource {
        override suspend fun fetchPage(playlistId: String, pageToken: String?) =
            PlaylistItemsResponse(items = ids.map(::pageItem), nextPageToken = null)
    }

    @Test
    fun networkBlockedWithCache_showsCacheAndNoError() = runTest {
        val cache = InMemoryCacheStore(mapOf("knee" to listOf(vid("c1"), vid("c2"))))
        val repo = PlaylistRepository(throwingSource, cache)

        val emissions = repo.stream("knee", "PL").toList()

        assertEquals(1, emissions.size)
        val only = emissions.single() as PlaylistResult.Data
        assertTrue(only.fromCache)
        assertEquals(listOf("c1", "c2"), only.videos.map { it.videoId })
    }

    @Test
    fun networkBlockedWithoutCache_reportsUnavailable() = runTest {
        val repo = PlaylistRepository(throwingSource, InMemoryCacheStore())

        val emissions = repo.stream("knee", "PL").toList()

        assertEquals(listOf(PlaylistResult.Unavailable), emissions)
    }

    @Test
    fun success_emitsFreshAndWritesCache() = runTest {
        val cache = InMemoryCacheStore()
        val repo = PlaylistRepository(sourceReturning("n1", "n2"), cache)

        val emissions = repo.stream("knee", "PL").toList()

        assertEquals(1, emissions.size)
        val fresh = emissions.single() as PlaylistResult.Data
        assertEquals(false, fresh.fromCache)
        assertEquals(listOf("n1", "n2"), fresh.videos.map { it.videoId })
        // cache now populated for next launch
        assertEquals(listOf("n1", "n2"), cache.read("knee")?.map { it.videoId })
    }

    @Test
    fun cacheThenNetwork_emitsCacheFirstThenReplaces() = runTest {
        val cache = InMemoryCacheStore(mapOf("knee" to listOf(vid("old"))))
        val repo = PlaylistRepository(sourceReturning("new1", "new2"), cache)

        val emissions = repo.stream("knee", "PL").toList()

        assertEquals(2, emissions.size)
        val first = emissions[0] as PlaylistResult.Data
        val second = emissions[1] as PlaylistResult.Data
        assertTrue(first.fromCache)
        assertEquals(listOf("old"), first.videos.map { it.videoId })
        assertEquals(false, second.fromCache)
        assertEquals(listOf("new1", "new2"), second.videos.map { it.videoId })
    }
}
