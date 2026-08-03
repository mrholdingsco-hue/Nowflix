package kr.prism.nowflix.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Pure JSON (de)serialization for a cached [RemoteConfig] — JVM-testable. */
object RemoteConfigCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(config: RemoteConfig): String = json.encodeToString(config)

    fun decode(text: String): RemoteConfig = json.decodeFromString(text)
}

/**
 * Last-successful remote config, abstracted behind an interface so the repository's
 * three-stage fallback can be exercised with an in-memory store in unit tests.
 */
interface ConfigCacheStore {
    fun read(): RemoteConfig?
    fun write(config: RemoteConfig)
}

/** Stores the last good remote config as `kiosk_config.json` in app-internal storage. */
class FileConfigCacheStore(private val dir: File) : ConfigCacheStore {

    private val file get() = File(dir, "kiosk_config.json")

    override fun read(): RemoteConfig? {
        val f = file
        if (!f.exists()) return null
        return try {
            RemoteConfigCodec.decode(f.readText()).takeIf { it.parts.isNotEmpty() }
        } catch (e: Exception) {
            null // Corrupt cache is treated as no cache.
        }
    }

    override fun write(config: RemoteConfig) {
        if (config.parts.isEmpty()) return // Never overwrite a good cache with an empty result.
        try {
            file.writeText(RemoteConfigCodec.encode(config))
        } catch (e: Exception) {
            // Best-effort; a cache write failure must never surface to the patient-facing UI.
        }
    }
}
