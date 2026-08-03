package kr.prism.nowflix.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** What the screen should show for a part's video list. */
sealed interface PlaylistResult {
    /**
     * A usable list. [totalCount] is the playlist's full item count (shown as "동영상 N개",
     * may exceed [videos].size because deleted/private items are filtered out).
     * [fromCache] true = shown from cache while the network is in flight.
     */
    data class Data(
        val videos: List<Video>,
        val totalCount: Int,
        val fromCache: Boolean,
    ) : PlaylistResult

    /** No cache and the network failed — the only case that shows the retry message. */
    data object Unavailable : PlaylistResult
}

/**
 * Cache-first playlist loader. Emits the cached list immediately (if any), then the
 * fresh network list once it arrives. On network failure the cached emission stands;
 * with no cache it emits [PlaylistResult.Unavailable]. No error codes or English
 * strings ever reach the UI — this is a lobby screen patients look at.
 *
 * After paginating the playlist it enriches each video with duration + view count via
 * videos.list (50 ids per call). Enrichment is best-effort: if it fails, the base list
 * (title, thumbnail, date) still shows.
 */
class PlaylistRepository(
    private val source: PlaylistItemsSource,
    private val cache: CacheStore,
    private val details: VideoDetailsSource = NoopVideoDetailsSource,
) {
    fun stream(partId: String, playlistId: String): Flow<PlaylistResult> = flow {
        val cached = cache.read(partId)
        if (cached != null) {
            emit(PlaylistResult.Data(cached.videos, cached.totalCount, fromCache = true))
        }

        try {
            val paged = PlaylistPaginator.fetchAll(source, playlistId)
            val base = VideoMapper.toVideos(paged.items)
            val videos = enrich(base)
            cache.write(partId, Playlist(paged.totalCount, videos))
            emit(PlaylistResult.Data(videos, paged.totalCount, fromCache = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep showing the cache if we have one; otherwise report unavailable.
            if (cached == null) emit(PlaylistResult.Unavailable)
        }
    }.flowOn(Dispatchers.IO)

    /** Merges duration + view count onto each video. Returns [base] unchanged on failure. */
    private suspend fun enrich(base: List<Video>): List<Video> {
        if (base.isEmpty()) return base
        return try {
            val byId = base.map { it.videoId }
                .chunked(PlaylistPaginator.PAGE_SIZE)
                .flatMap { details.fetchDetails(it) }
                .associateBy { it.videoId }
            if (byId.isEmpty()) return base
            base.map { v ->
                val d = byId[v.videoId] ?: return@map v
                v.copy(durationSeconds = d.durationSeconds, viewCount = d.viewCount)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            base
        }
    }
}
