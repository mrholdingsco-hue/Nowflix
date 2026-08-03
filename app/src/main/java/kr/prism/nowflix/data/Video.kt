package kr.prism.nowflix.data

import kotlinx.serialization.Serializable

/**
 * A playable video in a part's playlist. This is the domain model the UI and the
 * on-device JSON cache both use — deliberately free of any YouTube wire shape.
 * [publishedAt] is kept as the raw ISO-8601 string and formatted (relative) at render.
 * [durationSeconds] and [viewCount] are filled by the videos.list enrichment pass and
 * default to 0 (unknown) so a base list still renders before/without enrichment.
 */
@Serializable
data class Video(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val publishedAt: String,
    val channelTitle: String = "",
    val durationSeconds: Long = 0L,
    val viewCount: Long = 0L,
)

/**
 * A part's cached playlist: the available videos plus the playlist's *total* item count
 * (from the API's pageInfo, includes items hidden by the deleted/private filter). The
 * total is what the left panel's "동영상 N개" shows — matching YouTube's header — so it
 * must survive offline, hence it lives in the cache alongside the list.
 */
@Serializable
data class Playlist(
    val totalCount: Int,
    val videos: List<Video>,
)

/** Duration + view count for one video, from videos.list. Merged into [Video] by id. */
data class VideoDetail(
    val videoId: String,
    val durationSeconds: Long,
    val viewCount: Long,
)
