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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import kr.prism.nowflix.data.PublishDate
import kr.prism.nowflix.data.RetrofitPlaylistSource
import kr.prism.nowflix.data.Video
import kr.prism.nowflix.data.YoutubeService

private val NowflixRed = Color(0xFFE50914)
private val BodyBg = Color(0xFF0B0B0C)
private val Tile = Color(0xFF1A1A1D)
private val SkeletonTint = Color(0xFF202024)
private val SubtleText = Color(0xFF9A9AA0)

private const val TAG = "Nowflix"
private const val THUMB_ASPECT = 16f / 9f

private sealed interface ListUiState {
    data object Skeleton : ListUiState
    data class Content(val videos: List<Video>) : ListUiState
    data object Empty : ListUiState
}

@Composable
fun PartDetailScreen(part: Part, onBack: () -> Unit) {
    // Back gesture returns to the home grid — only ever active on this screen.
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val repo = remember {
        PlaylistRepository(
            source = RetrofitPlaylistSource(YoutubeService.api, BuildConfig.YOUTUBE_API_KEY),
            cache = FileCacheStore(context.filesDir),
        )
    }
    val ui by rememberPlaylistState(repo, part)
    val videoCount = (ui as? ListUiState.Content)?.videos?.size

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BodyBg)
    ) {
        LeftPanel(
            part = part,
            count = videoCount,
            onPlayAll = { Log.d(TAG, "play all: ${part.id} playlist=${part.playlistId}") },
            onBack = onBack,
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight()
                .padding(28.dp),
        )
        Box(
            modifier = Modifier
                .weight(0.68f)
                .fillMaxHeight()
        ) {
            when (val state = ui) {
                ListUiState.Skeleton -> SkeletonList()
                ListUiState.Empty -> EmptyMessage()
                is ListUiState.Content -> VideoList(
                    videos = state.videos,
                    onVideoClick = { v ->
                        Log.d(TAG, "video tap: ${v.videoId} (${v.title})")
                    },
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
                is PlaylistResult.Data -> ListUiState.Content(result.videos)
                PlaylistResult.Unavailable -> ListUiState.Empty
            }
        }
    }

@Composable
private fun LeftPanel(
    part: Part,
    count: Int?,
    onPlayAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        PartPoster(part)
        Spacer(Modifier.height(20.dp))
        Text(
            text = part.title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        if (count == null) {
            // Count unknown until the list resolves — hold a small gray placeholder.
            Box(
                Modifier
                    .width(72.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SkeletonTint)
            )
        } else {
            Text(text = "영상 ${count}편", color = SubtleText, fontSize = 15.sp)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onPlayAll,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NowflixRed, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("▶  모두 재생", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onBack,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2A2A2E),
                contentColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("메인으로", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
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
            .background(Tile),
        contentAlignment = Alignment.Center,
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
private fun VideoList(videos: List<Video>, onVideoClick: (Video) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 28.dp, top = 20.dp, bottom = 28.dp),
    ) {
        itemsIndexed(videos, key = { _, v -> v.videoId }) { index, video ->
            VideoRow(index = index + 1, video = video, onClick = { onVideoClick(video) })
        }
    }
}

@Composable
private fun VideoRow(index: Int, video: Video, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rowBg by animateColorAsState(
        targetValue = if (pressed) Color.White.copy(alpha = 0.06f) else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "rowBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(rowBg)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$index",
            color = SubtleText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp),
        )
        Box(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(THUMB_ASPECT)
                .clip(RoundedCornerShape(4.dp))
                .background(Tile),
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val date = PublishDate.format(video.publishedAt)
            if (date.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(text = date, color = SubtleText, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SkeletonList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 28.dp, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        repeat(6) { SkeletonRow() }
    }
}

@Composable
private fun SkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(36.dp))
        Box(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(THUMB_ASPECT)
                .clip(RoundedCornerShape(4.dp))
                .background(SkeletonTint)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SkeletonTint)
            )
            Box(
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SkeletonTint)
            )
        }
    }
}

@Composable
private fun EmptyMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "잠시 후 다시 시도해주세요",
            color = SubtleText,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}
