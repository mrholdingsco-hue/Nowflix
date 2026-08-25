package kr.prism.nowflix

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kr.prism.nowflix.data.FileConfigCacheStore
import kr.prism.nowflix.data.FileMetaCacheStore
import kr.prism.nowflix.data.KioskConfig
import kr.prism.nowflix.data.KioskConfigRepository
import kr.prism.nowflix.data.KioskSettings
import kr.prism.nowflix.data.PlaylistMetaRepository
import kr.prism.nowflix.data.RetrofitSupabaseSource
import kr.prism.nowflix.data.RetrofitYoutubeSource
import kr.prism.nowflix.data.SupabaseService
import kr.prism.nowflix.data.YoutubeService
import kr.prism.nowflix.kiosk.AndroidKioskController
import kr.prism.nowflix.kiosk.KioskController
import kr.prism.nowflix.kiosk.KioskLockMode
import kr.prism.nowflix.kiosk.LockTaskReentry
import kr.prism.nowflix.kiosk.PinGate
import kr.prism.nowflix.kiosk.lockModeFor
import kr.prism.nowflix.player.IdleReturnMachine
import kr.prism.nowflix.ui.AdminScreen
import kr.prism.nowflix.ui.PartDetailScreen
import kr.prism.nowflix.ui.PinPad
import kr.prism.nowflix.ui.PlayerScreen

private val NowflixRed = Color(0xFFE50914)
private val BodyBg = Color(0xFF0B0B0C)
private val PlaceholderTile = Color(0xFF1A1A1D)

private const val TAG = "Nowflix"

// Top red band is 18% of screen height.
private const val TOP_BAND_FRACTION = 0.18f
// Row geometry — also feeds CardMetrics for the width calc.
private val RowSidePadding = 24.dp
private val CardGap = 12.dp
// Gap tying the header to the card row so they read as one centered block (problem: header was
// floating ~395px above the cards).
private val HeaderCardGap = 24.dp
// Home cards are vertical posters (client artwork is 4:5). The poster fills the card
// edge-to-edge via ContentScale.Crop — no letterbox bars, no stretch.
private const val POSTER_ASPECT = 4f / 5f
private val CardCorner = 4.dp

class MainActivity : ComponentActivity() {

    // Kiosk lock surface + mode. Built once in onCreate; the mode is fixed by device owner status.
    private lateinit var kiosk: KioskController
    private lateinit var reentry: LockTaskReentry
    private var lockMode: KioskLockMode = KioskLockMode.FALLBACK
    private var lockEntered = false
    // Read from onResume (outside composition) to decide FALLBACK re-entry; set by the overlays
    // so we never fight the operator while they're unlocking.
    private var escapeOverlayOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        keepScreenOnAndBright()

        kiosk = AndroidKioskController(this)
        lockMode = lockModeFor(kiosk.isDeviceOwner())
        reentry = LockTaskReentry(clock = { SystemClock.uptimeMillis() })

