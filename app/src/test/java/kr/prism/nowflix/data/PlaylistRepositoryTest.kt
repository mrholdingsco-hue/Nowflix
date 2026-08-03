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

    private fun sourceReturning(vararg ids: String, total: Int = 0) = object : PlaylistItemsSource {
        override suspend fun fetchPage(playlistId: String, pageToken: String?) =
            PlaylistItemsResponse(
                items = ids.map(::pageItem),
                nextPageToken = null,
                pageInfo = PageInfo(totalResults = total),
            )
    }

    private fun detailsReturning(vararg details: VideoDetail) = object : VideoDetailsSource {
        override suspend fun fetchDetails(ids: List<String>): List<VideoDetail> =
            details.filter { it.videoId in ids }
    }

    @Test
    fun networkBlockedWithCache_showsCacheAndNoError() = runTest {
        val cache = InMemoryCacheStore(mapOf("knee" to Playlist(2, listOf(vid("c1"), vid("c2")))))
        val repo = PlaylistRepository(throwingSource, cache)

        val emissions = repo.stream("knee", "PL").toList()

        assertEquals(1, emissions.size)
        val only = emissions.single() as PlaylistResult.Data
        assertTrue(only.fromCache)
        assertEquals(2, only.totalCount)
        assertEquals(listOf("c1", "c2"), only.videos.map { it.videoId })
    }

    @Test
    fun networkBlockedWithoutCache_reportsUnavailable() = runTest {
        val repo = PlaylistRepository(throwingSource, InMemoryCacheStore())

        val emissions = repo.stream("knee", "PL").toList()

        assertEquals(listOf(PlaylistResult.Unavailable), emissions)
    }

    @Test
    fun success_emitsFreshWithTotalAndWritesCache() = runTest {
        val cache = InMemoryCacheStore()
        val repo = PlaylistRepository(sourceReturning("n1", "n2", total = 5), cache)

        val emissions = repo.stream("knee", "PL").toList()

        assertEquals(1, emissions.size)
        val fresh = emissions.single() as PlaylistResult.Data
        assertEquals(false, fresh.fromCache)
        // total (5) comes from pageInfo and can exceed the filtered list size (2).
        assertEquals(5, fresh.totalCount)
        assertEquals(listOf("n1", "n2"), fresh.videos.map { it.videoId })
        // cache now populated for next launch, total included
        assertEquals(Playlist(5, fresh.videos), cache.read("knee"))
    }

    @Test
    fun cacheThenNetwork_emitsCacheFirstThenReplaces() = runTest {
        val cache = InMemoryCacheStore(mapOf("knee" to Playlist(1, listOf(vid("old")))))
        val repo = PlaylistRepository(sourceReturning("new1", "new2", total = 2), cache)

        val emissions = repo.stream("knee", "PL").toList()

        assertEquals(2, emissions.size)
        val first = emissions[0] as PlaylistResult.Data
        val second = emissions[1] as PlaylistResult.Data
        assertTrue(first.fromCache)
        assertEquals(listOf("old"), first.videos.map { it.videoId })
        assertEquals(false, second.fromCache)
        assertEquals(listOf("new1", "new2"), second.videos.map { it.videoId })
    }

    @Test
    fun enrichmentMergesDurationAndViews() = runTest {
        val repo = PlaylistRepository(
            source = sourceReturning("n1", "n2", total = 2),
            cache = InMemoryCacheStore(),
            details = detailsReturning(VideoDetail("n1", durationSeconds = 229L, viewCount = 69_000L)),
        )

        val fresh = repo.stream("knee", "PL").toList().last() as PlaylistResult.Data

        val n1 = fresh.videos.first { it.videoId == "n1" }
        val n2 = fresh.videos.first { it.videoId == "n2" }
        assertEquals(229L, n1.durationSeconds)
        assertEquals(69_000L, n1.viewCount)
        // n2 had no detail — stays at the unknown defaults.
        assertEquals(0L, n2.durationSeconds)
        assertEquals(0L, n2.viewCount)
    }
}
