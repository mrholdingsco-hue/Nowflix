package kr.prism.nowflix.data

/**
 * Pure DTO -> domain mapping and the deleted/private filter. No Android or network
 * types, so it runs under a JVM unit test with hand-built [PlaylistItem]s.
 */
object VideoMapper {

    // YouTube returns these exact English titles for unavailable items.
    private val UNAVAILABLE_TITLES = setOf("Deleted video", "Private video")

    fun toVideos(items: List<PlaylistItem>): List<Video> = items.mapNotNull(::toVideo)

    /** Returns null for any item that must be excluded (deleted, private, malformed). */
    fun toVideo(item: PlaylistItem): Video? {
        val videoId = item.contentDetails?.videoId?.takeIf { it.isNotBlank() }
            ?: item.snippet?.resourceId?.videoId?.takeIf { it.isNotBlank() }
            ?: return null

        val title = item.snippet?.title?.trim().orEmpty()
        if (title.isEmpty() || title in UNAVAILABLE_TITLES) return null

        // Deleted/private videos also come back with their thumbnails stripped.
        val thumbnailUrl = bestThumbnail(item.snippet?.thumbnails) ?: return null

        val publishedAt = item.contentDetails?.videoPublishedAt
            ?: item.snippet?.publishedAt
            ?: ""

        val channelTitle = item.snippet?.videoOwnerChannelTitle?.trim().orEmpty()

        // durationSeconds/viewCount are filled later by the videos.list enrichment pass.
        return Video(videoId, title, thumbnailUrl, publishedAt, channelTitle)
    }

    /** First non-blank URL at medium quality or better (medium is 320x180). */
    private fun bestThumbnail(t: Thumbnails?): String? {
        if (t == null) return null
        return listOfNotNull(t.medium, t.high, t.standard, t.maxres)
            .map { it.url }
            .firstOrNull { it.isNotBlank() }
    }
}

/** videos.list DTO -> [VideoDetail]. Pure, so parsing is covered by JVM unit tests. */
object VideoDetailsMapper {

    fun toDetails(items: List<VideoResource>): List<VideoDetail> = items.mapNotNull { item ->
        val id = item.id.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        VideoDetail(
            videoId = id,
            durationSeconds = VideoDuration.parseSeconds(item.contentDetails?.duration),
            viewCount = item.statistics?.viewCount?.toLongOrNull() ?: 0L,
        )
    }
}