        setContent {
            // Thin shell: all kiosk UI + state lives in the testable [KioskApp] composable.
            // The Activity only supplies the lock-mode and the four host actions that need it.
            KioskApp(
                lockMode = lockMode,
                onEnterLock = { enterKioskLock() },
                onEscapeOverlayChanged = { escapeOverlayOpen = it },
                onReleaseFully = {
                    kiosk.releaseFully()
                    finishAndRemoveTask()
                },
                onExitApp = {
                    kiosk.stopLockTask()
                    finishAndRemoveTask()
                },
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Kiosk: keep system bars hidden even after transient system UI shows them.
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        // FALLBACK mode only: screen pinning is user-escapable, so re-assert it if we're no longer
        // pinned — unless an escape overlay is open (don't fight the operator). The reentry guard
        // rate-limits this so onResume -> startLockTask -> onResume can't spin forever.
        if (lockEntered && lockMode == KioskLockMode.FALLBACK &&
            reentry.shouldReenter(kiosk.isLockTaskActive(), escapeOverlayOpen)
        ) {
            kiosk.startLockTask()
        }
    }

    /**
     * Enter the kiosk lock, branching on device owner status (one code path):
     *  - FULL_LOCK: silent lock task + status bar & keyguard disabled.
     *  - FALLBACK:  screen pinning (system confirms) + home-launcher re-entry via [onResume].
     * Called once, only after the escape paths are live (see the LaunchedEffect in onCreate).
     */
    private fun enterKioskLock() {
        if (lockEntered) return
        lockEntered = true
        Log.i(TAG, "enterKioskLock: mode=$lockMode deviceOwner=${kiosk.isDeviceOwner()}")
        kiosk.registerLockTaskPackages()
        kiosk.startLockTask()
        if (lockMode == KioskLockMode.FULL_LOCK) {
            kiosk.setStatusBarDisabled(true)
            kiosk.setKeyguardDisabled(true)
        }
    }

    /**
     * Lobby kiosk display policy: never sleep or lock, and stay at full brightness. Orientation
     * is pinned to landscape in the manifest (`sensorLandscape`), so it is not re-asserted here.
     */
    private fun keepScreenOnAndBright() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

/**
 * The whole kiosk UI + state, hoisted out of [MainActivity] so it can be driven directly by
 * instrumented tests (with a no-op host and MockWebServer-backed data) — the Activity is a thin
 * shell around this. Behaviour is identical to the original inline composition; the only change
 * is that the four Activity-owned actions are passed in as callbacks.
 *
 *  - [lockMode]                which lock strength the admin screen reports.
 *  - [onEnterLock]             enter the kiosk lock, once, after this tree is live (safety belt).
 *  - [onEscapeOverlayChanged]  true while the PIN/admin overlay is open — the Activity reads it
 *                              from onResume so FALLBACK re-entry never fights the operator.
 *  - [onReleaseFully]/[onExitApp] the two admin-screen safety actions.
 */
@Composable
fun KioskApp(
    lockMode: KioskLockMode,
    onEnterLock: () -> Unit,
    onEscapeOverlayChanged: (Boolean) -> Unit,
    onReleaseFully: () -> Unit,
    onExitApp: () -> Unit,
) {
    MaterialTheme {
        // Three-screen kiosk: home grid -> a part's video list -> the player. The whole
        // navigation + playback state is one hoisted value so the idle reset is atomic.
        var nav by remember { mutableStateOf(KioskNavState()) }
        val descriptions = rememberDescriptions()
        // Supabase-backed config (parts + settings) with silent cache/asset fallback.
        val configHolder = rememberKioskConfig()
        val config = configHolder.config

        // Escape-path state, orthogonal to browse/play nav: hidden gesture -> PIN -> admin.
        var showPin by remember { mutableStateOf(false) }
        var showAdmin by remember { mutableStateOf(false) }
        val pinGate = remember { PinGate(clock = { SystemClock.uptimeMillis() }) }
        LaunchedEffect(showPin, showAdmin) { onEscapeOverlayChanged(showPin || showAdmin) }
        // Opening the PIN pad pulls the latest remote settings, so a PIN changed in the admin web
        // works right away instead of waiting out the 30-minute cadence. Failures are silent
        // (repo.load never throws) — the previously known PIN still opens the gate.
        LaunchedEffect(showPin) { if (showPin) configHolder.refresh() }

        // Last successful remote-config receipt, stamped when a live fetch lands.
        var lastRemoteAt by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(config) { if (config.fromRemote) lastRemoteAt = System.currentTimeMillis() }

        // Single idle-return authority for the whole kiosk. Monotonic clock so wall-clock
        // changes never skew it; tests inject a fake clock instead.
        val machine = remember {
            IdleReturnMachine(
                clock = { SystemClock.uptimeMillis() },
                idleReturnSeconds = config.settings.idleReturnSeconds,
            )
        }
        LaunchedEffect(config.settings.idleReturnSeconds) {
            machine.idleReturnSeconds = config.settings.idleReturnSeconds
        }
        // Playing vs browsing drives which rule applies; entering either resets the run.
        LaunchedEffect(nav.isPlaying) { machine.setPlaying(nav.isPlaying) }

        val homeListState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        fun goHome() {
            nav = nav.returnToHome()
            machine.onReturnHome()
            scope.launch { homeListState.scrollToItem(0) }
        }

        // Rule A (browsing): poll the idle timeout once a second. Playback is exempt —
        // rule B (auto-advance count) handles it, so a still viewer is never interrupted.
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                if (machine.isIdleExpired()) goHome()
            }
        }

        // Safety belt: only enter the lock AFTER this composition (with the escape
        // gesture + PIN/admin overlays below) is live. Never lock on start unconditionally.
        LaunchedEffect(Unit) { onEnterLock() }

        // Whole-screen touch observer. Runs on the Initial pass so it sees every touch
        // before any child (including the player's event-consuming shield) without
        // stealing it — observe only, never consume.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed }) machine.onTouch()
                        }
                    }
                }
        ) {
            val session = nav.playback
            val part = nav.selectedPart
            when {
                // Player sits on top of the detail screen; leaving it returns to the list.
                session != null -> PlayerScreen(
                    videos = session.videos,
                    startIndex = session.startIndex,
                    onBack = { nav = nav.closePlayer() },
                    onAutoAdvance = {
                        val goHomeNow = machine.onAutoAdvance()
                        if (goHomeNow) goHome()
                        goHomeNow
                    },
                    onManualNav = { machine.onManualNext() },
                )
                part != null -> PartDetailScreen(
                    part = part,
                    description = descriptions[part.playlistId].orEmpty(),
                    onBack = { nav = nav.closePart() },
                    onPlay = { videos, startIndex -> nav = nav.play(videos, startIndex) },
                )
                else -> HomeScreen(
                    parts = config.parts,
                    listState = homeListState,
                    onPartClick = { nav = nav.openPart(it) },
                )
            }

            // Escape path #1: hidden top-right hotspot, 3s long-press -> PIN. Topmost and
            // on the default (Main) pass, so it fires on every screen including the
            // player's event-consuming shield below it.
            if (!showPin && !showAdmin) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(96.dp)
                        .testTag("pinHotspot")
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val releasedEarly = withTimeoutOrNull(3000L) {
                                    waitForUpOrCancellation()
                                    true
                                }
                                if (releasedEarly == null) showPin = true
                            }
                        }
                )
            }

            if (showPin) {
                PinPad(
                    onSubmit = { pin ->
                        pinGate.submit(
                            pin,
                            PinGate.effectiveHash(config.settings.adminPinHash),
                            alsoAcceptHash = config.settings.previousAdminPinHash,
                        )
                    },
                    lockedSecondsLeft = { pinGate.lockedSecondsLeft() },
                    onAccepted = { showPin = false; showAdmin = true },
                    onDismiss = { showPin = false },
                )
            }

            if (showAdmin) {
                AdminScreen(
                    lockMode = lockMode,
                    appVersion = BuildConfig.VERSION_NAME,
                    lastRemoteAtMillis = lastRemoteAt,
                    returnSeconds = config.settings.idleReturnSeconds,
                    partCount = config.parts.size,
                    adminWebUrl = BuildConfig.ADMIN_WEB_URL,
                    refreshing = configHolder.refreshing,
                    onRefresh = configHolder.refresh,
                    onReleaseFully = onReleaseFully,
                    onExitApp = onExitApp,
                    onClose = { showAdmin = false },
                )
            }
        }
    }
}

