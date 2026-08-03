package kr.prism.nowflix.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Pure JSON (de)serialization for the playlistId -> description map — JVM-testable. */
object PlaylistMetaCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(map: Map<String, String>): String = json.encodeToString(map)
    fun decode(text: String): Map<String, String> = json.decodeFromString(text)
}

/** Cache of the last fetched playlist descriptions, keyed by playlistId. */
interface MetaCacheStore {
    fun read(): Map<String, String>?
    fun write(map: Map<String, String>)
}

/** Stores the descriptions map as `playlist_meta.json` in app-internal storage. */
class FileMetaCacheStore(private val dir: File) : MetaCacheStore {
    private val file get() = File(dir, "playlist_meta.json")

    override fun read(): Map<String, String>? {
        if (!file.exists()) return null
        return try {
            PlaylistMetaCodec.decode(file.readText()).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    override fun write(map: Map<String, String>) {
        if (map.isEmpty()) return
        try {
            file.writeText(PlaylistMetaCodec.encode(map))
        } catch (e: Exception) {
            // Best-effort; a cache write failure must never surface to the UI.
        }
    }
}

/**
 * Cache-first loader for playlist descriptions. Fetches all part playlists in one
 * `playlists.list` call, caches the result, and returns a playlistId -> description map.
 * Descriptions are effectively static, so a present cache is used as-is; the network is
 * only hit on a cold cache. Never invents text — an absent description maps to "".
 */
class PlaylistMetaRepository(
    private val source: PlaylistMetaSource,
    private val cache: MetaCacheStore,
) {
    suspend fun descriptions(playlistIds: List<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            cache.read()?.let { return@withContext it }
            try {
                val map = source.fetchMeta(playlistIds).associate { it.playlistId to it.description }
                cache.write(map)
                map
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cache.read() ?: emptyMap()
            }
        }
}
