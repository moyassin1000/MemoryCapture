package com.memorycapture.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import com.memorycapture.app.R
import kotlin.math.abs

class FloatingRecordingControls(
    private val context: Context,
    private val onPauseResume: () -> Unit,
    private val onScreenshot: () -> Unit,
    private val onHighlight: () -> Unit,
    private val onSaveReplay: () -> Unit,
    private val onStop: () -> Unit,
) {
    private val windowManager =
        context.getSystemService(WindowManager::class.java)

    private var root: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var actionPanel: LinearLayout? = null
    private var pauseResumeButton: ImageButton? = null

    private var paused = false
    private var bubbleAnchorX = 0
    private var bubbleAnchorY = 0
    private var bubbleOnRight = false

    fun show() {
        if (root != null || !Settings.canDrawOverlays(context)) return

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            elevation = dp(10).toFloat()
            background = roundedBackground(
                color = Color.argb(242, 24, 25, 31),
                radiusDp = 28f,
            )
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val pauseButton = actionButton(
            iconRes = R.drawable.ic_float_pause,
            contentDescription = context.getString(R.string.notification_pause),
            backgroundColor = Color.rgb(79, 86, 110),
        ) {
            onPauseResume()
        }

        val screenshotButton = actionButton(
            iconRes = R.drawable.ic_float_camera,
            contentDescription = context.getString(R.string.floating_screenshot),
            backgroundColor = Color.rgb(55, 105, 175),
        ) {
            onScreenshot()
        }

        val highlightButton = actionButton(
            iconRes = R.drawable.ic_float_star,
            contentDescription = context.getString(R.string.floating_highlight),
            backgroundColor = Color.rgb(126, 90, 190),
        ) {
            onHighlight()
        }

        val replayButton = actionButton(
            iconRes = R.drawable.ic_float_replay,
            contentDescription = context.getString(R.string.floating_save_replay),
            backgroundColor = Color.rgb(34, 135, 110),
        ) {
            onSaveReplay()
        }

        val stopButton = actionButton(
            iconRes = R.drawable.ic_float_stop,
            contentDescription = context.getString(R.string.notification_stop),
            backgroundColor = Color.rgb(196, 48, 62),
        ) {
            onStop()
        }

        panel.addView(pauseButton)
        panel.addView(screenshotButton)
        panel.addView(highlightButton)
        panel.addView(replayButton)
        panel.addView(stopButton)

        val bubbleSize = dp(BUBBLE_SIZE_DP)
        val bubble = ImageButton(context).apply {
            setImageResource(R.drawable.ic_float_screen_record)
            contentDescription = context.getString(R.string.capture_tab)
            tooltipText = context.getString(R.string.capture_tab)
            scaleType = ImageButton.ScaleType.CENTER_INSIDE
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = floatingBubbleBackground()
            elevation = dp(16).toFloat()
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(bubbleSize, bubbleSize)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(bubble)
            addView(
                panel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = dp(PANEL_GAP_DP)
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
            x = dp(EDGE_MARGIN_DP)
            y = dp(220)
        }

        bubbleAnchorX = params.x
        bubbleAnchorY = params.y
        bubbleOnRight = false

        attachDragBehavior(
            handle = bubble,
            container = container,
            panel = panel,
            params = params,
            bubbleSize = bubbleSize,
        )

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
        pauseResumeButton?.apply {
            setImageResource(
                if (isPaused) {
                    R.drawable.ic_float_play
                } else {
                    R.drawable.ic_float_pause
                },
            )
            contentDescription = context.getString(
                if (isPaused) {
                    R.string.notification_resume
                } else {
                    R.string.notification_pause
                },
            )
            tooltipText = contentDescription
        }
    }

    fun hide() {
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        root = null
        layoutParams = null
        actionPanel = null
        pauseResumeButton = null
        bubbleAnchorX = 0
        bubbleAnchorY = 0
        bubbleOnRight = false
    }

    private fun actionButton(
        iconRes: Int,
        contentDescription: String,
        backgroundColor: Int,
        onClick: () -> Unit,
    ) = ImageButton(context).apply {
        setImageResource(iconRes)
        this.contentDescription = contentDescription
        tooltipText = contentDescription
        scaleType = ImageButton.ScaleType.CENTER_INSIDE
        setPadding(dp(11), dp(11), dp(11), dp(11))
        background = circleBackground(backgroundColor)
        elevation = dp(5).toFloat()
        isClickable = true
        isFocusable = true
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        }
        layoutParams = LinearLayout.LayoutParams(
            dp(ACTION_BUTTON_SIZE_DP),
            dp(ACTION_BUTTON_SIZE_DP),
        ).apply {
            topMargin = dp(3)
            bottomMargin = dp(3)
        }
    }

    private fun attachDragBehavior(
        handle: View,
        container: LinearLayout,
        panel: LinearLayout,
        params: WindowManager.LayoutParams,
        bubbleSize: Int,
    ) {
        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (panel.visibility == View.VISIBLE) {
                        collapsePanel(container, panel, params)
                    }

                    startX = bubbleAnchorX
                    startY = bubbleAnchorY
                    downRawX = event.rawX
                    downRawY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val maxX =
                        (screenWidth() - bubbleSize - dp(EDGE_MARGIN_DP))
                            .coerceAtLeast(dp(EDGE_MARGIN_DP))
                    val maxY =
                        (screenHeight() - bubbleSize - dp(EDGE_MARGIN_DP))
                            .coerceAtLeast(dp(EDGE_MARGIN_DP))

                    bubbleAnchorX =
                        (startX + (event.rawX - downRawX).toInt())
                            .coerceIn(dp(EDGE_MARGIN_DP), maxX)
                    bubbleAnchorY =
                        (startY + (event.rawY - downRawY).toInt())
                            .coerceIn(dp(EDGE_MARGIN_DP), maxY)

                    params.x = bubbleAnchorX
                    params.y = bubbleAnchorY
                    runCatching {
                        windowManager.updateViewLayout(container, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val moved =
                        abs(event.rawX - downRawX) > dp(DRAG_THRESHOLD_DP) ||
                            abs(event.rawY - downRawY) > dp(DRAG_THRESHOLD_DP)

                    if (moved) {
                        snapBubbleToNearestEdge(
                            container = container,
                            panel = panel,
                            params = params,
                            bubbleSize = bubbleSize,
                        )
                    } else {
                        handle.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        togglePanel(
                            container = container,
                            panel = panel,
                            params = params,
                            bubbleSize = bubbleSize,
                        )
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    params.x = bubbleAnchorX
                    params.y = bubbleAnchorY
                    runCatching {
                        windowManager.updateViewLayout(container, params)
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun snapBubbleToNearestEdge(
        container: LinearLayout,
        panel: LinearLayout,
        params: WindowManager.LayoutParams,
        bubbleSize: Int,
    ) {
        val margin = dp(EDGE_MARGIN_DP)
        val width = screenWidth()
        val bubbleCenterX = bubbleAnchorX + bubbleSize / 2

        bubbleOnRight = bubbleCenterX >= width / 2
        bubbleAnchorX =
            if (bubbleOnRight) {
                (width - bubbleSize - margin).coerceAtLeast(margin)
            } else {
                margin
            }

        panel.visibility = View.GONE
        placePanelInsideScreen(container, panel)
        params.x = bubbleAnchorX
        params.y = bubbleAnchorY

        runCatching {
            windowManager.updateViewLayout(container, params)
        }
    }

    private fun togglePanel(
        container: LinearLayout,
        panel: LinearLayout,
        params: WindowManager.LayoutParams,
        bubbleSize: Int,
    ) {
        if (panel.visibility == View.VISIBLE) {
            collapsePanel(container, panel, params)
            return
        }

        placePanelInsideScreen(container, panel)
        panel.visibility = View.VISIBLE

        val panelWidth = dp(PANEL_WIDTH_DP)
        val panelHeight = dp(PANEL_HEIGHT_DP)
        val gap = dp(PANEL_GAP_DP)
        val margin = dp(EDGE_MARGIN_DP)

        params.x =
            if (bubbleOnRight) {
                (bubbleAnchorX - panelWidth - gap).coerceAtLeast(margin)
            } else {
                bubbleAnchorX
            }

        val verticalShift = ((panelHeight - bubbleSize) / 2).coerceAtLeast(0)
        val maxExpandedY =
            (screenHeight() - panelHeight - margin).coerceAtLeast(margin)
        params.y =
            (bubbleAnchorY - verticalShift)
                .coerceIn(margin, maxExpandedY)

        runCatching {
            windowManager.updateViewLayout(container, params)
        }
    }

    private fun collapsePanel(
        container: LinearLayout,
        panel: LinearLayout,
        params: WindowManager.LayoutParams,
    ) {
        panel.visibility = View.GONE
        params.x = bubbleAnchorX
        params.y = bubbleAnchorY
        runCatching {
            windowManager.updateViewLayout(container, params)
        }
    }

    private fun placePanelInsideScreen(
        container: LinearLayout,
        panel: LinearLayout,
    ) {
        val bubble = (0 until container.childCount)
            .map { container.getChildAt(it) }
            .firstOrNull { it !== panel }
            ?: return

        container.removeAllViews()

        if (bubbleOnRight) {
            container.addView(
                panel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginEnd = dp(PANEL_GAP_DP)
                },
            )
            container.addView(bubble)
        } else {
            container.addView(bubble)
            container.addView(
                panel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = dp(PANEL_GAP_DP)
                },
            )
        }
    }

    private fun floatingBubbleBackground() =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(82, 92, 235),
                Color.rgb(123, 84, 238),
            ),
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(2), Color.argb(110, 255, 255, 255))
        }

    private fun circleBackground(color: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(1), Color.argb(55, 255, 255, 255))
        }

    private fun roundedBackground(
        color: Int,
        radiusDp: Float,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), Color.argb(45, 255, 255, 255))
    }

    private fun screenWidth(): Int =
        context.resources.displayMetrics.widthPixels

    private fun screenHeight(): Int =
        context.resources.displayMetrics.heightPixels

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val BUBBLE_SIZE_DP = 64
        const val ACTION_BUTTON_SIZE_DP = 46
        const val PANEL_GAP_DP = 8
        const val PANEL_WIDTH_DP = 58
        const val PANEL_HEIGHT_DP = 272
        const val EDGE_MARGIN_DP = 12
        const val DRAG_THRESHOLD_DP = 8
    }
}
