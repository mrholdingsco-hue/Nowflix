package kr.prism.nowflix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistCacheTest {

    private fun vid(id: String) = Video(id, "title $id", "https://t/$id", "2023-01-01T00:00:00Z")

    @Test
    fun codecRoundTripsPlaylist() {
        val playlist = Playlist(totalCount = 85, videos = listOf(vid("a"), vid("b"), vid("c")))

        val decoded = PlaylistCodec.decode(PlaylistCodec.encode(playlist))

        assertEquals(playlist, decoded)
    }

    @Test
    fun codecRoundTripsEnrichedFields() {
        val v = Video("z", "t", "https://t/z", "2020-01-01T00:00:00Z", "채널", 229L, 69000L)
        val playlist = Playlist(totalCount = 1, videos = listOf(v))

        assertEquals(playlist, PlaylistCodec.decode(PlaylistCodec.encode(playlist)))
    }

    @Test
    fun cacheStoreReadsBackWhatItWrote() {
        val cache = InMemoryCacheStore()
        cache.write("knee", Playlist(85, listOf(vid("a"), vid("b"))))

        assertEquals(Playlist(85, listOf(vid("a"), vid("b"))), cache.read("knee"))
        assertNull(cache.read("hip")) // untouched part
    }

    @Test
    fun cacheStoreNeverOverwritesWithEmptyList() {
        val cache = InMemoryCacheStore()
        cache.write("knee", Playlist(1, listOf(vid("a"))))
        cache.write("knee", Playlist(0, emptyList()))

        assertEquals(Playlist(1, listOf(vid("a"))), cache.read("knee"))
    }
}

/** In-memory [CacheStore] shared by cache and repository tests. */
class InMemoryCacheStore(initial: Map<String, Playlist> = emptyMap()) : CacheStore {
    val store = initial.toMutableMap()
    override fun read(partId: String): Playlist? = store[partId]
    override fun write(partId: String, playlist: Playlist) {
        if (playlist.videos.isNotEmpty()) store[partId] = playlist
    }
}
