package com.spikeai.app.blocking

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.spikeai.app.R
import com.spikeai.app.localization.AppLocalization

/**
 * Full-screen blocking overlay drawn with a [WindowManager] (TYPE_APPLICATION_OVERLAY),
 * styled to mirror the iOS Screen Time shield. Each focus mode gets its own colour
 * identity and copy:
 *
 *  - **medium / Focus** → amber "Are you sure?" confirmation (can proceed once).
 *  - **hard / Lock-in**  → red "Blocked for Focus" (no bypass).
 *  - **sweat**           → teal "Exercise Required" (opens the Sweat screen).
 *
 * Built with classic Android views (not Compose) because it lives outside any
 * Activity/composition. The accessibility service owns one instance.
 *
 * @param onClose   user dismissed → go home (caller performs the redirect).
 * @param onBypass  Focus mode "Yes, open app" → let them through this once.
 * @param onMoveToUnlock sweat mode → open the app's Sweat screen.
 */
class BlockOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: View? = null

    val isShowing: Boolean get() = root != null

    fun show(
        mode: String,
        onClose: () -> Unit,
        onBypass: () -> Unit,
        onMoveToUnlock: () -> Unit,
    ) {
        if (root != null) return // already visible — don't stack overlays

        // Use the user's in-app language choice (falls back to device language),
        // so the block screen matches the rest of the app — not just the OS locale.
        val lang = AppLocalization.activeLanguage()
        fun s(key: String) = AppLocalization.string(key, lang)

        // ── Per-mode identity (matches the iOS ShieldConfiguration) ────────────
        val (bgColor, titleText, subtitleText) = when (mode) {
            "hard" -> Triple(
                0xF5E0352B.toInt(),                 // red, ~96% opaque
                s("shield_blocked_title"),
                s("shield_blocked_mode"),
            )
            "sweat" -> Triple(
                0xF51FB8A8.toInt(),                 // teal, ~96% opaque
                s("shield_exercise_title"),
                s("shield_exercise_subtitle"),
            )
            else -> Triple(                          // medium / Focus
                0xF5E0982E.toInt(),                 // amber, ~96% opaque
                s("shield_are_you_sure"),
                s("shield_are_you_sure_subtitle"),
            )
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bgColor)
            val pad = dp(32)
            setPadding(pad, pad, pad, pad)
        }

        // Spike logo (same asset used by the iOS shield).
        val logo = ImageView(context).apply {
            setImageResource(R.drawable.spike_logo)
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(24)
            }
        }

        val title = TextView(context).apply {
            text = titleText
            setTextColor(AndroidColor.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val message = TextView(context).apply {
            text = subtitleText
            setTextColor(AndroidColor.argb(216, 255, 255, 255)) // white @85%
            textSize = 16f
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(12), 0, dp(32))
        }

        container.addView(logo)
        container.addView(title)
        container.addView(message)

        // ── Buttons per mode ───────────────────────────────────────────────────
        when (mode) {
            "hard" -> {
                // No bypass — only a white "Close" that sends the user home.
                container.addView(
                    pillButton(s("shield_close"), bg = AndroidColor.WHITE, fg = 0xFFE0352B.toInt()) {
                        hide(); onClose()
                    }
                )
            }
            "sweat" -> {
                container.addView(
                    pillButton(s("shield_move_to_unlock"), bg = AndroidColor.WHITE, fg = 0xFF159486.toInt()) {
                        hide(); onMoveToUnlock()
                    }
                )
                container.addView(spacer(dp(12)))
                container.addView(
                    pillButton(s("shield_close"), bg = AndroidColor.TRANSPARENT,
                        fg = AndroidColor.WHITE, outline = true) {
                        hide(); onClose()
                    }
                )
            }
            else -> {
                // medium / Focus: confirm "Yes, open app", or back out.
                container.addView(
                    pillButton(s("shield_yes_open"), bg = 0xFFE0352B.toInt(), fg = AndroidColor.WHITE) {
                        hide(); onBypass()
                    }
                )
                container.addView(spacer(dp(12)))
                container.addView(
                    pillButton(s("shield_no_go_back"), bg = AndroidColor.TRANSPARENT,
                        fg = AndroidColor.WHITE, outline = true) {
                        hide(); onClose()
                    }
                )
            }
        }

        val layout = FrameLayout(context).apply { addView(container) }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        runCatching {
            windowManager.addView(layout, params)
            root = layout
        }
    }

    fun hide() {
        val current = root ?: return
        runCatching { windowManager.removeView(current) }
        root = null
    }

    // ── view helpers ─────────────────────────────────────────────────────────

    private fun pillButton(
        label: String,
        bg: Int,
        fg: Int,
        outline: Boolean = false,
        onClick: () -> Unit,
    ): Button = Button(context).apply {
        text = label
        setTextColor(fg)
        textSize = 16f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        val shape = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(bg)
            if (outline) setStroke(dp(1), AndroidColor.argb(128, 255, 255, 255))
        }
        background = shape
        layoutParams = LinearLayout.LayoutParams(dp(280), dp(54)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        setOnClickListener { onClick() }
    }

    private fun spacer(heightPx: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, heightPx)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
