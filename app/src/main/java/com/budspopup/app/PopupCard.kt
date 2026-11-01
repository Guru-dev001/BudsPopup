package com.budspopup.app

import android.animation.ValueAnimator
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Animated bottom "Fast Pair style" card drawn over other apps. */
object PopupCard {
    private const val SHOW_MS = 6500L

    private val handler = Handler(Looper.getMainLooper())
    private var current: View? = null
    private var autoClose: Runnable? = null
    private var pulse: ValueAnimator? = null

    private class Parts(
        val root: FrameLayout,
        val card: LinearLayout,
        val iconBox: View,
        val ring: View,
        val textBox: View,
        val batteryFill: View?,
        val countdownFill: View
    )

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
            val parts = build(app, name, battery) { dismiss(app) }
            val lp = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }

            try {
                wm.addView(parts.root, lp)
            } catch (e: Exception) {
                Toast.makeText(app, "$name connected", Toast.LENGTH_LONG).show()
                return@post
            }
            current = parts.root
            animateIn(app, parts, battery)

            val r = Runnable { dismiss(app) }
            autoClose = r
            handler.postDelayed(r, SHOW_MS)
        }
    }

    private fun animateIn(app: Context, p: Parts, battery: Int) {
        // Card springs up from the bottom
        p.root.alpha = 0f
        p.root.translationY = dp(app, 320).toFloat()
        p.root.animate()
            .translationY(0f).alpha(1f)
            .setDuration(480)
            .setInterpolator(OvershootInterpolator(1.0f))
            .start()

        // Earbuds image pops in
        p.iconBox.scaleX = 0.4f
        p.iconBox.scaleY = 0.4f
        p.iconBox.animate()
            .scaleX(1f).scaleY(1f)
            .setStartDelay(200).setDuration(500)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()

        // Text slides and fades in
        p.textBox.alpha = 0f
        p.textBox.translationX = -dp(app, 14).toFloat()
        p.textBox.animate()
            .alpha(1f).translationX(0f)
            .setStartDelay(300).setDuration(350)
            .start()

        // Pulsing ring around the image
        pulse?.cancel()
        pulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400
            startDelay = 500
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val f = it.animatedValue as Float
                p.ring.scaleX = 1f + 0.45f * f
                p.ring.scaleY = 1f + 0.45f * f
                p.ring.alpha = 0.6f * (1f - f)
            }
            start()
        }

        // Battery bar fills up
        p.batteryFill?.let { f ->
            f.pivotX = 0f
            f.scaleX = 0f
            f.animate()
                .scaleX(battery / 100f)
                .setStartDelay(550).setDuration(800)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // Countdown bar shrinks until the card closes
        p.countdownFill.pivotX = 0f
        p.countdownFill.scaleX = 1f
        p.countdownFill.animate()
            .scaleX(0f)
            .setStartDelay(480).setDuration(SHOW_MS - 500)
            .setInterpolator(LinearInterpolator())
            .start()
    }

    private fun dismiss(app: Context) {
        val v = current ?: return
        current = null
        autoClose?.let { handler.removeCallbacks(it) }
        autoClose = null
        pulse?.cancel()
        pulse = null
        v.animate()
            .translationY(dp(app, 320).toFloat()).alpha(0f)
            .setStartDelay(0).setDuration(250)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { removeView(app, v) }
            .start()
    }

    private fun removeNow(app: Context) {
        autoClose?.let { handler.removeCallbacks(it) }
        autoClose = null
        pulse?.cancel()
        pulse = null
        current?.let {
            it.animate().cancel()
            removeView(app, it)
        }
        current = null
    }

    private fun removeView(app: Context, v: View) {
        try {
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun bar(app: Context, radiusDp: Int, track: String, fill: String): Pair<FrameLayout, View> {
        val trackView = FrameLayout(app).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor(track))
                cornerRadius = dp(app, radiusDp).toFloat()
            }
        }
        val fillView = View(app).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor(fill))
                cornerRadius = dp(app, radiusDp).toFloat()
            }
        }
        trackView.addView(
            fillView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        return Pair(trackView, fillView)
    }

    private fun build(app: Context, name: String, battery: Int, onDone: () -> Unit): Parts {
        val root = FrameLayout(app).apply {
            setPadding(dp(app, 12), dp(app, 28), dp(app, 12), dp(app, 24))
            clipChildren = false
            clipToPadding = false
        }

        val card = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(app, 18), dp(app, 16), dp(app, 18), dp(app, 14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#202124"))
                cornerRadius = dp(app, 28).toFloat()
            }
            elevation = dp(app, 8).toFloat()
            clipChildren = false
            clipToPadding = false
        }

        val row = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
        }

        // Image with pulsing ring
        val iconWrap = FrameLayout(app).apply { clipChildren = false }
        val ring = View(app).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(app, 2), Color.parseColor("#8AB4F8"))
            }
            alpha = 0f
        }
        iconWrap.addView(
            ring,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val iconBox = FrameLayout(app).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#3C4043"))
            }
        }
        val image = ImageView(app).apply {
            setImageResource(R.drawable.ic_buds_image)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconBox.addView(image, FrameLayout.LayoutParams(dp(app, 40), dp(app, 40), Gravity.CENTER))
        iconWrap.addView(
            iconBox,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        row.addView(iconWrap, LinearLayout.LayoutParams(dp(app, 60), dp(app, 60)))

        // Text
        val texts = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(app).apply {
            text = name
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        })
        texts.addView(TextView(app).apply {
            text = if (battery in 0..100) "Connected · Battery $battery%" else "Connected"
            setTextColor(Color.parseColor("#B0B4B9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })
        row.addView(
            texts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(app, 14)
                marginEnd = dp(app, 8)
            }
        )

        // Done button
        row.addView(
            TextView(app).apply {
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
                setOnClickListener { onDone() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(row)

        // Battery bar (only when the phone reports a level)
        var batteryFill: View? = null
        if (battery in 0..100) {
            val fillColor = when {
                battery >= 50 -> "#81C995"
                battery >= 20 -> "#FDD663"
                else -> "#F28B82"
            }
            val (track, fill) = bar(app, 2, "#3C4043", fillColor)
            batteryFill = fill
            card.addView(
                track,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(app, 4)).apply {
                    topMargin = dp(app, 12)
                }
            )
        }

        // Countdown bar
        val (cTrack, cFill) = bar(app, 2, "#2B2D30", "#5F6368")
        card.addView(
            cTrack,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(app, 3)).apply {
                topMargin = dp(app, 12)
            }
        )

        // Swipe down to dismiss
        var startY = 0f
        card.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    root.translationY = (e.rawY - startY).coerceAtLeast(0f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (e.rawY - startY > dp(app, 60)) {
                        onDone()
                    } else {
                        root.animate().translationY(0f).setDuration(200).start()
                    }
                    true
                }
                else -> false
            }
        }

        root.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return Parts(root, card, iconBox, ring, texts, batteryFill, cFill)
    }
}
