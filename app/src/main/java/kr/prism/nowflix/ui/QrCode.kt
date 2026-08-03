package kr.prism.nowflix.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders [content] as a QR code. Generation is cheap for a short URL and cached by
 * [remember], so it runs once per distinct content. Black modules on a solid white
 * field — the orientation scanners expect — so it reads reliably from a phone camera.
 */
@Composable
fun QrCode(content: String, sizePx: Int, modifier: Modifier = Modifier) {
    val bitmap = remember(content, sizePx) { encodeQr(content, sizePx) }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "관리자 웹 QR 코드", modifier = modifier)
}

private fun encodeQr(content: String, size: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}
