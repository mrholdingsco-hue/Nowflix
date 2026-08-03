package kr.prism.nowflix.data

import java.util.Locale

/**
 * View count -> a Korean YouTube meta label. Under 만 (10,000) shows the exact grouped
 * number; from 만 up it abbreviates in 만 units the way YouTube does — one decimal
 * below 10만, a whole number above. Pure and JVM-testable.
 */
object ViewCount {

    /** `1234` -> `"조회수 1,234회"`, `69000` -> `"조회수 6.9만회"`, `690000` -> `"조회수 69만회"`. */
    fun format(count: Long): String = "조회수 ${abbreviate(count)}회"

    private fun abbreviate(count: Long): String {
        val n = count.coerceAtLeast(0L)
        if (n < 10_000) return "%,d".format(Locale.US, n)

        val man = n / 10_000.0
        return if (n < 100_000) {
            // 1만 ~ 9.9만: one decimal, but drop a trailing ".0" (6만, not 6.0만).
            val oneDp = "%.1f".format(Locale.US, man)
            val trimmed = if (oneDp.endsWith(".0")) oneDp.dropLast(2) else oneDp
            "${trimmed}만"
        } else {
            // 10만 and up: whole 만 units, rounded.
            "%.0f".format(Locale.US, man) + "만"
        }
    }
}
