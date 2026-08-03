package kr.prism.nowflix.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kr.prism.nowflix.data.RelativeDate
import kr.prism.nowflix.data.Video
import kr.prism.nowflix.data.VideoDuration
import kr.prism.nowflix.data.ViewCount
import java.time.OffsetDateTime

// Shared YouTube-dark row palette + geometry, used identically by the part detail list and
// the player's "다음 동영상" list so the two never drift.
internal val RowHighlight = Color(0xFF272727)
internal val MetaGray = Color(0xFFAAAAAA)
internal val PosterTile = Color(0xFF1F1F1F)
internal val NowRed = Color(0xFFE50914)
internal val BadgeScrim = Color(0xCC000000)
internal val ThumbWidth = 168.dp
internal const val THUMB_ASPECT = 16f / 9f

/**
 * One playlist row: [순번][16:9 썸네일 + 시간뱃지][제목 2줄][고쳐줘 NOW · 조회수 · N년 전].
 * [playing] paints the row #272727 with a left red bar; [now] is passed in so every row's
 * relative date is measured from one instant.
 */
@Composable
internal fun VideoRow(
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
        // Left red bar marks the currently-playing row.
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
internal fun metaLine(video: Video, now: OffsetDateTime): String = listOfNotNull(
    video.channelTitle.takeIf { it.isNotBlank() },
    if (video.viewCount > 0) ViewCount.format(video.viewCount) else null,
    RelativeDate.format(video.publishedAt, now).takeIf { it.isNotBlank() },
).joinToString(" · ")

@Composable
internal fun VideoThumbnail(video: Video) {
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
