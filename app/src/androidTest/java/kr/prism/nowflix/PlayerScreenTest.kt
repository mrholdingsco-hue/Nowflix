package kr.prism.nowflix

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import kr.prism.nowflix.data.PlaylistItemsResponse
import kr.prism.nowflix.data.Video
import kr.prism.nowflix.data.VideoMapper
import kr.prism.nowflix.ui.PlayerScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The player's full-surface touch-shield must absorb every pointer event over the video: a tap
 * there only reveals our own controls and never reaches the embedded WebView or leaves the
 * player. Hosted directly (no network) with fixture videos so the check is deterministic.
 *
 * True WebView touch-passthrough can only be fully confirmed on a real device (see
 * docs/onsite-checklist.md); here we assert the observable contract — the shield reveals our
 * controls and we stay in the player.
 */
@RunWith(AndroidJUnit4::class)
class PlayerScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<LandscapeActivity>()

    @Test
    fun touchShield_tap_revealsControls_andStaysInPlayer() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme { PlayerScreen(videos = fixtureVideos(), startIndex = 0, onBack = {}) }
        }

        // Let the 3-second controls auto-hide (plus the fade) run out.
        rule.mainClock.advanceTimeBy(3_800)
        rule.waitForIdle()
        // Controls are gone (the center play/pause glyph is no longer present).
        rule.onNodeWithText("❚❚").assertDoesNotExist()

        // Tap the video surface: only the shield should receive it.
        rule.onRoot().performTouchInput { click(Offset(width * 0.3f, height * 0.25f)) }
        // Clock is frozen, so advance a few frames to let the shield's gesture complete and the
        // revealed controls recompose (still well under the 3-second re-hide timeout).
        rule.mainClock.advanceTimeBy(64)
        rule.waitForIdle()

        // The shield revealed our controls again, and we never left the player.
        rule.onNodeWithText("❚❚").assertIsDisplayed()
        rule.onNodeWithText("다음 동영상").assertIsDisplayed()
    }

    // ---- v1.1.0 fullscreen ----

    /**
     * Entering fullscreen drops the up-next column and the title/meta block, and the two controls
     * a stranded viewer needs — "목록으로" and the progress time — stay on screen. The button in
     * that same spot becomes "작게 보기" and puts the normal layout back.
     */
    @Test
    fun fullscreen_hidesUpNextAndMeta_keepsBackAndProgress() {
        rule.mainClock.autoAdvance = false
        val videos = fixtureVideos()
        setPlayerContent(videos)

        rule.onNodeWithText("다음 동영상").assertIsDisplayed()
        // The playing title appears twice in the normal layout: the meta block and the up-next row.
        assertTrue("title is on screen while windowed", titleNodes(videos[0].title) > 0)

        rule.onNodeWithText("전체화면").performClick()
        settle()

        rule.onNodeWithText("다음 동영상").assertDoesNotExist()
        assertEquals("meta + up-next list are both gone", 0, titleNodes(videos[0].title))
        rule.onNodeWithText("←  목록으로").assertIsDisplayed()
        rule.onNodeWithText("0:00 / 0:00").assertIsDisplayed()
        rule.onNodeWithText("작게 보기").assertIsDisplayed()

        rule.onNodeWithText("작게 보기").performClick()
        settle()

        rule.onNodeWithText("다음 동영상").assertIsDisplayed()
        rule.onNodeWithText("전체화면").assertIsDisplayed()
    }

    /**
     * The touch shield has to grow with the stage. In fullscreen the video occupies the area that
     * used to be the up-next column and the meta block — exactly where the embed's own title /
     * YouTube logo sit. A tap there must be absorbed by the shield (it only reveals our controls)
     * and must never reach the WebView or leave the player.
     */
    @Test
    fun fullscreen_touchShield_coversTheEnlargedStage() {
        rule.mainClock.autoAdvance = false
        var backs = 0
        setPlayerContent(fixtureVideos(), onBack = { backs++ })

        rule.onNodeWithText("전체화면").performClick()
        settle()

        // Points that are OUTSIDE the windowed 16:9 stage and inside the fullscreen one: the
        // former up-next column (right 35%) and the former title/meta strip (below the stage).
        listOf(
            Offset(0.88f, 0.10f), // top-right — the embed's title / "Watch on YouTube" corner
            Offset(0.92f, 0.55f), // right edge, mid height
            Offset(0.30f, 0.72f), // under the old 16:9 stage, above the bottom bar
        ).forEach { p ->
            // Let the controls fade out again, so their reappearance can only come from the shield.
            rule.mainClock.advanceTimeBy(3_800)
            rule.waitForIdle()
            rule.onNodeWithText("❚❚").assertDoesNotExist()

            rule.onRoot().performTouchInput { click(Offset(width * p.x, height * p.y)) }
            rule.mainClock.advanceTimeBy(64)
            rule.waitForIdle()

            rule.onNodeWithText("❚❚").assertIsDisplayed()
        }

        // Never left the player, and never fell out of fullscreen.
        assertEquals("the shield absorbed every tap — no exit", 0, backs)
        rule.onNodeWithText("작게 보기").assertIsDisplayed()
    }

    /** Changing video keeps the fullscreen chrome — the picture must not shrink on every track. */
    @Test
    fun fullscreen_survivesAVideoChange() {
        rule.mainClock.autoAdvance = false
        val videos = fixtureVideos()
        setPlayerContent(videos)

        rule.onNodeWithText("전체화면").performClick()
        settle()

        rule.onNodeWithText("다음 영상  ▶").performClick()
        settle()

        rule.onNodeWithText("작게 보기").assertIsDisplayed()
        rule.onNodeWithText("다음 동영상").assertDoesNotExist()
    }

    /** Android back: first press only leaves fullscreen, a second one returns to the list. */
    @Test
    fun fullscreen_backPress_leavesFullscreenBeforeLeavingThePlayer() {
        rule.mainClock.autoAdvance = false
        var backs = 0
        setPlayerContent(fixtureVideos(), onBack = { backs++ })

        rule.onNodeWithText("전체화면").performClick()
        settle()

        pressBack()
        settle()
        assertEquals("first back only left fullscreen", 0, backs)
        rule.onNodeWithText("전체화면").assertIsDisplayed()
        rule.onNodeWithText("다음 동영상").assertIsDisplayed()

        pressBack()
        settle()
        assertEquals("second back returned to the list", 1, backs)
    }

    // ---- helpers ----

    /** Hosts the player with the fullscreen flag hoisted, exactly as MainActivity does. */
    private fun setPlayerContent(videos: List<Video>, onBack: () -> Unit = {}) {
        rule.setContent {
            var fullscreen by remember { mutableStateOf(false) }
            MaterialTheme {
                PlayerScreen(
                    videos = videos,
                    startIndex = 0,
                    onBack = onBack,
                    isFullscreen = fullscreen,
                    onToggleFullscreen = { fullscreen = !fullscreen },
                )
            }
        }
        settle()
    }

    /** The clock is frozen, so pump past the 180ms fullscreen transition by hand. */
    private fun settle() {
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
    }

    private fun titleNodes(title: String): Int =
        rule.onAllNodesWithText(title).fetchSemanticsNodes().size

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    }

    private fun fixtureVideos(): List<Video> {
        val json = Json { ignoreUnknownKeys = true }
        val items = json.decodeFromString<PlaylistItemsResponse>(Fixtures.read("playlist_items.json"))
        return VideoMapper.toVideos(items.items)
    }
}
