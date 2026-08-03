package kr.prism.nowflix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.delay
import kr.prism.nowflix.kiosk.PinResult

private val Overlay = Color(0xF20B0B0C) // near-opaque scrim
private val Key = Color(0xFF1F1F22)
private val Accent = Color(0xFFE50914)
private const val PIN_LENGTH = 6

/**
 * Admin-PIN entry overlay (escape path #1). Full-screen, sits above every kiosk screen.
 * Owns only its transient digit buffer; [onSubmit] does the hashing/lockout via the caller's
 * [kr.prism.nowflix.kiosk.PinGate], and [lockedSecondsLeft] lets the countdown tick live.
 */
@Composable
fun PinPad(
    onSubmit: (String) -> PinResult,
    lockedSecondsLeft: () -> Int,
    onAccepted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var entered by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var lockLeft by remember { mutableIntStateOf(lockedSecondsLeft()) }

    // Tick the lockout countdown once a second while locked; re-enable input at zero.
    LaunchedEffect(lockLeft > 0) {
        while (lockLeft > 0) {
            delay(1000)
            lockLeft = lockedSecondsLeft()
        }
    }

    val locked = lockLeft > 0

    fun press(digit: Char) {
        if (locked || entered.length >= PIN_LENGTH) return
        message = ""
        entered += digit
        if (entered.length == PIN_LENGTH) {
            when (val result = onSubmit(entered)) {
                is PinResult.Accepted -> onAccepted()
                is PinResult.Rejected -> {
                    message = "PIN이 올바르지 않습니다 · 남은 시도 ${result.remaining}회"
                    entered = ""
                }
                is PinResult.LockedOut -> {
                    lockLeft = result.secondsLeft
                    message = ""
                    entered = ""
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Overlay),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "닫기",
            color = Color(0xFFAAAAAA),
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onDismiss() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("관리자 PIN", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(PIN_LENGTH) { i ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (i < entered.length) Accent else Color(0xFF3A3A3E)),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = when {
                    locked -> "잠시 후 다시 시도해주세요 · $lockLeft 초"
                    message.isNotEmpty() -> message
                    else -> " "
                },
                color = if (locked) Accent else Color(0xFFE0A0A0),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))

            // 3x4 keypad: 1-9, then blank / 0 / backspace.
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫"),
            )
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    for (label in row) {
                        KeypadKey(
                            label = label,
                            enabled = !locked,
                            onClick = {
                                when (label) {
                                    "" -> Unit
                                    "⌫" -> if (entered.isNotEmpty()) entered = entered.dropLast(1)
                                    else -> press(label[0])
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    val clickable = enabled && label.isNotEmpty()
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(if (label.isEmpty()) Color.Transparent else Key)
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = if (enabled) Color.White else Color(0xFF666666),
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
