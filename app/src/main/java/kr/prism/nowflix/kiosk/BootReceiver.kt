package kr.prism.nowflix.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kr.prism.nowflix.MainActivity

/**
 * Relaunches the kiosk on device boot. Starting a full-screen launcher Activity from
 * BOOT_COMPLETED is permitted across Android versions (unlike background service/activity
 * starts), and when the app is the registered HOME app the system brings it up regardless.
 * FLAG_ACTIVITY_NEW_TASK is required to start an Activity from a receiver context.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i("KioskBoot", "BOOT_COMPLETED -> launching kiosk")
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(launch)
    }
}
