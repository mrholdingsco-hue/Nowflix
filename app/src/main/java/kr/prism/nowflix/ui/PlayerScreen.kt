package kr.prism.nowflix.ui

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kr.prism.nowflix.data.Video
import kr.prism.nowflix.player.PlaybackQueue
import kr.prism.nowflix.player.PlayerProgress
import kotlinx.coroutines.delay
import java.time.OffsetDateTime

private const val TAG = "Nowflix"
private val Background = Color(0xFF0F0F0F)
// Pill/scrim behind the on-video controls. Kept dark enough that white glyphs stay legible even
// over a bright video frame (raised from 40% to 60% black).
private val ControlScrim = Color(0x99000000)
// Gradient laid under the always-on bottom bar so the progress line + time never wash out on a
// bright frame. Transparent at the top, dark at the very bottom.
private val BottomScrim = Brush.verticalGradient(
    0f to Color.Transparent,
    1f to Color(0xB3000000),
)
private val TrackRemaining = Color(0x4DFFFFFF) // white @ 30%
private val ProgressRed = Color(0xFFFF0000)
private const val CONTROLS_TIMEOUT_MS = 3000L

/**
 * Full-screen player. The user should feel they are inside the YouTube app while being
 * physically unable to leave it: the official IFrame embed plays the video, but a
 * touch-absorbing overlay + a locked-down WebView block every path out of the embed.
 */
