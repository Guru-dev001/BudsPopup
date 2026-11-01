package com.budspopup.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var input: EditText

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
        ).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(24))
            setBackgroundColor(Color.parseColor("#F8F9FA"))
        }

        root.addView(TextView(this).apply {
            text = "Buds Popup"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.parseColor("#202124"))
        })
        root.addView(TextView(this).apply {
            text = "Shows a card at the bottom of your screen every time your earbuds connect."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#5F6368"))
            setPadding(0, dp(6), 0, dp(20))
        })

        root.addView(TextView(this).apply {
            text = "Device name contains (leave empty for any device)"
            setTextColor(Color.parseColor("#202124"))
        })
        input = EditText(this).apply {
            setText(Prefs.keyword(this@MainActivity))
            setSingleLine()
        }
        root.addView(input)

        root.addView(button("Save name") {
            Prefs.setKeyword(this, input.text.toString())
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        })
        root.addView(button("Grant permissions and start") { requestAndStart() })
        root.addView(button("Allow display over other apps") { openOverlaySettings() })
        root.addView(button("Test popup") {
            val name = input.text.toString().ifBlank { "Nord Buds 3r" }
            PopupCard.show(this, name, 82)
        })
        root.addView(button("Stop") {
            Prefs.setEnabled(this, false)
            stopService(Intent(this, BudsService::class.java))
            updateStatus()
        })

        status = TextView(this).apply {
            setTextColor(Color.parseColor("#5F6368"))
            setPadding(0, dp(20), 0, 0)
        }
        root.addView(status)

        root.addView(TextView(this).apply {
            text = "Tip: set this app's battery to Unrestricted and allow auto-start, " +
                "otherwise your phone may stop the background service."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#80868B"))
            setPadding(0, dp(12), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        })
    }

    override fun onResume() {
        super.onResume()
        // If everything is granted and the user wanted it on, make sure the service is running.
        if (Prefs.enabled(this) && missingPermissions().isEmpty()) startWatcher()
        updateStatus()
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    private fun missingPermissions(): List<String> {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        return needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAndStart() {
        Prefs.setKeyword(this, input.text.toString())
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 1)
        } else {
            proceed()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) proceed()
    }

    private fun proceed() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this, "Turn on \"Display over other apps\" for this app", Toast.LENGTH_LONG
            ).show()
            openOverlaySettings()
        }
        if (missingPermissions().none { it == Manifest.permission.BLUETOOTH_CONNECT }) {
            Prefs.setEnabled(this, true)
            startWatcher()
        }
        updateStatus()
    }

    private fun startWatcher() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, BudsService::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun updateStatus() {
        val overlay = if (Settings.canDrawOverlays(this)) "on" else "OFF"
        val running = if (Prefs.enabled(this)) "running" else "stopped"
        status.text = "Watcher: $running\nDisplay over other apps: $overlay"
    }
}
