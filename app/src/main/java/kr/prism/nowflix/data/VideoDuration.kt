package kr.prism.nowflix.data

import java.time.Duration

/**
 * ISO-8601 video duration (`PT3M49S`) -> seconds -> a YouTube-style clock label.
 * Both halves are pure and JVM-testable; the badge on each thumbnail renders [format].
 */
object VideoDuration {

    /** Parses `PT#H#M#S` to whole seconds; 0 on blank/unparseable input. */
    fun parseSeconds(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            Duration.parse(iso).seconds.coerceAtLeast(0L)
        } catch (e: Exception) {
            0L
        }
    }

    /** `229` -> `"3:49"`, `3733` -> `"1:02:13"`. Minutes/seconds always 2 digits past the lead. */
    fun format(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0L)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val seconds = s % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
