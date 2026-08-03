package kr.prism.nowflix.kiosk

/**
 * The Android lock-down surface the kiosk logic needs, behind an interface so the pure
 * mode-decision and re-entry logic can be exercised with a fake in JVM tests. The real
 * implementation ([AndroidKioskController]) wraps DevicePolicyManager / Activity /
 * ActivityManager using standard AOSP APIs only — no vendor (Samsung Knox) APIs.
 */
interface KioskController {

    /** True when this app was granted device owner via `adb dpm set-device-owner` at install. */
    fun isDeviceOwner(): Boolean

    /** Device-owner only: whitelist this package for lock task so [startLockTask] needs no prompt. */
    fun registerLockTaskPackages()

    /** Device-owner only: hide/show the status bar + notification shade. */
    fun setStatusBarDisabled(disabled: Boolean)

    /** Device-owner only: disable/enable the keyguard (lock screen). */
    fun setKeyguardDisabled(disabled: Boolean)

    /** Enter lock task (screen pinning). Silent when device owner; prompts otherwise. */
    fun startLockTask()

    /** Leave lock task. */
    fun stopLockTask()

    /** True while the activity is pinned (ActivityManager lock task state != NONE). */
    fun isLockTaskActive(): Boolean

    /**
     * Full hospital-handoff release: clear the persistent home-launcher preference and, if
     * device owner, relinquish device ownership. After this the tablet is an ordinary device
     * again (no factory reset). Logs its before/after state.
     */
    fun releaseFully()
}