/**
 * Cache-first playlist descriptions, keyed by playlistId. One `playlists.list` call for all
 * parts; the map is empty until it resolves, so the detail panel just hides the block.
 */
@Composable
private fun rememberDescriptions(): Map<String, String> {
    val context = LocalContext.current
    val playlistIds = remember { PartsRepository.load(context).map { it.playlistId } }
    val state = produceState(initialValue = emptyMap<String, String>(), playlistIds) {
        val source = RetrofitYoutubeSource(YoutubeService.api, BuildConfig.YOUTUBE_API_KEY)
        val repo = PlaylistMetaRepository(source, FileMetaCacheStore(context.filesDir))
        value = repo.descriptions(playlistIds)
    }
    return state.value
}

/** Live config plus the manual-refresh hook the admin screen's "설정 새로고침" button uses. */
class KioskConfigHolder(
    val config: KioskConfig,
    val refreshing: Boolean,
    val refresh: () -> Unit,
)

/**
 * Kiosk config with the three-stage fallback (Supabase -> cache -> bundled assets).
 * Renders instantly from bundled assets, then loads once at start and re-fetches every
 * 30 minutes. A failed fetch changes nothing on screen — it silently keeps prior values.
 *
 * [KioskConfigHolder.refresh] bumps a key that restarts the load loop, so the admin can
 * pull new remote settings immediately instead of waiting out the 30-minute cadence.
 */
