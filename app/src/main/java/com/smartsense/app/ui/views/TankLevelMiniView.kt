package com.smartsense.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.smartsense.app.R
import com.smartsense.app.domain.model.LevelStatus

/**
 * Compact tank icon for list items. Same design as TankLevelView but without the percentage badge.
 */
class TankLevelMiniView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var percentage: Float = 0f
    private var levelStatus: LevelStatus = LevelStatus.RED

    // Tank fills the view more aggressively for the mini size
    private val tankLeftRatio = 0.15f
    private val tankRightRatio = 0.85f
    private val tankTopRatio = 0.15f
    private val tankBottomRatio = 0.85f

    private val tankBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF5A5A5A.toInt()
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fillHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val valveBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF6D6D6D.toInt()
    }

    private val valveCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF4A4A4A.toInt()
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF555555.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFBBBBBB.toInt()
    }

    private val rimStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF888888.toInt()
    }

    private val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF777777.toInt()
    }

    private val weldLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x30000000
    }

    private val tankPath = Path()
    private var lastWidth = 0f

    fun setLevel(percentage: Float, status: LevelStatus) {
        this.percentage = percentage.coerceIn(0f, 100f)
        this.levelStatus = status

        fillPaint.color = when (status) {
            LevelStatus.GREEN -> ContextCompat.getColor(context, R.color.level_green)
            LevelStatus.YELLOW -> ContextCompat.getColor(context, R.color.level_yellow)
            LevelStatus.RED -> 0xFFD24520.toInt()
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val scale = w / 56f // scaled relative to typical 56dp size

        val tankLeft = w * tankLeftRatio
        val tankRight = w * tankRightRatio
        val tankTop = h * tankTopRatio
        val tankBottom = h * tankBottomRatio
        val tankWidth = tankRight - tankLeft
        val tankHeight = tankBottom - tankTop
        val cr = 4f * scale

        // Scale stroke widths
        outlinePaint.strokeWidth = 1.5f * scale
        handlePaint.strokeWidth = 2.5f * scale
        rimStrokePaint.strokeWidth = 0.8f * scale
        weldLinePaint.strokeWidth = 0.8f * scale

        // Tank body gradient
        if (w != lastWidth) {
            lastWidth = w
            tankBodyPaint.shader = LinearGradient(
                tankLeft, 0f, tankRight, 0f,
                intArrayOf(0xFFE8E8E8.toInt(), 0xFFF5F5F5.toInt(), 0xFFE0E0E0.toInt()),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        val cx = (tankLeft + tankRight) / 2f

        // --- Valve assembly ---
        val stemWidth = tankWidth * 0.14f
        val stemTop = tankTop - h * 0.05f
        val stemBottom = tankTop + cr * 0.5f
        canvas.drawRoundRect(
            cx - stemWidth / 2, stemTop, cx + stemWidth / 2, stemBottom,
            1.5f * scale, 1.5f * scale, valveBodyPaint
        )

        // Valve cap
        val capWidth = tankWidth * 0.24f
        val capHeight = h * 0.03f
        val capTop = stemTop - capHeight * 0.3f
        canvas.drawRoundRect(
            cx - capWidth / 2, capTop, cx + capWidth / 2, capTop + capHeight,
            capHeight / 2, capHeight / 2, valveCapPaint
        )

        // Handle arc
        val handleRadius = tankWidth * 0.15f
        val handleCy = capTop - 1f * scale
        val handleRect = RectF(
            cx - handleRadius, handleCy - handleRadius,
            cx + handleRadius, handleCy + handleRadius
        )
        canvas.drawArc(handleRect, 200f, 140f, false, handlePaint)

        // --- Tank body ---
        tankPath.reset()
        tankPath.addRoundRect(
            RectF(tankLeft, tankTop, tankRight, tankBottom),
            cr, cr, Path.Direction.CW
        )

        // Tank body fill
        canvas.drawPath(tankPath, tankBodyPaint)

        // --- Liquid fill ---
        if (percentage > 0f) {
            val fillTop = tankBottom - (tankHeight * percentage / 100f)
            canvas.save()
            canvas.clipPath(tankPath)
            canvas.drawRect(tankLeft, fillTop, tankRight, tankBottom, fillPaint)

            // Subtle highlight
            fillHighlightPaint.shader = LinearGradient(
                tankLeft, 0f, tankLeft + tankWidth * 0.4f, 0f,
                0x30FFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(tankLeft, fillTop, tankLeft + tankWidth * 0.4f, tankBottom, fillHighlightPaint)
            canvas.restore()
        }

        // --- Weld lines ---
        val seamY1 = tankTop + tankHeight * 0.15f
        val seamY2 = tankBottom - tankHeight * 0.15f
        canvas.drawLine(tankLeft + cr * 0.5f, seamY1, tankRight - cr * 0.5f, seamY1, weldLinePaint)
        canvas.drawLine(tankLeft + cr * 0.5f, seamY2, tankRight - cr * 0.5f, seamY2, weldLinePaint)

        // --- Rims ---
        val rimHeight = 2.5f * scale
        // Top rim
        canvas.drawRoundRect(
            RectF(tankLeft - 0.5f * scale, tankTop - rimHeight * 0.3f,
                tankRight + 0.5f * scale, tankTop + rimHeight * 0.7f),
            rimHeight / 2, rimHeight / 2, rimPaint
        )
        canvas.drawRoundRect(
            RectF(tankLeft - 0.5f * scale, tankTop - rimHeight * 0.3f,
                tankRight + 0.5f * scale, tankTop + rimHeight * 0.7f),
            rimHeight / 2, rimHeight / 2, rimStrokePaint
        )
        // Bottom rim
        canvas.drawRoundRect(
            RectF(tankLeft - 0.5f * scale, tankBottom - rimHeight * 0.7f,
                tankRight + 0.5f * scale, tankBottom + rimHeight * 0.3f),
            rimHeight / 2, rimHeight / 2, rimPaint
        )
        canvas.drawRoundRect(
            RectF(tankLeft - 0.5f * scale, tankBottom - rimHeight * 0.7f,
                tankRight + 0.5f * scale, tankBottom + rimHeight * 0.3f),
            rimHeight / 2, rimHeight / 2, rimStrokePaint
        )

        // Tank outline
        canvas.drawPath(tankPath, outlinePaint)

        // --- Feet ---
        val footHeight = h * 0.025f
        val footWidth = tankWidth * 0.2f
        val footY = tankBottom + rimHeight * 0.3f
        canvas.drawRoundRect(
            RectF(tankLeft + tankWidth * 0.1f, footY,
                tankLeft + tankWidth * 0.1f + footWidth, footY + footHeight),
            1.5f * scale, 1.5f * scale, footPaint
        )
        canvas.drawRoundRect(
            RectF(tankRight - tankWidth * 0.1f - footWidth, footY,
                tankRight - tankWidth * 0.1f, footY + footHeight),
            1.5f * scale, 1.5f * scale, footPaint
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = minOf(w, h)
        setMeasuredDimension(size, size)
    }
}
