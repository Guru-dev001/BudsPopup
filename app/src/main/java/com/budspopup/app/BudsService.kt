package com.budspopup.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Foreground service that listens for Bluetooth connections and shows the popup
 * when a device whose name matches the saved keyword connects.
 */
class BudsService : Service() {

    private var registered = false
    private var lastAddress: String? = null
    private var lastTime = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            if (device == null) return

            val name = try {
                device.name
            } catch (e: SecurityException) {
                null
            } ?: return

            val keyword = Prefs.keyword(context)
            if (keyword.isNotEmpty() && !name.contains(keyword, ignoreCase = true)) return

            // ACL_CONNECTED can fire more than once; ignore repeats within 5 seconds.
            val now = System.currentTimeMillis()
            if (device.address == lastAddress && now - lastTime < 5000) return
            lastAddress = device.address
            lastTime = now

            PopupCard.show(context, name, batteryOf(device))
        }
    }

    /** Uses a hidden Android method; returns -1 when the phone does not expose it. */
    private fun batteryOf(device: BluetoothDevice): Int {
        return try {
            val m = device.javaClass.getMethod("getBatteryLevel")
            (m.invoke(device) as? Int) ?: -1
        } catch (e: Throwable) {
            -1
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (!registered) {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED),
                ContextCompat.RECEIVER_EXPORTED
            )
            registered = true
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Buds Popup", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Buds Popup is running")
            .setContentText("Waiting for your earbuds to connect")
            .setSmallIcon(R.drawable.ic_buds)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        if (registered) {
            try {
                unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            registered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "buds_popup_service"
    }
}
