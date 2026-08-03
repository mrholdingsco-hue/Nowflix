package kr.prism.nowflix.kiosk

/**
 * Re-entry decision for FALLBACK mode. When the app is not device owner, screen pinning can be
 * escaped (back + recents long-press), so on every `onResume` we re-assert the pin — but only
 * when it makes sense, and never in a tight loop.
 *
 * Pure and clock-injected. [shouldReenter] returns true only when:
 *  - lock task is currently NOT active (we actually escaped), and
 *  - the admin screen is NOT open (the operator is deliberately inside — don't fight them), and
 *  - at least [minIntervalMs] has passed since the last re-entry we authorized.
 *
 * The interval guard is the anti-infinite-loop belt: startLockTask -> onResume fires again ->
 * without the guard we'd re-enter forever. Each authorized re-entry stamps [lastAttempt].
 */
class LockTaskReentry(
    private val clock: () -> Long,
    private val minIntervalMs: Long = 2_000L,
) {
    private var lastAttempt: Long? = null // null = never re-entered yet (avoids sentinel overflow)

    fun shouldReenter(isLockTaskActive: Boolean, adminOpen: Boolean): Boolean {
        if (isLockTaskActive || adminOpen) return false
        val now = clock()
        val last = lastAttempt
        if (last != null && now - last < minIntervalMs) return false
        lastAttempt = now
        return true
    }
}
