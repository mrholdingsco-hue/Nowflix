package kr.prism.nowflix.data

import kotlinx.serialization.Serializable

/**
 * Wire models for `playlistItems.list` (part=snippet,contentDetails).
 * Every field is optional with a default so a partial/odd item never fails
 * deserialization — filtering happens later in [VideoMapper].
 */
@Serializable
data class PlaylistItemsResponse(
    val items: List<PlaylistItem> = emptyList(),
    val nextPageToken: String? = null,
    val pageInfo: PageInfo? = null,
)

@Serializable
data class PageInfo(
    // Total items in the playlist, including deleted/private ones the UI filters out.
    val totalResults: Int = 0,
)

@Serializable
data class PlaylistItem(
    val snippet: Snippet? = null,
    val contentDetails: ContentDetails? = null,
)

@Serializable
data class Snippet(
    val title: String = "",
    val publishedAt: String? = null,
    val resourceId: ResourceId? = null,
    val thumbnails: Thumbnails? = null,
    // Uploader shown as the first token of each row's meta line ("고쳐줘 NOW · ...").
    val videoOwnerChannelTitle: String? = null,
)

@Serializable
data class ResourceId(
    val videoId: String? = null,
)

@Serializable
data class ContentDetails(
    val videoId: String? = null,
    // Actual upload date of the video (absent for deleted/private items).
    val videoPublishedAt: String? = null,
)

@Serializable
data class Thumbnails(
    val default: Thumb? = null,
    val medium: Thumb? = null,
    val high: Thumb? = null,
    val standard: Thumb? = null,
    val maxres: Thumb? = null,
)

@Serializable
data class Thumb(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

/** Wire models for `videos.list` (part=contentDetails,statistics) — duration + views. */
@Serializable
data class VideosResponse(
    val items: List<VideoResource> = emptyList(),
)

@Serializable
data class VideoResource(
    val id: String = "",
    val contentDetails: VideoContentDetails? = null,
    val statistics: VideoStatistics? = null,
)

@Serializable
data class VideoContentDetails(
    // ISO-8601 duration, e.g. "PT3M49S".
    val duration: String? = null,
)

@Serializable
data class VideoStatistics(
    // Decimal string; absent when the owner hides view counts.
    val viewCount: String? = null,
)
