package kr.prism.nowflix

import kr.prism.nowflix.data.Video
import kr.prism.nowflix.player.IdleReturnMachine
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

    // v1.1.0 — fullscreen is a nav-level mode: the button flips it, and leaving the player drops it.
    @Test
    fun fullscreenTogglesAndIsClearedOnLeavingThePlayer() {
        val windowed = KioskNavState().openPart(part("a")).play(listOf(video("v1")), startIndex = 0)
        assertFalse(windowed.isFullscreen)

        val full = windowed.toggleFullscreen()
        assertTrue("the 전체화면 button enters fullscreen", full.isFullscreen)
        assertFalse("the 작게 보기 button leaves it again", full.toggleFullscreen().isFullscreen)

        assertFalse("back to the list never leaves the chrome hidden", full.closePlayer().isFullscreen)
        assertFalse("idle return starts the next visitor windowed", full.returnToHome().isFullscreen)
    }

    /**
     * The host wiring for a video ending on its own (MainActivity: onAutoAdvance -> goHome only when
     * the idle machine says the seat is empty). Auto-advancing must NOT shrink the picture; only the
     * idle return does, and it clears fullscreen with the rest of the nav state.
     */
    @Test
    fun autoAdvanceKeepsFullscreenUntilTheIdleRuleReturnsHome() {
        var nav = KioskNavState()
            .openPart(part("a"))
            .play(listOf(video("v1"), video("v2"), video("v3")), startIndex = 0)
            .toggleFullscreen()
        val machine = IdleReturnMachine(clock = { 0L }, idleReturnSeconds = 120)
        machine.setPlaying(true)

        // Exactly what the host callback does on each auto-advance.
        fun autoAdvance() { if (machine.onAutoAdvance()) nav = nav.returnToHome() }

        autoAdvance()
        assertTrue("still fullscreen after the first auto-advance", nav.isFullscreen)
        assertTrue("and still playing", nav.isPlaying)

        autoAdvance() // AUTO_ADVANCE_LIMIT reached -> the seat is empty -> home
        assertFalse("the idle return dropped fullscreen too", nav.isFullscreen)
        assertFalse(nav.isPlaying)
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
