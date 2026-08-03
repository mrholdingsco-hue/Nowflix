package kr.prism.nowflix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistCacheTest {

    private fun vid(id: String) = Video(id, "title $id", "https://t/$id", "2023-01-01T00:00:00Z")

    @Test
    fun codecRoundTripsVideoList() {
        val videos = listOf(vid("a"), vid("b"), vid("c"))

        val decoded = PlaylistCodec.decode(PlaylistCodec.encode(videos))

        assertEquals(videos, decoded)
    }

    @Test
    fun cacheStoreReadsBackWhatItWrote() {
        val cache = InMemoryCacheStore()
        cache.write("knee", listOf(vid("a"), vid("b")))

        assertEquals(listOf(vid("a"), vid("b")), cache.read("knee"))
        assertNull(cache.read("hip")) // untouched part
    }

    @Test
    fun cacheStoreNeverOverwritesWithEmptyList() {
        val cache = InMemoryCacheStore()
        cache.write("knee", listOf(vid("a")))
        cache.write("knee", emptyList())

        assertEquals(listOf(vid("a")), cache.read("knee"))
    }
}

/** In-memory [CacheStore] shared by cache and repository tests. */
class InMemoryCacheStore(initial: Map<String, List<Video>> = emptyMap()) : CacheStore {
    val store = initial.toMutableMap()
    override fun read(partId: String): List<Video>? = store[partId]
    override fun write(partId: String, videos: List<Video>) {
        if (videos.isNotEmpty()) store[partId] = videos
    }
}
