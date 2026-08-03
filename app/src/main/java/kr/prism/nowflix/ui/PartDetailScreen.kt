package kr.prism.nowflix.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kr.prism.nowflix.BuildConfig
import kr.prism.nowflix.Part
import kr.prism.nowflix.data.FileCacheStore
import kr.prism.nowflix.data.PlaylistRepository
import kr.prism.nowflix.data.PlaylistResult
import kr.prism.nowflix.data.RelativeDate
import kr.prism.nowflix.data.RetrofitYoutubeSource
import kr.prism.nowflix.data.Video
import kr.prism.nowflix.data.VideoDuration
import kr.prism.nowflix.data.ViewCount
import kr.prism.nowflix.data.YoutubeService
import java.time.OffsetDateTime

// YouTube dark theme. Flat #0F0F0F canvas, no cards, no dividers.
private val Background = Color(0xFF0F0F0F)
private val RowHighlight = Color(0xFF272727)
private val MetaGray = Color(0xFFAAAAAA)
private val Skeleton = Color(0xFF222222)
private val PosterTile = Color(0xFF1F1F1F)
private val NowRed = Color(0xFFE50914)
private val BadgeScrim = Color(0xCC000000)

private const val TAG = "Nowflix"
private const val THUMB_ASPECT = 16f / 9f
private val ThumbWidth = 168.dp

private sealed interface ListUiState {
    data object Skeleton : ListUiState
    data class Content(val videos: List<Video>, val total: Int) : ListUiState
    data object Empty : ListUiState
}

@Composable
fun PartDetailScreen(
    part: Part,
    onBack: () -> Unit,
    // Wired for STEP 4's player: the row matching this id shows the playing indicator.
    playingVideoId: String? = null,
) {
    // Back gesture returns to the home grid — only ever active on this screen.
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val repo = remember {
        val source = RetrofitYoutubeSource(YoutubeService.api, BuildConfig.YOUTUBE_API_KEY)
        PlaylistRepository(
            source = source,
            cache = FileCacheStore(context.filesDir),
            details = source,
        )
    }
    val ui by rememberPlaylistState(repo, part)
    // Computed once per screen so every row's "N년 전" is measured from the same instant.
    val now = remember { OffsetDateTime.now() }
    val total = (ui as? ListUiState.Content)?.total

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        LeftPanel(
            part = part,
            total = total,
            onPlayAll = { Log.d(TAG, "play all: ${part.id} playlist=${part.playlistId}") },
            onBack = onBack,
            modifier = Modifier
                .weight(0.28f)
                .fillMaxHeight()
                .padding(24.dp),
        )
        Box(
            modifier = Modifier
                .weight(0.72f)
                .fillMaxHeight()
        ) {
            when (val state = ui) {
                ListUiState.Skeleton -> SkeletonList()
                ListUiState.Empty -> EmptyMessage()
                is ListUiState.Content -> VideoList(
                    videos = state.videos,
                    now = now,
                    playingVideoId = playingVideoId,
                    onVideoClick = { v -> Log.d(TAG, "video tap: ${v.videoId} (${v.title})") },
                )
            }
        }
    }
}

@Composable
private fun rememberPlaylistState(repo: PlaylistRepository, part: Part): State<ListUiState> =
    produceState<ListUiState>(initialValue = ListUiState.Skeleton, part.id) {
        repo.stream(part.id, part.playlistId).collect { result ->
            value = when (result) {
                is PlaylistResult.Data -> ListUiState.Content(result.videos, result.totalCount)
                PlaylistResult.Unavailable -> ListUiState.Empty
            }
        }
    }

@Composable
private fun LeftPanel(
    part: Part,
    total: Int?,
    onPlayAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        PartPoster(part)
        Spacer(Modifier.height(16.dp))
        Text(
            text = part.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        if (total == null) {
            // Count unknown until the list resolves — hold a small gray placeholder.
            Box(
                Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Skeleton)
            )
        } else {
            Text(
                text = "재생목록 · 동영상 ${total}개",
                color = MetaGray,
                fontSize = 12.sp,
            )
        }
        if (part.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = part.description,
                color = MetaGray,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(20.dp))
        PlayAllButton(onClick = onPlayAll)
        Spacer(Modifier.height(6.dp))
        BackTextButton(onClick = onBack)
    }
}

@Composable
private fun PlayAllButton(onClick: () -> Unit) {
    // White pill, black label — YouTube's primary playlist action.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "▶  모두 재생",
            color = Color(0xFF0F0F0F),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BackTextButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "메인으로",
            color = Color(0xFFF1F1F1),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PartPoster(part: Part) {
    val context = LocalContext.current
    val resId = remember(part.thumbnail) {
        context.resources.getIdentifier(part.thumbnail, "drawable", context.packageName)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(THUMB_ASPECT)
            .clip(RoundedCornerShape(8.dp))
            .background(PosterTile),
    ) {
        if (resId != 0) {
            Image(
                painter = painterResource(resId),
                contentDescription = part.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun VideoList(
    videos: List<Video>,
    now: OffsetDateTime,
    playingVideoId: String?,
    onVideoClick: (Video) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 4.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
    ) {
        itemsIndexed(videos, key = { _, v -> v.videoId }) { index, video ->
            VideoRow(
                index = index + 1,
                video = video,
                now = now,
                playing = video.videoId == playingVideoId,
                onClick = { onVideoClick(video) },
            )
        }
    }
}

@Composable
private fun VideoRow(
    index: Int,
    video: Video,
    now: OffsetDateTime,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rowBg by animateColorAsState(
        targetValue = if (playing || pressed) RowHighlight else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "rowBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBg)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left red bar marks the currently-playing row (STEP 4 sets playingVideoId).
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(94.dp)
                .background(if (playing) NowRed else Color.Transparent)
        )
        Text(
            text = "$index",
            color = MetaGray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(30.dp),
        )
        VideoThumbnail(video)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
        ) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = metaLine(video, now)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
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
}

/** "고쳐줘 NOW · 조회수 6.9만회 · 6년 전" — each segment dropped when its data is absent. */
private fun metaLine(video: Video, now: OffsetDateTime): String = listOfNotNull(
    video.channelTitle.takeIf { it.isNotBlank() },
    if (video.viewCount > 0) ViewCount.format(video.viewCount) else null,
    RelativeDate.format(video.publishedAt, now).takeIf { it.isNotBlank() },
).joinToString(" · ")

@Composable
private fun VideoThumbnail(video: Video) {
    Box(
        modifier = Modifier
            .width(ThumbWidth)
            .aspectRatio(THUMB_ASPECT)
            .clip(RoundedCornerShape(8.dp))
            .background(PosterTile),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (video.durationSeconds > 0) {
            // Signature YouTube tell: black-scrim duration badge, bottom-right.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BadgeScrim)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = VideoDuration.format(video.durationSeconds),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SkeletonList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 34.dp, end = 24.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(6) { SkeletonRow() }
    }
}

@Composable
private fun SkeletonRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(ThumbWidth)
                .aspectRatio(THUMB_ASPECT)
                .clip(RoundedCornerShape(8.dp))
                .background(Skeleton)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Skeleton)
            )
            Box(
                Modifier
                    .fillMaxWidth(0.45f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Skeleton)
            )
        }
    }
}

@Composable
private fun EmptyMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "잠시 후 다시 시도해주세요",
            color = MetaGray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}
