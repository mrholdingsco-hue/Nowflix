package kr.prism.nowflix.data

import kr.prism.nowflix.Part

/**
 * Three-stage config loader for the lobby kiosk. The priority order must never break —
 * this is what the patient sees on the wall:
 *
 *   1. Supabase (live)         -> also refreshes the on-disk cache
 *   2. last remote cache       -> kiosk_config.json in internal storage
 *   3. bundled assets/parts.json + default settings
 *
 * [load] never throws and never surfaces an error: a failed fetch silently keeps
 * whatever the lower stages provide. Injectable dependencies keep it JVM-testable.
 */
class KioskConfigRepository(
    private val source: SupabaseConfigSource,
    private val cache: ConfigCacheStore,
    private val bundled: () -> List<Part>,
) {
    suspend fun load(): KioskConfig {
        val bundledParts = bundled()

        // Stage 1 — live remote. On success, persist for the next cold start.
        val remote = try {
            source.fetch()
        } catch (e: Exception) {
            null // Any failure (offline, timeout, parse, empty) falls through silently.
        }
        if (remote != null) {
            cache.write(remote)
            return KioskConfigMapper.merge(remote, bundledParts)
        }

        // Stage 2 — last successful remote config.
        cache.read()?.let { return KioskConfigMapper.merge(it, bundledParts) }

        // Stage 3 — bundled assets only.
        return KioskConfigMapper.bundledOnly(bundledParts)
    }
}
