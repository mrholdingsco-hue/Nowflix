package kr.prism.nowflix.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Real [KioskController] over standard AOSP APIs only (no Samsung Knox). Wraps the app's
 * [Activity] (for lock task), [DevicePolicyManager] + the [KioskAdminReceiver] component (for
 * device-owner policies), and [ActivityManager] (for the live lock-task state).
 *
 * Every device-owner-only call is guarded by [isDeviceOwner], so this same object drives both
 * modes: in FALLBACK it simply skips the owner policies and relies on plain screen pinning.
 */
class AndroidKioskController(private val activity: Activity) : KioskController {

    private val dpm =
        activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val activityManager =
        activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val admin = ComponentName(activity, KioskAdminReceiver::class.java)
    private val pkg = activity.packageName

    override fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(pkg)

    /**
     * Device-owner setup: whitelist this package for silent lock task, and pin ourselves as the
     * persistent default HOME so a stray home press / reboot always lands back here. Cleared in
     * [releaseFully]. No-op when not device owner (the manifest CATEGORY_HOME filter still lets
     * an operator pick us as launcher manually in FALLBACK mode).
     */
    override fun registerLockTaskPackages() {
        if (!isDeviceOwner()) return
        dpm.setLockTaskPackages(admin, arrayOf(pkg))
        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(
            admin,
            homeFilter,
            ComponentName(activity, activity.javaClass),
        )
    }

    override fun setStatusBarDisabled(disabled: Boolean) {
        if (!isDeviceOwner()) return
        dpm.setStatusBarDisabled(admin, disabled)
    }

    override fun setKeyguardDisabled(disabled: Boolean) {
        if (!isDeviceOwner()) return
        dpm.setKeyguardDisabled(admin, disabled)
    }

    override fun startLockTask() {
        if (isLockTaskActive()) return
        try {
            activity.startLockTask()
        } catch (e: Exception) {
            // In FALLBACK the user can decline the system pin prompt; never crash on it.
            Log.w(TAG, "startLockTask failed", e)
        }
    }

    override fun stopLockTask() {
        try {
            activity.stopLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "stopLockTask failed", e)
        }
    }

    override fun isLockTaskActive(): Boolean =
        activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    override fun releaseFully() {
        val owner = isDeviceOwner()
        Log.i(TAG, "releaseFully BEGIN: deviceOwner=$owner lockTaskActive=${isLockTaskActive()}")

        stopLockTask()
        if (owner) {
            dpm.setStatusBarDisabled(admin, false)
            dpm.setKeyguardDisabled(admin, false)
            dpm.clearPackagePersistentPreferredActivities(admin, pkg)
            @Suppress("DEPRECATION") // the standard self-relinquish path for a device owner
            dpm.clearDeviceOwnerApp(pkg)
        }

        Log.i(TAG, "releaseFully END: deviceOwner=${isDeviceOwner()} lockTaskActive=${isLockTaskActive()}")
    }

    private companion object {
        const val TAG = "KioskController"
    }
}