@Composable
private fun rememberKioskConfig(): KioskConfigHolder {
    val context = LocalContext.current
    val bundled = remember { PartsRepository.load(context) }
    var config by remember { mutableStateOf(KioskConfig(bundled, KioskSettings())) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    val repo = remember {
        KioskConfigRepository(
            source = RetrofitSupabaseSource(
                SupabaseService.create(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
            ),
            cache = FileConfigCacheStore(context.filesDir),
            bundled = { bundled },
        )
    }
    // Keyed on refreshKey: a manual refresh cancels this and re-enters, loading immediately
    // and then resuming the 30-minute cadence from that point.
    LaunchedEffect(refreshKey) {
        while (true) {
            refreshing = true
            config = repo.load()
            refreshing = false
            delay(30 * 60 * 1000L) // once now, then every 30 minutes
        }
    }
    val refresh: () -> Unit = remember { { refreshKey += 1 } }
    return KioskConfigHolder(config, refreshing, refresh)
}

@Composable
internal fun HomeScreen(
    parts: List<Part>,
    listState: LazyListState,
    onPartClick: (Part) -> Unit,
) {
    // Kiosk main screen swallows the back gesture — it must never exit the app.
    BackHandler(enabled = true) { /* no-op */ }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BodyBg)
    ) {
        val bandHeight = maxHeight * TOP_BAND_FRACTION
        Column(modifier = Modifier.fillMaxSize()) {
            TopBand(bandHeight = bandHeight, modifier = Modifier.height(bandHeight))
            // Header + card row are one block, tied together by a small gap, and the block is
            // centered vertically in the area below the red band so the header never floats off
            // on its own above the cards.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopContentHeader(count = parts.size)
                    Spacer(Modifier.height(HeaderCardGap))
                    PartsRow(
                        parts = parts,
                        listState = listState,
                        onPartClick = onPartClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBand(bandHeight: Dp, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NowflixRed)
            .padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Wordmark drawn as text — no PNG, so no transparent-edge rectangle bleeds through the
        // red band, and it stays crisp when parts are added or the resolution changes. Bold wide-
        // tracked white sans on the solid #E50914 band, sized as a fraction of the band height so
        // it keeps generous margins above/below rather than filling the band.
        Text(
            text = "NOWFLIX",
            color = Color.White,
            fontSize = (bandHeight.value * 0.34f).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (bandHeight.value * 0.03f).sp,
        )
        Image(
            painter = painterResource(R.drawable.nowflix_qr),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxHeight(0.62f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun TopContentHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RowSidePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(NowflixRed)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "TOP $count",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "오늘 대한민국의 TOP $count 콘텐츠",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun PartsRow(
    parts: List<Part>,
    listState: LazyListState,
    onPartClick: (Part) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        // Width follows the screen-width constraint only (six columns fill the row ~edge-to-edge);
        // the row now wraps its own height, so no height cap is applied. Extra parts keep this
        // width and scroll.
        val cardWidthDp = CardMetrics.cardWidthDp(
            rowWidthDp = maxWidth.value,
            itemCount = parts.size,
            sidePaddingDp = RowSidePadding.value,
            gapDp = CardGap.value,
        ).dp
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = RowSidePadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(CardGap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(parts, key = { it.id }) { part ->
                PartCard(part = part, width = cardWidthDp, onClick = { onPartClick(part) })
            }
        }
    }
}

@Composable
private fun PartCard(part: Part, width: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.06f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "cardScale",
    )
    val borderColor = if (pressed) NowflixRed else Color.Transparent

    Column(
        modifier = Modifier
            .width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                Log.d(TAG, "card tap: ${part.id} playlist=${part.playlistId}")
                onClick()
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT)
                .clip(RoundedCornerShape(CardCorner))
                .background(PlaceholderTile)
                .border(2.dp, borderColor, RoundedCornerShape(CardCorner)),
            contentAlignment = Alignment.Center,
        ) {
            Thumbnail(part = part)
        }
        Text(
            text = part.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun Thumbnail(part: Part) {
    val context = LocalContext.current
    // Resolve the bundled drawable by name at runtime — a missing image just shows the
    // placeholder tile, so parts can be added before their artwork exists.
    val resId = remember(part.thumbnail) {
        if (part.thumbnail.isBlank()) 0
        else context.resources.getIdentifier(part.thumbnail, "drawable", context.packageName)
    }
    val drawable = if (resId != 0) painterResource(id = resId) else null

    when {
        // Remote thumbnail (Coil, disk cache on). Any load failure falls back to the
        // bundled drawable; a null fallback just leaves the placeholder tile showing.
        part.thumbnailUrl.isNotBlank() -> AsyncImage(
            model = ImageRequest.Builder(context)
                .data(part.thumbnailUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(false)
                .build(),
            contentDescription = part.title,
            // Crop, not Fit: the poster fills the whole card with no letterbox bars. Any
            // ratio mismatch is trimmed rather than boxed or stretched.
            contentScale = ContentScale.Crop,
            placeholder = drawable,
            error = drawable,
            fallback = drawable,
            modifier = Modifier.fillMaxSize(),
        )

        drawable != null -> Image(
            painter = drawable,
            contentDescription = part.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        else -> Text(
            text = part.title,
            color = Color(0xFF6B6B70),
            fontSize = 13.sp,
            modifier = Modifier.padding(8.dp),
        )
    }
}
