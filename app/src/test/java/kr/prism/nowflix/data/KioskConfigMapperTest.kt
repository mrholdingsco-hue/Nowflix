package kr.prism.nowflix.data

import kr.prism.nowflix.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskConfigMapperTest {

    private val bundled = listOf(
        Part(id = "knee", title = "무릎", thumbnail = "part_01_knee", playlistId = "pl_knee"),
        Part(id = "chuk", title = "척척", thumbnail = "part_02_chuk", playlistId = "pl_chuk"),
    )

    @Test
    fun sortsByPosition_filtersInactive_mapsDrawable() {
        val remote = RemoteConfig(
            parts = listOf(
                PartRow(position = 2, title = "척척 원격", playlistId = "pl_chuk", isActive = true),
                PartRow(position = 3, title = "숨김", playlistId = "pl_knee", isActive = false),
                PartRow(position = 1, title = "무릎 원격", playlistId = "pl_knee", isActive = true),
            ),
        )

        val config = KioskConfigMapper.merge(remote, bundled)

        assertEquals(listOf("무릎 원격", "척척 원격"), config.parts.map { it.title }) // pos 1,2; inactive dropped
        assertEquals("part_01_knee", config.parts[0].thumbnail) // drawable by playlistId
        assertEquals("knee", config.parts[0].id)
    }

    @Test
    fun remoteThumbnailUrl_isCarried() {
        val remote = RemoteConfig(
            parts = listOf(
                PartRow(position = 1, title = "무릎", thumbnailUrl = "https://x/y.png", playlistId = "pl_knee"),
            ),
        )

        val config = KioskConfigMapper.merge(remote, bundled)

        assertEquals("https://x/y.png", config.parts[0].thumbnailUrl)
    }

    // Verification #4 at the unit level: a 7th part with no bundled artwork still renders.
    @Test
    fun sevenParts_unknownPlaylist_stillIncluded_withPlaceholder() {
        val remoteParts = (1..6).map {
            PartRow(position = it, title = "part$it", playlistId = "pl_$it", isActive = true)
        } + PartRow(position = 7, title = "일곱번째", playlistId = "pl_new", isActive = true)
        val config = KioskConfigMapper.merge(RemoteConfig(parts = remoteParts), bundled)

        assertEquals(7, config.parts.size)
        val seventh = config.parts.last()
        assertEquals("일곱번째", seventh.title)
        assertTrue(seventh.thumbnail.isBlank())   // no bundled drawable -> placeholder path
        assertEquals("pl_new", seventh.id)        // falls back to playlistId as the key
    }

    @Test
    fun codec_roundTrips() {
        val cfg = RemoteConfig(
            parts = listOf(PartRow(position = 1, title = "a", playlistId = "pl_a")),
            settings = SettingsRow(idleReturnSeconds = 45, adminPinHash = "h", headerText = "t"),
        )
        val decoded = RemoteConfigCodec.decode(RemoteConfigCodec.encode(cfg))
        assertEquals(cfg, decoded)
    }
}
