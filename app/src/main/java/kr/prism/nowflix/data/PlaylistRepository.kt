package kr.prism.nowflix.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** What the screen should show for a part's video list. */
sealed interface PlaylistResult {
    /** A usable list. [fromCache] true = shown from cache while the network is in flight. */
    data class Data(val videos: List<Video>, val fromCache: Boolean) : PlaylistResult

    /** No cache and the network failed — the only case that shows the retry message. */
    data object Unavailable : PlaylistResult
}

/**
 * Cache-first playlist loader. Emits the cached list immediately (if any), then the
 * fresh network list once it arrives. On network failure the cached emission stands;
 * with no cache it emits [PlaylistResult.Unavailable]. No error codes or English
 * strings ever reach the UI — this is a lobby screen patients look at.
 */
class PlaylistRepository(
    private val source: PlaylistItemsSource,
    private val cache: CacheStore,
) {
    fun stream(partId: String, playlistId: String): Flow<PlaylistResult> = flow {
        val cached = cache.read(partId)
        if (cached != null) emit(PlaylistResult.Data(cached, fromCache = true))

        try {
            val items = PlaylistPaginator.fetchAll(source, playlistId)
            val videos = VideoMapper.toVideos(items)
            cache.write(partId, videos)
            emit(PlaylistResult.Data(videos, fromCache = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep showing the cache if we have one; otherwise report unavailable.
            if (cached == null) emit(PlaylistResult.Unavailable)
        }
    }.flowOn(Dispatchers.IO)
}
