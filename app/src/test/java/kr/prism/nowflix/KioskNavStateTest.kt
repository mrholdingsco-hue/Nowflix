package kr.prism.nowflix

import kr.prism.nowflix.data.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskNavStateTest {

    private fun part(id: String) = Part(id = id, title = id, thumbnail = "", playlistId = "pl_$id")
    private fun video(id: String) = Video(videoId = id, title = id, thumbnailUrl = "", publishedAt = "")

    // 10. Returning home resets the playback queue and the list scroll position.
    @Test
    fun returnToHomeClearsTheQueueSelectionAndScroll() {
        val playing = KioskNavState(homeScrollIndex = 7)
            .openPart(part("a"))
            .play(listOf(video("v1"), video("v2")), startIndex = 1)
        assertTrue(playing.isPlaying)

        val home = playing.returnToHome()

        assertNull("playback stopped and queue emptied", home.playback)
        assertNull("selected part cleared", home.selectedPart)
        assertEquals("list scrolled back to the top", 0, home.homeScrollIndex)
        assertFalse(home.isPlaying)
    }

    @Test
    fun screenSelectionFollowsTheState() {
        val home = KioskNavState()
        assertNull(home.selectedPart)
        assertNull(home.playback)

        val list = home.openPart(part("a"))
        assertEquals("a", list.selectedPart?.id)
        assertNull(list.playback)

        val player = list.play(listOf(video("v1")), startIndex = 0)
        assertTrue(player.isPlaying)

        assertNull("closing the player drops back to the list", player.closePlayer().playback)
        assertEquals("a", player.closePlayer().selectedPart?.id)
    }
}
