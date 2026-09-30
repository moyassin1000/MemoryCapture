package com.memorycapture.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.memorycapture.app.R
import kotlin.math.abs

class FloatingRecordingControls(
    private val context: Context,
    private val onPauseResume: () -> Unit,
    private val onHighlight: () -> Unit,
    private val onStop: () -> Unit,
) {
    private val windowManager =
        context.getSystemService(WindowManager::class.java)

    private var root: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var actionPanel: LinearLayout? = null
    private var pauseResumeButton: TextView? = null
    private var paused = false

    fun show() {
        if (root != null || !Settings.canDrawOverlays(context)) return

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = roundedBackground(
                color = Color.argb(235, 30, 30, 36),
                radiusDp = 24f,
            )
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        val pauseButton = actionButton(
            text = context.getString(R.string.notification_pause),
        ) {
            onPauseResume()
        }

        val highlightButton = actionButton(
            text = context.getString(R.string.floating_highlight),
            backgroundColor = Color.rgb(126, 90, 190),
        ) {
            onHighlight()
        }

        val stopButton = actionButton(
            text = context.getString(R.string.notification_stop),
            backgroundColor = Color.rgb(190, 42, 55),
        ) {
            onStop()
        }

        panel.addView(pauseButton)
        panel.addView(highlightButton)
        panel.addView(stopButton)

        val bubble = TextView(context).apply {
            text = "●"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBackground(
                color = Color.rgb(210, 40, 58),
                radiusDp = 32f,
            )
        }

        val bubbleSize = dp(58)
        bubble.layoutParams = LinearLayout.LayoutParams(bubbleSize, bubbleSize)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bubble)
            addView(
                panel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = dp(8)
                },
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(220)
        }

        attachDragBehavior(bubble, container, params) {
            panel.visibility =
                if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        runCatching {
            windowManager.addView(container, params)
        }.onSuccess {
            root = container
            layoutParams = params
            actionPanel = panel
            pauseResumeButton = pauseButton
            updatePaused(paused)
        }
    }

    fun updatePaused(isPaused: Boolean) {
        paused = isPaused
        pauseResumeButton?.text = context.getString(
            if (isPaused) R.string.notification_resume
            else R.string.notification_pause,
        )
    }

    fun hide() {
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        root = null
        layoutParams = null
        actionPanel = null
        pauseResumeButton = null
    }

    private fun actionButton(
        text: String,
        backgroundColor: Int = Color.argb(255, 65, 65, 76),
        onClick: () -> Unit,
    ) = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = roundedBackground(backgroundColor, 18f)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        }
    }

    private fun attachDragBehavior(
        handle: View,
        container: View,
        params: WindowManager.LayoutParams,
        onTap: () -> Unit,
    ) {
        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downRawX).toInt()
                    params.y = startY + (event.rawY - downRawY).toInt()
                    runCatching { windowManager.updateViewLayout(container, params) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val moved =
                        abs(event.rawX - downRawX) > dp(8) ||
                            abs(event.rawY - downRawY) > dp(8)
                    if (!moved) onTap()
                    true
                }

                else -> false
            }
        }
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Float,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
