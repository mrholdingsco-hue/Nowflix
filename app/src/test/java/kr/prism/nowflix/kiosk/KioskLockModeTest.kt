package kr.prism.nowflix.kiosk

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A fake [KioskController] so the mode branch (and, later, wiring) is exercised with no Android
 * dependency — DevicePolicyManager is behind the interface exactly for this.
 */
class FakeKioskController(
    var deviceOwner: Boolean = false,
    var lockTaskActive: Boolean = false,
) : KioskController {
    var registered = false
    var statusBarArg = false
    var keyguardArg = false
    var startCount = 0
    var stopCount = 0
    var released = false

    override fun isDeviceOwner() = deviceOwner
    override fun registerLockTaskPackages() { registered = true }
    override fun setStatusBarDisabled(disabled: Boolean) { statusBarArg = disabled }
    override fun setKeyguardDisabled(disabled: Boolean) { keyguardArg = disabled }
    override fun startLockTask() { startCount++; lockTaskActive = true }
    override fun stopLockTask() { stopCount++; lockTaskActive = false }
    override fun isLockTaskActive() = lockTaskActive
    override fun releaseFully() { released = true; lockTaskActive = false }
}

class KioskLockModeTest {

    @Test
    fun deviceOwnerSelectsFullLock() {
        val fake = FakeKioskController(deviceOwner = true)
        assertEquals(KioskLockMode.FULL_LOCK, lockModeFor(fake.isDeviceOwner()))
    }

    @Test
    fun nonOwnerSelectsFallback() {
        val fake = FakeKioskController(deviceOwner = false)
        assertEquals(KioskLockMode.FALLBACK, lockModeFor(fake.isDeviceOwner()))
    }
}
