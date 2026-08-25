package kr.prism.nowflix.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kr.prism.nowflix.Part

/**
 * Remote configuration as it comes back from Supabase (PostgREST snake_case).
 * This is also exactly what gets written to the on-disk cache, so a cold start
 * with no network can re-derive the same [KioskConfig] the last fetch produced.
 */
@Serializable
data class PartRow(
    val position: Int = 0,
    val title: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("playlist_id") val playlistId: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class SettingsRow(
    @SerialName("idle_return_seconds") val idleReturnSeconds: Int = 120,
    @SerialName("admin_pin_hash") val adminPinHash: String? = null,
    @SerialName("header_text") val headerText: String? = null,
    // Cache-only field: the PIN hash this tablet knew before the last remote change. Supabase
    // never sends it (the kiosk selects the three columns above); the repository stamps it when
    // it writes the cache, so an old PIN still opens the admin screen once after a remote change.
    @SerialName("previous_admin_pin_hash") val previousAdminPinHash: String? = null,
)

@Serializable
data class RemoteConfig(
    val parts: List<PartRow> = emptyList(),
    val settings: SettingsRow = SettingsRow(),
)

/** App-facing settings. Only stored this step; the idle timer is wired in STEP 6. */
data class KioskSettings(
    val idleReturnSeconds: Int = 120,
    val adminPinHash: String = "",
    val headerText: String = "",
    /** PIN hash in force before the last remote change; also accepted by the gate. */
    val previousAdminPinHash: String = "",
)

/** Everything the kiosk needs to render, whatever the source (remote / cache / assets). */
data class KioskConfig(
    val parts: List<Part>,
    val settings: KioskSettings,
    // True only when this value came from a live Supabase fetch (not cache/assets). The admin
    // screen uses it to stamp "마지막 원격 설정 수신 시각".
    val fromRemote: Boolean = false,
)

/**
 * Pure remote->UI merge (no Android deps, JVM-testable).
 *
 * The remote `parts` table owns order/title/active/thumbnail, but not the bundled
 * drawable or the slug id — those come from `assets/parts.json`, matched by
 * `playlistId`. A remote row with no bundled match (e.g. a freshly added 7th part)
 * still renders: it just has no drawable, so the card shows its placeholder.
 */
object KioskConfigMapper {

    fun merge(remote: RemoteConfig, bundled: List<Part>): KioskConfig {
        val byPlaylist = bundled.associateBy { it.playlistId }
        val parts = remote.parts
            .filter { it.isActive }
            .sortedBy { it.position }
            .map { row ->
                val local = byPlaylist[row.playlistId]
                Part(
                    id = local?.id ?: row.playlistId,
                    title = row.title,
                    thumbnail = local?.thumbnail.orEmpty(),
                    playlistId = row.playlistId,
                    description = local?.description.orEmpty(),
                    thumbnailUrl = row.thumbnailUrl.orEmpty(),
                )
            }
        return KioskConfig(parts = parts, settings = remote.settings.toSettings())
    }

    /** Last-resort fallback: the bundled parts as-is, with default settings. */
    fun bundledOnly(bundled: List<Part>): KioskConfig =
        KioskConfig(parts = bundled, settings = KioskSettings())

    private fun SettingsRow.toSettings() = KioskSettings(
        idleReturnSeconds = idleReturnSeconds,
        adminPinHash = adminPinHash.orEmpty(),
        headerText = headerText.orEmpty(),
        previousAdminPinHash = previousAdminPinHash.orEmpty(),
    )
}
