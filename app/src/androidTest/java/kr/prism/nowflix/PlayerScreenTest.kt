package kr.prism.nowflix

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
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
    val rule = createAndroidComposeRule<ComponentActivity>()

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
        rule.waitForIdle()

        // The shield revealed our controls again, and we never left the player.
        rule.onNodeWithText("❚❚").assertIsDisplayed()
        rule.onNodeWithText("다음 동영상").assertIsDisplayed()
    }

    private fun fixtureVideos(): List<Video> {
        val json = Json { ignoreUnknownKeys = true }
        val items = json.decodeFromString<PlaylistItemsResponse>(Fixtures.read("playlist_items.json"))
        return VideoMapper.toVideos(items.items)
    }
}
