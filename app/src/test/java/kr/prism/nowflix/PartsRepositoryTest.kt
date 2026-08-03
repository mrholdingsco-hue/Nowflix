package kr.prism.nowflix

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PartsRepositoryTest {

    @Test
    fun parsesShippedPartsJsonWithSixEntries() {
        // Validate the real asset the app ships, not a copy.
        val text = File("src/main/assets/parts.json").readText()
        val parts = PartsRepository.parse(text)

        assertEquals(6, parts.size)
        assertEquals("괜찮Knee TV", parts[0].title)
        assertEquals("part_01_knee", parts[0].thumbnail)
        assertEquals("PLfxolKs8oDR66iswGEXMQz10w1seo8O80", parts[0].playlistId)
        assertEquals("족집게 TV", parts[5].title)
        assertEquals("PLfxolKs8oDR4x2gGkTk29RpJ7aysqo0J3", parts[5].playlistId)
        // ids unique -> safe as LazyRow keys
        assertEquals(6, parts.map { it.id }.toSet().size)
    }

    @Test
    fun parsesNineEntriesUnchanged() {
        // Adding parts is a data-only change: 9 entries must parse to 9 parts.
        val text = (1..9).joinToString(prefix = "[", postfix = "]", separator = ",") { i ->
            val thumb = "part_%02d".format(i)
            """{"id":"p$i","title":"파트 $i","thumbnail":"$thumb","playlistId":"PL$i"}"""
        }
        val parts = PartsRepository.parse(text)
        assertEquals(9, parts.size)
        assertEquals("파트 9", parts[8].title)
        assertEquals("part_09", parts[8].thumbnail)
    }
}
