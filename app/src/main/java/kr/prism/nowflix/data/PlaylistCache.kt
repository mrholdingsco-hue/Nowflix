package kr.prism.nowflix.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Pure JSON (de)serialization for a cached [Playlist] — JVM-testable. */
object PlaylistCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(playlist: Playlist): String = json.encodeToString(playlist)

    fun decode(text: String): Playlist = json.decodeFromString(text)
}

/**
 * Per-part cache of the last successfully fetched [Playlist] (total count + videos).
 * Abstracted behind an interface so the repository's fallback behaviour can be tested
 * with an in-memory store instead of a real filesystem.
 */
interface CacheStore {
    fun read(partId: String): Playlist?
    fun write(partId: String, playlist: Playlist)
}

/** Stores each part's playlist as `playlist_<partId>.json` in app-internal storage. */
class FileCacheStore(private val dir: File) : CacheStore {

    private fun fileFor(partId: String) = File(dir, "playlist_$partId.json")

    override fun read(partId: String): Playlist? {
        val f = fileFor(partId)
        if (!f.exists()) return null
        return try {
            PlaylistCodec.decode(f.readText()).takeIf { it.videos.isNotEmpty() }
        } catch (e: Exception) {
            null // Corrupt cache is treated as no cache.
        }
    }

    override fun write(partId: String, playlist: Playlist) {
        if (playlist.videos.isEmpty()) return // Never overwrite a good cache with an empty result.
        try {
            fileFor(partId).writeText(PlaylistCodec.encode(playlist))
        } catch (e: Exception) {
            // Cache write is best-effort; a failure must not surface to the patient-facing UI.
        }
    }
}
