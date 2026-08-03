package kr.prism.nowflix

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.prism.nowflix.kiosk.KioskLockMode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the whole integrated kiosk ([KioskApp]) on the emulator with a no-op host and fixed
 * fixtures over MockWebServer, verifying the STEP 9 interaction checklist: navigation, the
 * hidden PIN gesture (on home and over the player), and the PIN lockout.
 *
 * The main clock runs with autoAdvance = false because [KioskApp] holds infinite poll loops
 * (the 1-second idle check, the 30-minute config refresh) that would make a full waitForIdle
 * spin forever. Async fixture loads flip autoAdvance on only for the duration of a waitUntil.
 *
 * Because the clock is frozen, an interaction's resulting recomposition is not applied until a
 * frame is produced — so each touch is followed by [settle], which advances a few frames (well
 * short of the 1-second idle poll) to let the new screen compose before the next assertion.
 * The assertions themselves are unchanged; only the clock is nudged. Runs on the landscape host.
 */
@RunWith(AndroidJUnit4::class)
class KioskFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<LandscapeActivity>()

    private lateinit var backend: FakeBackend

    @Before
    fun setUp() {
        backend = FakeBackend()
        backend.install()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KioskApp(
                lockMode = KioskLockMode.FALLBACK,
                onEnterLock = {},
                onEscapeOverlayChanged = {},
                onReleaseFully = {},
                onExitApp = {},
            )
        }
    }

    @After
    fun tearDown() {
        backend.stop()
    }

    @Test
    fun cardTap_opensVideoList() {
        // Home renders instantly from the bundled parts; tap the first card.
        rule.onNodeWithText("괜찮Knee TV").performClick()
        settle()
        // The detail screen always shows "메인으로" (independent of the video load).
        rule.onNodeWithText("메인으로").assertIsDisplayed()
    }

    @Test
    fun videoList_backToMain_returnsHome() {
        rule.onNodeWithText("괜찮Knee TV").performClick()
        settle()
        rule.onNodeWithText("메인으로").performClick()
        settle()
        // Back on the home grid: the "TOP 6" chip reflects the six parts.
        rule.onNodeWithText("TOP 6").assertIsDisplayed()
    }

    @Test
    fun longPress_topRight_opensPin_onHome() {
        holdPinHotspot()
        rule.onNodeWithText("관리자 PIN").assertIsDisplayed()
    }

    @Test
    fun wrongPin_fiveTimes_locksOut() {
        holdPinHotspot()
        rule.onNodeWithText("관리자 PIN").assertIsDisplayed()
        // Default admin PIN is 000000, so 111111 is always wrong. Five full wrong entries
        // (6 digits each) trip the lockout. A frame per digit lets each keypress register.
        repeat(5 * 6) {
            rule.onNodeWithText("1").performClick()
            rule.mainClock.advanceTimeByFrame()
        }
        rule.waitForIdle()
        rule.onNodeWithText("잠시 후 다시 시도해주세요", substring = true).assertIsDisplayed()
    }

    @Test
    fun longPress_topRight_opensPin_overPlayer() {
        // Home -> detail.
        rule.onNodeWithText("괜찮Knee TV").performClick()
        // Wait for the fixture playlist so "모두 재생" is available (this drives frames itself).
        awaitContent { rule.onAllNodesWithText("모두 재생", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("모두 재생", substring = true).performClick()
        settle()
        // Player is up (its up-next column header shows).
        rule.onNodeWithText("다음 동영상").assertIsDisplayed()
        // The hidden gesture must still fire while the player's touch-shield is on screen.
        holdPinHotspot()
        rule.onNodeWithText("관리자 PIN").assertIsDisplayed()
    }

    /**
     * Presses the top-right hotspot and holds past its 3-second threshold to open the PIN pad.
     * The hold is a bare down + a manual clock advance (the gesture's withTimeoutOrNull runs on
     * the compose clock); the pointer is then released so any later taps start from a clean state.
     */
    private fun holdPinHotspot() {
        rule.onNodeWithTag("pinHotspot").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(3_200)
        rule.waitForIdle()
        // Release the still-down pointer (the hotspot itself is now gone behind the PIN pad).
        try {
            rule.onRoot().performTouchInput { up() }
            rule.waitForIdle()
        } catch (_: Throwable) {
            // Best-effort: the open-PIN assertion doesn't depend on the release.
        }
    }

    /**
     * Applies pending recompositions after an interaction while the clock is frozen: advances a
     * few frames (64 ms — far below the 1-second idle poll, so the infinite loops never spin) and
     * lets layout settle, so the screen the touch opened is composed before the next assertion.
     */
    private fun settle() {
        rule.mainClock.advanceTimeBy(64)
        rule.waitForIdle()
    }

    /** Runs [condition] under autoAdvance so a fixture network load can complete, then restores it. */
    private fun awaitContent(condition: () -> Boolean) {
        rule.mainClock.autoAdvance = true
        try {
            rule.waitUntil(timeoutMillis = 15_000, condition = condition)
        } finally {
            rule.mainClock.autoAdvance = false
        }
    }
}
