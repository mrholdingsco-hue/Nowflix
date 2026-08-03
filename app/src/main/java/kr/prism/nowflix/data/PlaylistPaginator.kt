package kr.prism.nowflix.data

/**
 * Source of raw playlist pages. Retrofit backs it in production; tests supply a
 * fake so pagination can be exercised without a network.
 */
interface PlaylistItemsSource {
    suspend fun fetchPage(playlistId: String, pageToken: String?): PlaylistItemsResponse
}

/**
 * Source of per-video duration + view count (videos.list). Retrofit backs it in
 * production; the repository enriches its base list through this.
 */
interface VideoDetailsSource {
    suspend fun fetchDetails(ids: List<String>): List<VideoDetail>
}

/** A [VideoDetailsSource] that returns nothing — used where enrichment isn't wired. */
object NoopVideoDetailsSource : VideoDetailsSource {
    override suspend fun fetchDetails(ids: List<String>): List<VideoDetail> = emptyList()
}

/** All of a playlist's items plus the API's reported total (may exceed [items].size). */
data class PagedItems(
    val items: List<PlaylistItem>,
    val totalCount: Int,
)

/**
 * Walks `nextPageToken` to the end and concatenates every page's items. A 85-video
 * playlist spans multiple 50-item pages; this returns all of them, unfiltered, plus
 * the total item count from the first page's pageInfo (falls back to the fetched size).
 */
object PlaylistPaginator {

    const val PAGE_SIZE = 50

    suspend fun fetchAll(source: PlaylistItemsSource, playlistId: String): PagedItems {
        val all = mutableListOf<PlaylistItem>()
        var pageToken: String? = null
        var totalResults = 0
        do {
            val page = source.fetchPage(playlistId, pageToken)
            if (pageToken == null) totalResults = page.pageInfo?.totalResults ?: 0
            all += page.items
            pageToken = page.nextPageToken
        } while (!pageToken.isNullOrBlank())
        return PagedItems(all, totalResults.takeIf { it > 0 } ?: all.size)
    }
}
