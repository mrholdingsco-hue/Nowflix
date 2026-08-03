package kr.prism.nowflix.data

import kotlinx.serialization.Serializable

/**
 * A playable video in a part's playlist. This is the domain model the UI and the
 * on-device JSON cache both use — deliberately free of any YouTube wire shape.
 * [publishedAt] is kept as the raw ISO-8601 string; it is formatted at render time.
 */
@Serializable
data class Video(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val publishedAt: String,
)
