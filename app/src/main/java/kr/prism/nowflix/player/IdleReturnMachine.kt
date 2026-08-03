package kr.prism.nowflix.player

/**
 * Pure idle-return state machine — the single authority for "bring the unattended kiosk
 * home without interrupting anyone who is watching". No Android/Compose types; [clock]
 * (milliseconds, monotonic) is injected so tests can advance time by hand.
 *
 * Two regimes, switched by [setPlaying]:
 *  - Browsing (home / list): return [idleReturnSeconds] after the last touch — see [isIdleExpired].
 *  - Playing: a viewer sits still and produces no touches, so elapsed time cannot mean "gone".
 *    Instead, [AUTO_ADVANCE_LIMIT] auto-advances in a row with no touch between them means the
 *    seat is empty — [onAutoAdvance] returns true. Any touch, or a manual next, clears the run.
 */
class IdleReturnMachine(
    private val clock: () -> Long,
    idleReturnSeconds: Int,
) {
    /** Live-updatable so a remote settings change takes effect without rebuilding the machine. */
    var idleReturnSeconds: Int = idleReturnSeconds

    private var playing = false
    private var lastTouchMs = clock()
    private var autoAdvances = 0

    /** Any touch anywhere on screen: restart the idle countdown and clear the auto-advance run. */
    fun onTouch() {
        lastTouchMs = clock()
        autoAdvances = 0
    }

    /** Enter/leave playback. Either way the auto-advance run and the idle clock start fresh. */
    fun setPlaying(value: Boolean) {
        playing = value
        lastTouchMs = clock()
        autoAdvances = 0
    }

    /**
     * A video ended and the next one auto-started with no user input. Returns true when this is
     * the [AUTO_ADVANCE_LIMIT]-th in a row — the caller should stop and return home.
     */
    fun onAutoAdvance(): Boolean {
        autoAdvances++
        return autoAdvances >= AUTO_ADVANCE_LIMIT
    }

    /** The user pressed "다음 영상" / picked a row: deliberate, so it never counts as unattended. */
    fun onManualNext() {
        lastTouchMs = clock()
        autoAdvances = 0
    }

    /** Landed back on home: a clean slate for the next visitor. */
    fun onReturnHome() {
        playing = false
        lastTouchMs = clock()
        autoAdvances = 0
    }

    /** Rule A: true once the idle timeout has elapsed while browsing. Never fires during playback. */
    fun isIdleExpired(): Boolean {
        if (playing) return false
        return clock() - lastTouchMs >= idleReturnSeconds * 1000L
    }

    companion object {
        /** Consecutive touch-free auto-advances that mean "the viewer has left". */
        const val AUTO_ADVANCE_LIMIT = 2
    }
}
