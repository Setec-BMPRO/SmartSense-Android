package com.smartsense.app.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.smartsense.app.R
import com.smartsense.app.domain.model.LevelStatus

class TankGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var strokeWidth: Float
    private var customTrackColor: Int? = null
    private var showPercentage: Boolean

    private var currentPercentage: Float = 0f
    private var animatedSweep: Float = 0f
    private var levelStatus: LevelStatus = LevelStatus.GREEN
    private var animator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()

    companion object {
        private const val START_ANGLE = 135f
        private const val MAX_SWEEP = 270f
        private const val ANIMATION_DURATION = 600L
    }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.TankGaugeView, defStyleAttr, 0)
        strokeWidth = a.getDimension(
            R.styleable.TankGaugeView_gaugeStrokeWidth,
            resources.getDimension(R.dimen.gauge_stroke_width)
        )
        if (a.hasValue(R.styleable.TankGaugeView_gaugeTrackColor)) {
            customTrackColor = a.getColor(R.styleable.TankGaugeView_gaugeTrackColor, 0)
        }
        showPercentage = a.getBoolean(R.styleable.TankGaugeView_gaugeShowPercentage, true)
        a.recycle()

        trackPaint.strokeWidth = strokeWidth
        arcPaint.strokeWidth = strokeWidth
        updateThemeColors()
    }

    private fun resolveThemeColor(attrId: Int, fallback: Int): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(attrId, typedValue, true)) {
            typedValue.data
        } else {
            ContextCompat.getColor(context, fallback)
        }
    }

    private fun updateThemeColors() {
        trackPaint.color = customTrackColor
            ?: resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant, R.color.gauge_track)
        textPaint.color = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface, R.color.on_surface)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateThemeColors()
        invalidate()
    }

    fun setLevel(percentage: Float, status: LevelStatus = LevelStatus.GREEN) {
        val clamped = percentage.coerceIn(0f, 100f)
        levelStatus = status
        currentPercentage = clamped

        arcPaint.color = when (status) {
            LevelStatus.GREEN -> ContextCompat.getColor(context, R.color.level_green)
            LevelStatus.YELLOW -> ContextCompat.getColor(context, R.color.level_yellow)
            LevelStatus.RED -> ContextCompat.getColor(context, R.color.level_red)
        }

        val targetSweep = clamped / 100f * MAX_SWEEP
        animator?.cancel()
        animator = ValueAnimator.ofFloat(animatedSweep, targetSweep).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                animatedSweep = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = minOf(width, height)
        setMeasuredDimension(
            resolveSize(size, widthMeasureSpec),
            resolveSize(size, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = strokeWidth / 2f + 4f
        val size = minOf(width, height).toFloat()
        arcRect.set(padding, padding, size - padding, size - padding)

        // Draw track
        canvas.drawArc(arcRect, START_ANGLE, MAX_SWEEP, false, trackPaint)

        // Draw level arc
        if (animatedSweep > 0f) {
            canvas.drawArc(arcRect, START_ANGLE, animatedSweep, false, arcPaint)
        }

        // Draw percentage text
        if (showPercentage) {
            textPaint.textSize = size * 0.22f
            textPaint.isFakeBoldText = true
            val text = "${currentPercentage.toInt()}%"
            val x = size / 2f
            val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, x, y, textPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
