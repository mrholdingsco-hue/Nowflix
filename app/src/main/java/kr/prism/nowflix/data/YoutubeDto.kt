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
