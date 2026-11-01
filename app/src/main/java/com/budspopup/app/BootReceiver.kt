package com.budspopup.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Restarts the watcher after the phone reboots, if it was enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.enabled(context)) {
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, BudsService::class.java)
                )
            } catch (_: Exception) {
            }
        }
    }
}