@Composable
fun PlayerScreen(
    videos: List<Video>,
    startIndex: Int,
    onBack: () -> Unit,
    // A video ended and the next is about to auto-start. Returns true when the idle rule has
    // tripped (too many touch-free auto-advances) and the host is returning home — stop here.
    onAutoAdvance: () -> Boolean = { false },
    // The user deliberately changed video (next button / row pick) — never an auto-advance.
    onManualNav: () -> Unit = {},
) {
    if (videos.isEmpty()) {
        // Nothing to play — treat as "return to the list".
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // Android back gesture on the player only ever returns to the list.
    BackHandler(enabled = true) { onBack() }

    var currentIndex by remember { mutableIntStateOf(startIndex.coerceIn(0, videos.lastIndex)) }
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    val currentSec = remember { mutableFloatStateOf(0f) }
    val durationSec = remember { mutableFloatStateOf(0f) }
    val scrubbing = remember { mutableStateOf(false) }
    val now = remember { OffsetDateTime.now() }

    // Load the next video, or return to the list when there is none.
    fun advanceTo(next: Int?) {
        when (next) {
            null -> onBack()
            else -> currentIndex = next
        }
    }

    // Auto-advance (a video ended on its own). Reports to the idle rule first: if it says the
    // seat is empty, the host is tearing the player down, so don't load anything. Wrapped in
    // rememberUpdatedState so the once-created player listener always sees the latest index.
    val advanceAuto by rememberUpdatedState {
        if (!onAutoAdvance()) advanceTo(PlaybackQueue.nextIndex(currentIndex, videos.size))
    }
    // Manual next ("다음 영상" button): a deliberate action, so it resets the auto-advance run.
    val advanceManual by rememberUpdatedState {
        onManualNav()
        advanceTo(PlaybackQueue.nextIndex(currentIndex, videos.size))
    }

    val listener = remember {
        object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                player = youTubePlayer
            }

            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                if (!scrubbing.value) currentSec.floatValue = second
            }

            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                durationSec.floatValue = duration
            }

            override fun onStateChange(
                youTubePlayer: YouTubePlayer,
                state: PlayerConstants.PlayerState,
            ) {
                when (state) {
                    PlayerConstants.PlayerState.PLAYING -> isPlaying = true
                    PlayerConstants.PlayerState.PAUSED -> isPlaying = false
                    // Switch the instant the video ends, before the end-screen recommendations
                    // (which are exits out of the embed) get a chance to render.
                    PlayerConstants.PlayerState.ENDED -> {
                        isPlaying = false
                        advanceAuto()
                    }
                    else -> Unit
                }
            }
        }
    }

    // Load whenever the player becomes ready or the selection changes. loadVideo autoplays.
    LaunchedEffect(player, currentIndex) {
        val p = player ?: return@LaunchedEffect
        currentSec.floatValue = 0f
        durationSec.floatValue = 0f
        p.loadVideo(videos[currentIndex].videoId, 0f)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // Left ~65%: player + title/meta.
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight(),
        ) {
            PlayerStage(
                listener = listener,
                isPlaying = isPlaying,
                currentSec = currentSec,
                durationSec = durationSec,
                scrubbing = scrubbing,
                showNext = !PlaybackQueue.isLast(currentIndex, videos.size),
                onTogglePlay = {
                    player?.let { if (isPlaying) it.pause() else it.play() }
                },
                onSeek = { fraction ->
                    player?.seekTo(PlayerProgress.seekSeconds(fraction, durationSec.floatValue))
                },
                onNext = { advanceManual() },
                onBack = onBack,
            )
            NowPlayingMeta(video = videos[currentIndex], now = now)
        }

        // Right ~35%: "다음 동영상" up-next list, current at top and highlighted.
        UpNextList(
            videos = videos,
            currentIndex = currentIndex,
            now = now,
            onSelect = { index -> onManualNav(); currentIndex = index },
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun PlayerStage(
    listener: AbstractYouTubePlayerListener,
    isPlaying: Boolean,
    currentSec: MutableFloatState,
    durationSec: MutableFloatState,
    scrubbing: androidx.compose.runtime.MutableState<Boolean>,
    showNext: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var controlsVisible by remember { mutableStateOf(true) }
    // Bumped on every touch to restart the 3-second auto-hide countdown.
    var interactionTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(interactionTick, controlsVisible, isPlaying) {
        // Keep the controls up while paused; only fade them out during playback.
        if (controlsVisible && isPlaying) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    fun revealControls() {
        controlsVisible = true
        interactionTick++
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                YouTubePlayerView(ctx).apply {
                    // The embed's WebView defaults to a white page; paint the whole view stack black
                    // so the video area is never a white flash before/behind the frame.
                    setBackgroundColor(android.graphics.Color.BLACK)
                    enableAutomaticInitialization = false
                    val options = IFramePlayerOptions.Builder(ctx)
                        .controls(0)    // hide the iframe's own web UI — we draw our own
                        .rel(0)         // no cross-channel related videos
                        .fullscreen(0)  // no fullscreen button (so no FullscreenListener needed)
                        .ccLoadPolicy(0) // captions off
                        .autoplay(1)
                        .build()
                    initialize(listener, options)
                    // Pause/resume with the app; the view auto-releases on ON_DESTROY too.
                    lifecycleOwner.lifecycle.addObserver(this)
                    // Lock down the internal WebView as soon as it is attached.
                    post { findWebView()?.let(::lockDownWebView) }
                }
            },
            onRelease = { view ->
                lifecycleOwner.lifecycle.removeObserver(view)
                view.release()
            },
        )

        // Until the frame paints (duration still unknown), sit a gentle brand mark on the black
        // stage instead of a spinner. It fades away the moment the video reports its duration.
        if (durationSec.floatValue <= 0f) {
            LoadingMark(modifier = Modifier.align(Alignment.Center))
        }

        // Touch shield: swallows EVERY pointer event over the video (down, moves, up) so
        // nothing inside the embed — YouTube logo, video title, "Watch on YouTube", long-press
        // menu — is ever reachable. Any completed touch simply reveals our own controls.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (pointer == null || !pointer.pressed) break
                            pointer.consume()
                        }
                        revealControls()
                    }
                },
        )

        // "← 목록으로" stays visible even after the controls fade out.
        BackToListButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        )

        // Center play / pause fades out during playback (immersion); a touch brings it back.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            PlayPauseButton(
                isPlaying = isPlaying,
                onClick = { revealControls(); onTogglePlay() },
            )
        }

        // Bottom bar — progress line + time (left) and "다음 영상" (right) — is ALWAYS on, pinned
        // to the player's bottom edge over a dark gradient so it reads on any frame.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(BottomScrim)
                .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = PlayerProgress.label(currentSec.floatValue, durationSec.floatValue),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                if (showNext) {
                    NextButton(onClick = { revealControls(); onNext() })
                }
            }
            Spacer(Modifier.height(8.dp))
            SeekBar(
                currentSec = currentSec,
                durationSec = durationSec,
                scrubbing = scrubbing,
                onScrub = { revealControls() },
                onSeek = onSeek,
            )
        }
    }
}

