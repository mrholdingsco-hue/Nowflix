package kr.prism.nowflix.data

import kotlinx.serialization.Serializable

/**
 * A playlist's own title + description from `playlists.list` (part=snippet). The detail
 * left panel shows [description] (2 lines, ellipsized). Only ever holds API-provided text —
 * never a made-up blurb; an absent description stays blank and the UI hides the block.
 */
@Serializable
data class PlaylistMeta(
    val playlistId: String,
    val title: String,
    val description: String,
)

/**
 * Source of playlist metadata (`playlists.list`). Retrofit backs it in production; tests
 * supply a fake. All part playlist ids are fetched in a single call.
 */
interface PlaylistMetaSource {
    suspend fun fetchMeta(playlistIds: List<String>): List<PlaylistMeta>
}

/** `playlists.list` DTO -> [PlaylistMeta]. Pure, so it runs under a JVM unit test. */
object PlaylistMetaMapper {
    fun toMeta(items: List<PlaylistResourceDto>): List<PlaylistMeta> = items.mapNotNull { item ->
        val id = item.id.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        PlaylistMeta(
            playlistId = id,
            title = item.snippet?.title.orEmpty().trim(),
            description = item.snippet?.description.orEmpty().trim(),
        )
    }
}
