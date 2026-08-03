package kr.prism.nowflix

import kr.prism.nowflix.data.Video

/** An active playback session: the list to play through and where it started. */
data class Playback(val videos: List<Video>, val startIndex: Int)

/**
 * The kiosk's whole navigation + playback state as one value, hoisted out of Compose so the
 * idle-return reset is a single pure transform (JVM-testable). Screen selection:
 *  - [playback] non-null   -> the player is up
 *  - else [selectedPart]   -> that part's video list
 *  - else                  -> home
 *
 * [returnToHome] is exactly what the idle timer fires: stop playback and empty the queue, drop
 * the selected part, and rewind the home scroll to the top. Per-screen state (list scroll,
 * player controls) resets for free because the detail/player leave composition.
 */
data class KioskNavState(
    val selectedPart: Part? = null,
    val playback: Playback? = null,
    val homeScrollIndex: Int = 0,
) {
    val isPlaying: Boolean get() = playback != null

    fun openPart(part: Part) = copy(selectedPart = part, playback = null)

    fun play(videos: List<Video>, startIndex: Int) = copy(playback = Playback(videos, startIndex))

    fun closePlayer() = copy(playback = null)

    fun closePart() = copy(selectedPart = null, playback = null)

    /** Idle return: a pristine home — nothing selected, nothing playing, scrolled to the top. */
    fun returnToHome() = KioskNavState()
}