/** Subtle, spinner-free loading state: the wordmark breathing on the black stage. */
@Composable
private fun LoadingMark(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "loadingAlpha",
    )
    Text(
        text = "NOWFLIX",
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 3.sp,
        modifier = modifier.alpha(alpha),
    )
}

@Composable
private fun SeekBar(
    currentSec: MutableFloatState,
    durationSec: MutableFloatState,
    scrubbing: androidx.compose.runtime.MutableState<Boolean>,
    onScrub: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val fraction = if (scrubbing.value) {
        scrubFraction
    } else {
        PlayerProgress.fraction(currentSec.floatValue, durationSec.floatValue)
    }

    fun fractionFor(x: Float): Float = if (widthPx <= 0f) 0f else (x / widthPx).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                // One handler covers both a tap-to-seek and a drag-to-scrub: the knob follows
                // the finger while pressed, and the seek is committed on release.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    scrubbing.value = true
                    scrubFraction = fractionFor(down.position.x)
                    onScrub()
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer == null || !pointer.pressed) break
                        pointer.consume()
                        scrubFraction = fractionFor(pointer.position.x)
                        onScrub()
                    }
                    onSeek(scrubFraction)
                    scrubbing.value = false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Remaining track (white @ 30%).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(TrackRemaining),
        )
        // Played track (#FF0000).
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ProgressRed),
        )
        // Scrubber knob.
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(ProgressRed),
            )
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(ControlScrim)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // Simple, dependency-free glyphs; no share/like/etc. controls anywhere.
            text = if (isPlaying) "❚❚" else "▶",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BackToListButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(ControlScrim)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = "←  목록으로",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun NextButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ControlScrim)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = "다음 영상  ▶",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun NowPlayingMeta(video: Video, now: OffsetDateTime) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
        Text(
            text = video.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = metaLine(video, now)
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = meta,
                color = MetaGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UpNextList(
    videos: List<Video>,
    currentIndex: Int,
    now: OffsetDateTime,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Keep the currently-playing row anchored at the top as playback advances.
    LaunchedEffect(currentIndex) { listState.scrollToItem(currentIndex) }

    Column(modifier = modifier.padding(end = 16.dp)) {
        Text(
            text = "다음 동영상",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(videos, key = { _, v -> v.videoId }) { index, video ->
                // Show the current video and everything after it — the up-next queue.
                if (index >= currentIndex) {
                    VideoRow(
                        index = index + 1,
                        video = video,
                        now = now,
                        playing = index == currentIndex,
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

/** Depth-first search for the embed's internal WebView (not publicly exposed). */
private fun View.findWebView(): WebView? {
    if (this is WebView) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findWebView()?.let { return it }
        }
    }
    return null
}

/**
 * Second, belt-and-suspenders exit block on the embed's WebView. The touch shield already
 * stops the user reaching any link; this catches programmatic escapes: any main-frame
 * navigation (a tapped logo/title, an end-screen card) is swallowed, popups are disabled,
 * and the long-press context menu / text selection are turned off.
 */
private fun lockDownWebView(web: WebView) {
    // Kill the WebView's default white page so the video area is black until the frame paints.
    web.setBackgroundColor(android.graphics.Color.BLACK)
    web.isLongClickable = false
    web.setOnLongClickListener { true }
    web.isHapticFeedbackEnabled = false
    web.settings.setSupportMultipleWindows(false) // target=_blank loads in-page -> caught below
    web.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // The player runs in sub-frames (isForMainFrame == false) — let those load.
            // A main-frame navigation is always an attempt to leave the embed: swallow it.
            if (!request.isForMainFrame) return false
            Log.d(TAG, "blocked exit navigation: ${request.url}")
            return true
        }
    }
}
