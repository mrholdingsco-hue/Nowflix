package kr.prism.nowflix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistMetaTest {

    @Test
    fun mapsIdTitleAndDescriptionAndTrims() {
        val items = listOf(
            PlaylistResourceDto(id = "PL1", snippet = PlaylistSnippet(title = " 무릎 ", description = "  무릎 재활 영상 모음  ")),
            PlaylistResourceDto(id = "PL2", snippet = PlaylistSnippet(title = "척추", description = "")),
        )

        val meta = PlaylistMetaMapper.toMeta(items)

        assertEquals(2, meta.size)
        assertEquals(PlaylistMeta("PL1", "무릎", "무릎 재활 영상 모음"), meta[0])
        // Empty description is preserved as blank (never invented).
        assertEquals(PlaylistMeta("PL2", "척추", ""), meta[1])
    }

    @Test
    fun dropsItemsWithNoIdOrMissingSnippet() {
        val items = listOf(
            PlaylistResourceDto(id = "", snippet = PlaylistSnippet(title = "x")),
            PlaylistResourceDto(id = "PL3", snippet = null),
        )

        val meta = PlaylistMetaMapper.toMeta(items)

        assertEquals(1, meta.size)
        assertEquals("PL3", meta[0].playlistId)
        assertEquals("", meta[0].description)
    }

    @Test
    fun codecRoundTripsTheDescriptionsMap() {
        val map = mapOf("PL1" to "무릎 재활", "PL2" to "")
        val decoded = PlaylistMetaCodec.decode(PlaylistMetaCodec.encode(map))
        assertEquals(map, decoded)
    }

    @Test
    fun corruptCacheDecodesToNothingUsable() {
        var threw = false
        try {
            PlaylistMetaCodec.decode("{ not json")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }
}
