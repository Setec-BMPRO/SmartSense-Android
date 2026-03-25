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

class TankLevelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var percentage: Float = 0f
    private var levelStatus: LevelStatus = LevelStatus.RED
    private var levelText: String = "Empty"

    // Tank proportions (centered, taller and narrower for a realistic propane tank)
    private val tankLeftRatio = 0.25f
    private val tankRightRatio = 0.75f
    private val tankTopRatio = 0.18f
    private val tankBottomRatio = 0.82f

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

    private val circleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    private val circleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x18000000
    }

    private val tankPath = Path()
    private var lastWidth = 0f

    fun setLevel(percentage: Float, status: LevelStatus) {
        this.percentage = percentage.coerceIn(0f, 100f)
        this.levelStatus = status
        this.levelText = if (percentage <= 0f) "Empty" else "${percentage.toInt()}%"

        fillPaint.color = when (status) {
            LevelStatus.GREEN -> ContextCompat.getColor(context, R.color.level_green)
            LevelStatus.YELLOW -> ContextCompat.getColor(context, R.color.level_yellow)
            LevelStatus.RED -> 0xFFD24520.toInt()
        }

        circleBorderPaint.color = when (status) {
            LevelStatus.GREEN -> ContextCompat.getColor(context, R.color.level_green)
            LevelStatus.YELLOW -> ContextCompat.getColor(context, R.color.level_yellow)
            LevelStatus.RED -> ContextCompat.getColor(context, R.color.level_red)
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val scale = w / 360f

        val tankLeft = w * tankLeftRatio
        val tankRight = w * tankRightRatio
        val tankTop = h * tankTopRatio
        val tankBottom = h * tankBottomRatio
        val tankWidth = tankRight - tankLeft
        val tankHeight = tankBottom - tankTop
        val cr = 20f * scale

        // Stroke widths scale with view
        outlinePaint.strokeWidth = 3f * scale
        handlePaint.strokeWidth = 5f * scale
        rimStrokePaint.strokeWidth = 1.5f * scale
        weldLinePaint.strokeWidth = 1.5f * scale
        circleBorderPaint.strokeWidth = 3f * scale

        // Tank body gradient (light gray with a subtle left-to-right highlight)
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
        // Valve stem
        val stemWidth = tankWidth * 0.12f
        val stemTop = tankTop - h * 0.06f
        val stemBottom = tankTop + cr * 0.5f
        canvas.drawRoundRect(
            cx - stemWidth / 2, stemTop, cx + stemWidth / 2, stemBottom,
            3f * scale, 3f * scale, valveBodyPaint
        )

        // Valve cap (dark oval)
        val capWidth = tankWidth * 0.22f
        val capHeight = h * 0.035f
        val capTop = stemTop - capHeight * 0.3f
        canvas.drawRoundRect(
            cx - capWidth / 2, capTop, cx + capWidth / 2, capTop + capHeight,
            capHeight / 2, capHeight / 2, valveCapPaint
        )

        // Handle (curved arc above valve)
        val handleRadius = tankWidth * 0.16f
        val handleCy = capTop - 2f * scale
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

        // Drop shadow behind tank
        canvas.drawRoundRect(
            RectF(tankLeft + 3f * scale, tankTop + 3f * scale,
                tankRight + 3f * scale, tankBottom + 3f * scale),
            cr, cr, shadowPaint
        )

        // Tank body fill
        canvas.drawPath(tankPath, tankBodyPaint)

        // --- Liquid fill ---
        if (percentage > 0f) {
            val fillTop = tankBottom - (tankHeight * percentage / 100f)
            canvas.save()
            canvas.clipPath(tankPath)

            // Main fill
            canvas.drawRect(tankLeft, fillTop, tankRight, tankBottom, fillPaint)

            // Subtle highlight on left side of liquid
            fillHighlightPaint.shader = LinearGradient(
                tankLeft, 0f, tankLeft + tankWidth * 0.4f, 0f,
                0x30FFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(tankLeft, fillTop, tankLeft + tankWidth * 0.4f, tankBottom, fillHighlightPaint)

            canvas.restore()
        }

        // --- Weld / seam lines ---
        val seamY1 = tankTop + tankHeight * 0.15f
        val seamY2 = tankBottom - tankHeight * 0.15f
        canvas.drawLine(tankLeft + cr * 0.5f, seamY1, tankRight - cr * 0.5f, seamY1, weldLinePaint)
        canvas.drawLine(tankLeft + cr * 0.5f, seamY2, tankRight - cr * 0.5f, seamY2, weldLinePaint)

        // Center horizontal weld
        val seamYMid = (tankTop + tankBottom) / 2f
        canvas.drawLine(tankLeft + cr * 0.5f, seamYMid, tankRight - cr * 0.5f, seamYMid, weldLinePaint)

        // --- Top & bottom rims ---
        val rimHeight = 6f * scale
        // Top rim
        canvas.drawRoundRect(
            RectF(tankLeft - 1f * scale, tankTop - rimHeight * 0.3f,
                tankRight + 1f * scale, tankTop + rimHeight * 0.7f),
            rimHeight / 2, rimHeight / 2, rimPaint
        )
        canvas.drawRoundRect(
            RectF(tankLeft - 1f * scale, tankTop - rimHeight * 0.3f,
                tankRight + 1f * scale, tankTop + rimHeight * 0.7f),
            rimHeight / 2, rimHeight / 2, rimStrokePaint
        )
        // Bottom rim
        canvas.drawRoundRect(
            RectF(tankLeft - 1f * scale, tankBottom - rimHeight * 0.7f,
                tankRight + 1f * scale, tankBottom + rimHeight * 0.3f),
            rimHeight / 2, rimHeight / 2, rimPaint
        )
        canvas.drawRoundRect(
            RectF(tankLeft - 1f * scale, tankBottom - rimHeight * 0.7f,
                tankRight + 1f * scale, tankBottom + rimHeight * 0.3f),
            rimHeight / 2, rimHeight / 2, rimStrokePaint
        )

        // Tank outline
        canvas.drawPath(tankPath, outlinePaint)

        // --- Base / feet ---
        val footHeight = h * 0.03f
        val footWidth = tankWidth * 0.18f
        val footY = tankBottom + rimHeight * 0.3f
        // Left foot
        canvas.drawRoundRect(
            RectF(tankLeft + tankWidth * 0.12f, footY,
                tankLeft + tankWidth * 0.12f + footWidth, footY + footHeight),
            3f * scale, 3f * scale, footPaint
        )
        // Right foot
        canvas.drawRoundRect(
            RectF(tankRight - tankWidth * 0.12f - footWidth, footY,
                tankRight - tankWidth * 0.12f, footY + footHeight),
            3f * scale, 3f * scale, footPaint
        )

        // --- Percentage badge ---
        val badgeRadius = w * 0.11f
        val badgeCx = tankRight + badgeRadius * 0.15f
        val badgeCy = tankTop + tankHeight * 0.35f

        // Badge shadow
        canvas.drawCircle(badgeCx + 1.5f * scale, badgeCy + 1.5f * scale, badgeRadius, shadowPaint)
        // Badge fill
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, circleFillPaint)
        // Badge border
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, circleBorderPaint)

        // Badge text
        circleTextPaint.color = circleBorderPaint.color
        circleTextPaint.textSize = badgeRadius * 0.6f
        circleTextPaint.isFakeBoldText = true
        val textY = badgeCy - (circleTextPaint.descent() + circleTextPaint.ascent()) / 2f
        canvas.drawText(levelText, badgeCx, textY, circleTextPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * 0.85f).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }
}
