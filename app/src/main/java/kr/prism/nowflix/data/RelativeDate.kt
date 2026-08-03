package kr.prism.nowflix.data

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Absolute upload instant -> a relative "6년 전 / 3개월 전 / 2주 전" label, YouTube-style.
 * [now] is passed in rather than read from the clock so the ladder is JVM-testable; the
 * UI supplies `OffsetDateTime.now()` at render time.
 */
object RelativeDate {

    fun format(iso: String, now: OffsetDateTime): String {
        if (iso.isBlank()) return ""
        val then = try {
            OffsetDateTime.parse(iso)
        } catch (e: Exception) {
            return ""
        }

        val seconds = ChronoUnit.SECONDS.between(then, now)
        if (seconds <= 0) return "방금 전"

        val days = seconds / 86_400
        return when {
            days >= 365 -> "${days / 365}년 전"
            days >= 30 -> "${days / 30}개월 전"
            days >= 7 -> "${days / 7}주 전"
            days >= 1 -> "${days}일 전"
            seconds >= 3600 -> "${seconds / 3600}시간 전"
            seconds >= 60 -> "${seconds / 60}분 전"
            else -> "방금 전"
        }
    }
}
