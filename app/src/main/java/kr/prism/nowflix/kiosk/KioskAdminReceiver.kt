package kr.prism.nowflix.kiosk

import android.app.admin.DeviceAdminReceiver

/**
 * Device admin component required to grant device owner via
 * `adb shell dpm set-device-owner kr.prism.nowflix/.kiosk.KioskAdminReceiver`.
 *
 * No policy callbacks are needed here — the receiver exists so DevicePolicyManager has an admin
 * [android.content.ComponentName] to authorize the lock-task / status-bar / keyguard calls.
 */
class KioskAdminReceiver : DeviceAdminReceiver()
