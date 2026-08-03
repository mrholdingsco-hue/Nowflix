package kr.prism.nowflix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.prism.nowflix.kiosk.KioskLockMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF0F0F0F)
private val Card = Color(0xFF17171A)
private val Accent = Color(0xFFE50914)
private val Muted = Color(0xFFAAAAAA)

/**
 * Admin panel reached after a correct PIN (escape path #2). STEP 7 scope: show live state and
 * the two safety actions. PIN change + part management are STEP 8 — placeholders only here.
 */
@Composable
fun AdminScreen(
    lockMode: KioskLockMode,
    appVersion: String,
    lastRemoteAtMillis: Long?,
    onReleaseFully: () -> Unit,
    onExitApp: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmRelease by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(32.dp),
    ) {
        Text(
            text = "닫기",
            color = Muted,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onClose() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Text("관리자", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            InfoCard {
                InfoRow("잠금 모드", lockModeLabel(lockMode))
                InfoRow("앱 버전", appVersion)
                InfoRow("마지막 원격 설정 수신", formatRemoteTime(lastRemoteAtMillis))
            }

            Spacer(Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ActionButton(
                    text = "키오스크 모드 완전 해제",
                    filled = true,
                    onClick = { confirmRelease = true },
                )
                ActionButton(text = "앱 종료", filled = false, onClick = onExitApp)
            }

            Spacer(Modifier.height(28.dp))

            // STEP 8 placeholders — reserved slots, intentionally inert.
            Text("설정 (STEP 8 예정)", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            DisabledRow("PIN 변경")
            DisabledRow("파트 관리")
        }
    }

    if (confirmRelease) {
        AlertDialog(
            onDismissRequest = { confirmRelease = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmRelease = false
                    onReleaseFully()
                }) { Text("완전 해제", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRelease = false }) { Text("취소", color = Muted) }
            },
            title = { Text("키오스크 모드 완전 해제") },
            text = {
                Text(
                    "device owner 권한과 홈 런처 설정을 해제하고 앱을 종료합니다. " +
                        "태블릿이 공장초기화 없이 평범한 상태로 돌아갑니다. 계속할까요?",
                )
            },
            containerColor = Card,
            titleContentColor = Color.White,
            textContentColor = Muted,
        )
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 15.sp, modifier = Modifier.width(200.dp))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (filled) Modifier.background(Accent)
                else Modifier.border(1.dp, Muted, RoundedCornerShape(8.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DisabledRow(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141416))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(text, color = Color(0xFF5A5A5E), fontSize = 15.sp)
    }
}

private fun lockModeLabel(mode: KioskLockMode): String = when (mode) {
    KioskLockMode.FULL_LOCK -> "완전 잠금 (device owner)"
    KioskLockMode.FALLBACK -> "차선책 (화면 고정 + 홈 런처)"
}

private fun formatRemoteTime(millis: Long?): String {
    if (millis == null) return "아직 수신 없음"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
    return fmt.format(Date(millis))
}
