package com.smartsense.app.ui.views

import android.content.Context
import android.content.res.Configuration
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

    private val tankLeftRatio = 0.25f
    private val tankRightRatio = 0.75f
    private val tankTopRatio = 0.18f
    private val tankBottomRatio = 0.82f

    private val tankBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val valveBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val valveCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rimStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val weldLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val circleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val circleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val tankPath = Path()
    private var lastWidth = 0f
    private var lastDarkMode: Boolean? = null

    private fun isDarkMode(): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyThemeColors() {
        val dark = isDarkMode()
        if (dark) {
            outlinePaint.color = 0xFFAAAAAA.toInt()
            valveBodyPaint.color = 0xFF888888.toInt()
            valveCapPaint.color = 0xFF999999.toInt()
            handlePaint.color = 0xFF999999.toInt()
            rimPaint.color = 0xFF666666.toInt()
            rimStrokePaint.color = 0xFF999999.toInt()
            footPaint.color = 0xFF888888.toInt()
            weldLinePaint.color = 0x40FFFFFF
            circleFillPaint.color = 0xFF1E1E1E.toInt()
            shadowPaint.color = 0x30000000
        } else {
            outlinePaint.color = 0xFF5A5A5A.toInt()
            valveBodyPaint.color = 0xFF6D6D6D.toInt()
            valveCapPaint.color = 0xFF4A4A4A.toInt()
            handlePaint.color = 0xFF555555.toInt()
            rimPaint.color = 0xFFBBBBBB.toInt()
            rimStrokePaint.color = 0xFF888888.toInt()
            footPaint.color = 0xFF777777.toInt()
            weldLinePaint.color = 0x30000000
            circleFillPaint.color = 0xFFFFFFFF.toInt()
            shadowPaint.color = 0x18000000
        }
    }

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
        val dark = isDarkMode()

        // Re-apply theme colors if mode changed or first draw
        if (lastDarkMode != dark) {
            lastDarkMode = dark
            lastWidth = 0f // Force gradient rebuild
            applyThemeColors()
        }

        val tankLeft = w * tankLeftRatio
        val tankRight = w * tankRightRatio
        val tankTop = h * tankTopRatio
        val tankBottom = h * tankBottomRatio
        val tankWidth = tankRight - tankLeft
        val tankHeight = tankBottom - tankTop
        val cr = 20f * scale

        outlinePaint.strokeWidth = 3f * scale
        handlePaint.strokeWidth = 5f * scale
        rimStrokePaint.strokeWidth = 1.5f * scale
        weldLinePaint.strokeWidth = 1.5f * scale
        circleBorderPaint.strokeWidth = 3f * scale

        // Tank body gradient
        if (w != lastWidth) {
            lastWidth = w
            tankBodyPaint.shader = if (dark) {
                LinearGradient(
                    tankLeft, 0f, tankRight, 0f,
                    intArrayOf(0xFF3A3A3A.toInt(), 0xFF4A4A4A.toInt(), 0xFF333333.toInt()),
                    floatArrayOf(0f, 0.4f, 1f),
                    Shader.TileMode.CLAMP
                )
            } else {
                LinearGradient(
                    tankLeft, 0f, tankRight, 0f,
                    intArrayOf(0xFFE8E8E8.toInt(), 0xFFF5F5F5.toInt(), 0xFFE0E0E0.toInt()),
                    floatArrayOf(0f, 0.4f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        }

        val cx = (tankLeft + tankRight) / 2f

        // --- Valve assembly ---
        val stemWidth = tankWidth * 0.12f
        val stemTop = tankTop - h * 0.06f
        val stemBottom = tankTop + cr * 0.5f
        canvas.drawRoundRect(
            cx - stemWidth / 2, stemTop, cx + stemWidth / 2, stemBottom,
            3f * scale, 3f * scale, valveBodyPaint
        )

        val capWidth = tankWidth * 0.22f
        val capHeight = h * 0.035f
        val capTop = stemTop - capHeight * 0.3f
        canvas.drawRoundRect(
            cx - capWidth / 2, capTop, cx + capWidth / 2, capTop + capHeight,
            capHeight / 2, capHeight / 2, valveCapPaint
        )

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

        // Drop shadow
        if (!dark) {
            canvas.drawRoundRect(
                RectF(tankLeft + 3f * scale, tankTop + 3f * scale,
                    tankRight + 3f * scale, tankBottom + 3f * scale),
                cr, cr, shadowPaint
            )
        }

        canvas.drawPath(tankPath, tankBodyPaint)

        // --- Liquid fill ---
        if (percentage > 0f) {
            val fillTop = tankBottom - (tankHeight * percentage / 100f)
            canvas.save()
            canvas.clipPath(tankPath)
            canvas.drawRect(tankLeft, fillTop, tankRight, tankBottom, fillPaint)

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
        val seamYMid = (tankTop + tankBottom) / 2f
        canvas.drawLine(tankLeft + cr * 0.5f, seamY1, tankRight - cr * 0.5f, seamY1, weldLinePaint)
        canvas.drawLine(tankLeft + cr * 0.5f, seamY2, tankRight - cr * 0.5f, seamY2, weldLinePaint)
        canvas.drawLine(tankLeft + cr * 0.5f, seamYMid, tankRight - cr * 0.5f, seamYMid, weldLinePaint)

        // --- Rims ---
        val rimHeight = 6f * scale
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

        canvas.drawPath(tankPath, outlinePaint)

        // --- Feet ---
        val footHeight = h * 0.03f
        val footWidth = tankWidth * 0.18f
        val footY = tankBottom + rimHeight * 0.3f
        canvas.drawRoundRect(
            RectF(tankLeft + tankWidth * 0.12f, footY,
                tankLeft + tankWidth * 0.12f + footWidth, footY + footHeight),
            3f * scale, 3f * scale, footPaint
        )
        canvas.drawRoundRect(
            RectF(tankRight - tankWidth * 0.12f - footWidth, footY,
                tankRight - tankWidth * 0.12f, footY + footHeight),
            3f * scale, 3f * scale, footPaint
        )

        // --- Percentage badge ---
        val badgeRadius = w * 0.11f
        val badgeCx = tankRight + badgeRadius * 0.15f
        val badgeCy = tankTop + tankHeight * 0.35f

        if (!dark) {
            canvas.drawCircle(badgeCx + 1.5f * scale, badgeCy + 1.5f * scale, badgeRadius, shadowPaint)
        }
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, circleFillPaint)
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, circleBorderPaint)

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
