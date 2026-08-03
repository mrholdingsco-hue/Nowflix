package kr.prism.nowflix.player

/**
 * Pure queue math for the player. No Android/YouTube types, so the auto-advance and
 * last-video rules are exercised by JVM unit tests.
 *
 * A playback session is a fixed [size] list of videos and a current position. The player
 * advances on the ENDED state and on the "다음 영상" button; when there is nothing after the
 * current video the caller returns to the list screen instead of loading anything.
 */
object PlaybackQueue {

    /**
     * Index of the video to play after [currentIndex], or `null` when [currentIndex] is the
     * last item (or out of range) — `null` means "no next video, go back to the list".
     */
    fun nextIndex(currentIndex: Int, size: Int): Int? {
        if (size <= 0) return null
        val next = currentIndex + 1
        return if (next in 0 until size) next else null
    }

    /** True when [currentIndex] is the final playable item — the end of the session. */
    fun isLast(currentIndex: Int, size: Int): Boolean = nextIndex(currentIndex, size) == null
}
