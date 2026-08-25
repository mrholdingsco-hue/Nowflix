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
        val cached = cache.read()

        // Stage 1 — live remote. On success, persist for the next cold start.
        val remote = try {
            source.fetch()
        } catch (e: Exception) {
            null // Any failure (offline, timeout, parse, empty) falls through silently.
        }
        if (remote != null) {
            val stamped = withPreviousPin(remote, cached)
            cache.write(stamped)
            return KioskConfigMapper.merge(stamped, bundledParts).copy(fromRemote = true)
        }

        // Stage 2 — last successful remote config.
        cached?.let { return KioskConfigMapper.merge(it, bundledParts) }

        // Stage 3 — bundled assets only.
        return KioskConfigMapper.bundledOnly(bundledParts)
    }

    /**
     * Keeps the superseded PIN alive alongside the new one. Without this, changing the PIN in the
     * admin web locks the staff out of the tablet: the new PIN is only picked up by a refresh, and
     * the only refresh button lives behind the PIN screen.
     *
     * The grace PIN is carried forward across later fetches (it is only replaced by the *next*
     * actual change), so a 30-minute auto-refresh landing before anyone walks to the tablet does
     * not silently expire it.
     */
    private fun withPreviousPin(remote: RemoteConfig, cached: RemoteConfig?): RemoteConfig {
        val current = remote.settings.adminPinHash.orEmpty()
        val cachedPin = cached?.settings?.adminPinHash.orEmpty()
        val previous = if (cachedPin.isNotBlank() && cachedPin != current) {
            cachedPin // the PIN just changed remotely — the tablet's old one gets one more life
        } else {
            cached?.settings?.previousAdminPinHash.orEmpty() // unchanged: keep the existing grace PIN
        }
        // Blank, or reverted back to the current PIN -> nothing worth carrying.
        if (previous.isBlank() || previous == current) return remote
        return remote.copy(settings = remote.settings.copy(previousAdminPinHash = previous))
    }
}
