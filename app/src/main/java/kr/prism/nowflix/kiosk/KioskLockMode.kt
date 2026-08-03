package kr.prism.nowflix.kiosk

/**
 * Which lock-down strength the kiosk runs at. One code path, decided at runtime by device
 * owner status:
 *  - [FULL_LOCK]  device owner  -> silent lock task + status bar / keyguard disabled.
 *  - [FALLBACK]   not owner     -> screen pinning (system confirms, escapable) + home launcher.
 */
enum class KioskLockMode { FULL_LOCK, FALLBACK }

/** Pure mode selection so the branch is unit-testable with a fake [KioskController]. */
fun lockModeFor(isDeviceOwner: Boolean): KioskLockMode =
    if (isDeviceOwner) KioskLockMode.FULL_LOCK else KioskLockMode.FALLBACK
