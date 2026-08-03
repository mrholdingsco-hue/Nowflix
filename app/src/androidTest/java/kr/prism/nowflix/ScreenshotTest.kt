package kr.prism.nowflix

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kr.prism.nowflix.data.KioskConfigMapper
import kr.prism.nowflix.data.PartRow
import kr.prism.nowflix.data.PlaylistItemsResponse
import kr.prism.nowflix.data.RemoteConfig
import kr.prism.nowflix.data.SettingsRow
import kr.prism.nowflix.data.Video
import kr.prism.nowflix.data.VideoDetailsMapper
import kr.prism.nowflix.data.VideoMapper
import kr.prism.nowflix.data.VideosResponse
import kr.prism.nowflix.kiosk.KioskLockMode
import kr.prism.nowflix.kiosk.PinGate
import kr.prism.nowflix.ui.AdminScreen
import kr.prism.nowflix.ui.PartDetailScreen
import kr.prism.nowflix.ui.PinPad
import kr.prism.nowflix.ui.PlayerScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders each kiosk screen with fixed fixture data and writes a PNG to the app's external
 * files dir (`/sdcard/Android/data/kr.prism.nowflix/files/screenshots/`), which the CI workflow
 * pulls as artifacts. Every screen is its own @Test so one screen crashing (e.g. the embedded
 * player) still leaves the other screenshots on disk to upload.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val json = Json { ignoreUnknownKeys = true }

    @After
    fun tearDown() {
        kr.prism.nowflix.data.YoutubeService.baseUrlOverride = null
        kr.prism.nowflix.data.SupabaseService.baseUrlOverride = null
    }

    @Test
    fun mainScreen_6parts() {
        val parts = partsFromFixture("parts_6.json")
        rule.setContent {
            MaterialTheme { HomeScreen(parts = parts, listState = rememberLazyListState(), onPartClick = {}) }
        }
        rule.waitForIdle()
        capture("01_main_6parts")
    }

    @Test
    fun mainScreen_8parts_horizontalScroll() {
        val parts = partsFromFixture("parts_8.json")
        rule.setContent {
            MaterialTheme { HomeScreen(parts = parts, listState = rememberLazyListState(), onPartClick = {}) }
        }
        rule.waitForIdle()
        capture("02_main_8parts")
    }

    @Test
    fun videoListScreen() {
        val backend = FakeBackend()
        backend.install()
        try {
            val part = Part(
                id = "knee",
                title = "괜찮Knee TV",
                thumbnail = "part_01_knee",
                playlistId = "PLfxolKs8oDR66iswGEXMQz10w1seo8O80",
            )
            rule.setContent {
                MaterialTheme {
                    PartDetailScreen(
                        part = part,
                        description = "무릎 통증부터 재활까지, 무릎 건강의 모든 것을 알려드립니다.",
                        onBack = {},
                        onPlay = { _, _ -> },
                    )
                }
            }
            // Wait for the fixture list to land (first video title appears).
            rule.waitUntil(timeoutMillis = 15_000) {
                rule.onAllNodesWithText("무릎 통증, 이 스트레칭 하나면 끝").fetchSemanticsNodes().isNotEmpty()
            }
            rule.waitForIdle()
            capture("03_video_list")
        } finally {
            backend.stop()
        }
    }

    @Test
    fun playerScreen_controlsVisible() {
        rule.setContent {
            MaterialTheme { PlayerScreen(videos = fixtureVideos(), startIndex = 0, onBack = {}) }
        }
        rule.waitForIdle()
        // Controls start visible; capture before the 3s auto-hide.
        capture("04_player_controls_shown")
    }

    @Test
    fun playerScreen_controlsHidden() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme { PlayerScreen(videos = fixtureVideos(), startIndex = 0, onBack = {}) }
        }
        // Drive past the 3s controls auto-hide deterministically.
        rule.mainClock.advanceTimeBy(3_500)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        capture("05_player_controls_hidden")
    }

    @Test
    fun pinPadScreen() {
        val gate = PinGate(clock = { SystemClock.uptimeMillis() })
        rule.setContent {
            MaterialTheme {
                PinPad(
                    onSubmit = { pin -> gate.submit(pin, PinGate.effectiveHash("")) },
                    lockedSecondsLeft = { 0 },
                    onAccepted = {},
                    onDismiss = {},
                )
            }
        }
        // A few digits so the entry dots read as in-use in the screenshot.
        listOf("1", "2", "3").forEach { rule.onNodeWithText(it).performClick() }
        rule.waitForIdle()
        capture("06_pin_pad")
    }

    @Test
    fun adminScreen() {
        rule.setContent {
            MaterialTheme {
                AdminScreen(
                    lockMode = KioskLockMode.FULL_LOCK,
                    appVersion = "0.1.0",
                    lastRemoteAtMillis = 1_722_600_000_000L,
                    returnSeconds = 120,
                    partCount = 6,
                    adminWebUrl = BuildConfig.ADMIN_WEB_URL,
                    refreshing = false,
                    onRefresh = {},
                    onReleaseFully = {},
                    onExitApp = {},
                    onClose = {},
                )
            }
        }
        rule.waitForIdle()
        capture("07_admin")
    }

    // ---- helpers ----

    private fun partsFromFixture(name: String): List<Part> {
        val rows = json.decodeFromString<List<PartRow>>(Fixtures.read(name))
        val bundled = PartsRepository.load(InstrumentationRegistry.getInstrumentation().targetContext)
        return KioskConfigMapper.merge(RemoteConfig(parts = rows, settings = SettingsRow()), bundled).parts
    }

    private fun fixtureVideos(): List<Video> {
        val items = json.decodeFromString<PlaylistItemsResponse>(Fixtures.read("playlist_items.json"))
        val base = VideoMapper.toVideos(items.items)
        val details = VideoDetailsMapper
            .toDetails(json.decodeFromString<VideosResponse>(Fixtures.read("videos.json")).items)
            .associateBy { it.videoId }
        return base.map { v ->
            details[v.videoId]?.let { v.copy(durationSeconds = it.durationSeconds, viewCount = it.viewCount) } ?: v
        }
    }

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        // Internal filesDir (/data/data/<pkg>/files) is always non-null and writable. External
        // storage (getExternalFilesDir) can be null before storage is mounted on a fresh CI
        // emulator, which silently sent earlier captures nowhere. CI pulls this dir via `adb root`.
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "screenshots",
        ).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
