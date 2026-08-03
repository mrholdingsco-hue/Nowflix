package kr.prism.nowflix

import androidx.activity.ComponentActivity
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
 */
@RunWith(AndroidJUnit4::class)
class KioskFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

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
        // The detail screen always shows "메인으로" (independent of the video load).
        rule.onNodeWithText("메인으로").assertIsDisplayed()
    }

    @Test
    fun videoList_backToMain_returnsHome() {
        rule.onNodeWithText("괜찮Knee TV").performClick()
        rule.onNodeWithText("메인으로").performClick()
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
        // (6 digits each) trip the lockout.
        repeat(5 * 6) { rule.onNodeWithText("1").performClick() }
        rule.onNodeWithText("잠시 후 다시 시도해주세요", substring = true).assertIsDisplayed()
    }

    @Test
    fun longPress_topRight_opensPin_overPlayer() {
        // Home -> detail.
        rule.onNodeWithText("괜찮Knee TV").performClick()
        // Wait for the fixture playlist so "모두 재생" is available.
        awaitContent { rule.onAllNodesWithText("모두 재생", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("모두 재생", substring = true).performClick()
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
