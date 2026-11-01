package com.budspopup.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Bottom "Fast Pair style" card drawn over other apps. */
object PopupCard {
    private val handler = Handler(Looper.getMainLooper())
    private var current: View? = null
    private var autoClose: Runnable? = null

    private fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics
        ).toInt()

    fun show(context: Context, name: String, battery: Int) {
        val app = context.applicationContext
        handler.post {
            if (!Settings.canDrawOverlays(app)) {
                Toast.makeText(app, "$name connected", Toast.LENGTH_LONG).show()
                return@post
            }
            removeNow(app)

            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = build(app, name, battery)
            val lp = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }

            try {
                wm.addView(view, lp)
            } catch (e: Exception) {
                Toast.makeText(app, "$name connected", Toast.LENGTH_LONG).show()
                return@post
            }
            current = view

            // Slide up
            view.translationY = dp(app, 300).toFloat()
            view.animate().translationY(0f).setDuration(280).start()

            val r = Runnable { dismiss(app) }
            autoClose = r
            handler.postDelayed(r, 6000)
        }
    }

    private fun dismiss(app: Context) {
        val v = current ?: return
        autoClose?.let { handler.removeCallbacks(it) }
        autoClose = null
        v.animate()
            .translationY(dp(app, 300).toFloat())
            .setDuration(220)
            .withEndAction { removeView(app, v) }
            .start()
        current = null
    }

    private fun removeNow(app: Context) {
        autoClose?.let { handler.removeCallbacks(it) }
        autoClose = null
        current?.let { removeView(app, it) }
        current = null
    }

    private fun removeView(app: Context, v: View) {
        try {
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun build(app: Context, name: String, battery: Int): View {
        val wrapper = FrameLayout(app)
        wrapper.setPadding(dp(app, 12), dp(app, 12), dp(app, 12), dp(app, 24))

        val card = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(app, 18), dp(app, 16), dp(app, 18), dp(app, 16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#202124"))
                cornerRadius = dp(app, 28).toFloat()
            }
            elevation = dp(app, 8).toFloat()
        }

        val icon = TextView(app).apply {
            text = "🎧"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#3C4043"))
            }
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(app, 56), dp(app, 56)))

        val texts = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(app).apply {
            text = name
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        }
        val status = TextView(app).apply {
            text = if (battery in 0..100) "Connected · Battery $battery%" else "Connected"
            setTextColor(Color.parseColor("#B0B4B9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        texts.addView(title)
        texts.addView(status)
        card.addView(
            texts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(app, 14)
                marginEnd = dp(app, 8)
            }
        )

        val done = TextView(app).apply {
            text = "Done"
            setTextColor(Color.parseColor("#202124"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(app, 20), dp(app, 10), dp(app, 20), dp(app, 10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#8AB4F8"))
                cornerRadius = dp(app, 20).toFloat()
            }
            setOnClickListener { dismiss(app) }
        }
        card.addView(
            done,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        wrapper.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return wrapper
    }
}
