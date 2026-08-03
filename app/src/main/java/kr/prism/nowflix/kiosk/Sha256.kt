package kr.prism.nowflix.kiosk

import java.security.MessageDigest

/**
 * Lowercase-hex SHA-256. Pure (java.security only), so it runs in JVM unit tests and is used
 * both to hash a typed PIN for comparison and to derive the built-in default admin hash.
 */
object Sha256 {
    fun hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append(HEX[v ushr 4])
                append(HEX[v and 0x0F])
            }
        }
    }

    private const val HEX = "0123456789abcdef"
}
