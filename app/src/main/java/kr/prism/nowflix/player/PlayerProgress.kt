package kr.prism.nowflix.player

import kr.prism.nowflix.data.VideoDuration

/**
 * Pure progress-bar math + the "1:28 / 3:49" label. Separated from the composable so the
 * fraction/seek/format rules are JVM-testable and never drift from what the bar draws.
 */
object PlayerProgress {

    /** Played fraction in [0f, 1f]; 0 when the duration is unknown (<= 0). */
    fun fraction(currentSeconds: Float, durationSeconds: Float): Float {
        if (durationSeconds <= 0f) return 0f
        return (currentSeconds / durationSeconds).coerceIn(0f, 1f)
    }

    /** Seconds to seek to for a scrub [fraction] of the bar (clamped to the video length). */
    fun seekSeconds(fraction: Float, durationSeconds: Float): Float {
        if (durationSeconds <= 0f) return 0f
        return (fraction.coerceIn(0f, 1f) * durationSeconds)
    }

    /** "1:28 / 3:49" — current over total, both as YouTube-style clocks. */
    fun label(currentSeconds: Float, durationSeconds: Float): String {
        val cur = currentSeconds.coerceIn(0f, durationSeconds.coerceAtLeast(0f))
        return "${VideoDuration.format(cur.toLong())} / ${VideoDuration.format(durationSeconds.toLong().coerceAtLeast(0L))}"
    }
}
