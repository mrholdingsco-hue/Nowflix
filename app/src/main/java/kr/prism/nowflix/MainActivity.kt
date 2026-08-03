package kr.prism.nowflix

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private val NowflixRed = Color(0xFFE50914)
private val BodyBg = Color(0xFF0B0B0C)
private val PlaceholderTile = Color(0xFF1A1A1D)

private const val TAG = "Nowflix"

// Top red band is 18% of screen height.
private const val TOP_BAND_FRACTION = 0.18f
// Row geometry — also feeds CardMetrics for the width calc.
private val RowSidePadding = 24.dp
private val CardGap = 12.dp
// Thumbnail slot ratio (YouTube playlist thumbs are 16:9). Real images are drawn
// Fit inside this slot, so their own ratio is preserved once assets land.
private const val THUMB_ASPECT = 16f / 9f
private val CardCorner = 4.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            MaterialTheme {
                HomeScreen()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Kiosk: keep system bars hidden even after transient system UI shows them.
        if (hasFocus) hideSystemBars()
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

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val parts = remember { PartsRepository.load(context) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BodyBg)
    ) {
        val bandHeight = maxHeight * TOP_BAND_FRACTION
        Column(modifier = Modifier.fillMaxSize()) {
            TopBand(modifier = Modifier.height(bandHeight))
            TopContentHeader(count = parts.size)
            PartsRow(parts = parts)
        }
    }
}

@Composable
private fun TopBand(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NowflixRed)
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // TODO: swap for the extracted NOWFLIX logo PNG once the asset lands.
        Text(
            text = "NOWFLIX",
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
        QrPlaceholder(modifier = Modifier.fillMaxHeight(0.62f))
    }
}

@Composable
private fun QrPlaceholder(modifier: Modifier = Modifier) {
    // TODO: swap for the extracted QR PNG once the asset lands.
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "QR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun TopContentHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = RowSidePadding, end = RowSidePadding, top = 20.dp, bottom = 12.dp),
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
private fun PartsRow(parts: List<Part>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidthDp = CardMetrics.cardWidthDp(
            rowWidthDp = maxWidth.value,
            itemCount = parts.size,
            sidePaddingDp = RowSidePadding.value,
            gapDp = CardGap.value,
        ).dp
        LazyRow(
            contentPadding = PaddingValues(horizontal = RowSidePadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(CardGap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(parts, key = { it.id }) { part ->
                PartCard(part = part, width = cardWidthDp)
            }
        }
    }
}

@Composable
private fun PartCard(part: Part, width: Dp) {
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
            ) { Log.d(TAG, "card tap: ${part.id} playlist=${part.playlistId}") }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(THUMB_ASPECT)
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
    // Resolve the drawable by name at runtime — a missing image just shows the
    // placeholder tile, so parts can be added before their artwork exists.
    val resId = remember(part.thumbnail) {
        context.resources.getIdentifier(part.thumbnail, "drawable", context.packageName)
    }
    if (resId != 0) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = part.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Text(
            text = part.title,
            color = Color(0xFF6B6B70),
            fontSize = 13.sp,
            modifier = Modifier.padding(8.dp),
        )
    }
}
