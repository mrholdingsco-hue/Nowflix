package kr.prism.nowflix.data

/**
 * Source of raw playlist pages. Retrofit backs it in production; tests supply a
 * fake so pagination can be exercised without a network.
 */
interface PlaylistItemsSource {
    suspend fun fetchPage(playlistId: String, pageToken: String?): PlaylistItemsResponse
}

/**
 * Walks `nextPageToken` to the end and concatenates every page's items. A 85-video
 * playlist spans multiple 50-item pages; this returns all of them, unfiltered.
 */
object PlaylistPaginator {

    const val PAGE_SIZE = 50

    suspend fun fetchAll(source: PlaylistItemsSource, playlistId: String): List<PlaylistItem> {
        val all = mutableListOf<PlaylistItem>()
        var pageToken: String? = null
        do {
            val page = source.fetchPage(playlistId, pageToken)
            all += page.items
            pageToken = page.nextPageToken
        } while (!pageToken.isNullOrBlank())
        return all
    }
}
