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
import com.smartsense.app.domain.model.TankLevelUnit
import timber.log.Timber
import kotlin.math.ceil

class TankLevelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var percentage: Float = 0f
    private var levelStatus: LevelStatus = LevelStatus.RED
    private var levelText: String = "Empty"

    private val tankLeftRatio = 0.28f
    private val tankRightRatio = 0.72f
    private val tankTopRatio = 0.22f
    private val tankBottomRatio = 0.84f

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
     var levelUnit: TankLevelUnit= TankLevelUnit.default()
    var tankHeightMm: Float=0F
    fun setLevelUnit(levelUnit: TankLevelUnit,tankHeightMm: Float){
        this.levelUnit=levelUnit
        this.tankHeightMm=tankHeightMm
    }
    fun setLevel(percentage: Float, status: LevelStatus) {
        this.percentage = percentage.coerceIn(0f, 100f)
        this.levelStatus = status
        this.levelText = if (percentage <= 0f) {
            "Empty".also { Timber.d("Level: Empty") }
        } else {
            val result = when (levelUnit) {
                TankLevelUnit.INCHES -> {
                    // (mm to inches) * (percentage / 100)
                    val currentInches = (tankHeightMm * 0.0393701f) * (percentage / 100f)
                    "${currentInches.toInt()} ${levelUnit.shortName}"
                }

                TankLevelUnit.PERCENT -> {
                    "${percentage.toInt()} ${levelUnit.shortName}"
                }

                else -> {
                    // Default to CM: (mm to cm) * (percentage / 100)
                    val currentCm = (tankHeightMm / 10f) * (percentage / 100f)
                    "${currentCm.toInt()} ${levelUnit.shortName}"
                }
            }

            Timber.i("Level Calculation -> Height: ${tankHeightMm}mm, Percent: $percentage%, Result: $result")
            result
        }

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
        val cx = (tankLeft + tankRight) / 2f
        val cornerRadius = tankWidth * 0.35f

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

        // --- Handle / Collar (based on reference image) ---
        val collarWidth = tankWidth * 0.65f
        val collarTop = tankTop - tankHeight * 0.18f
        val collarBottom = tankTop + cornerRadius * 0.2f
        val postWidth = 10f * scale
        val topBarHeight = 8f * scale

        // Collar vertical posts
        canvas.drawRect(cx - collarWidth * 0.35f, collarTop, cx - collarWidth * 0.35f + postWidth, collarBottom, handlePaint.apply { style = Paint.Style.FILL })
        canvas.drawRect(cx + collarWidth * 0.35f - postWidth, collarTop, cx + collarWidth * 0.35f, collarBottom, handlePaint)
        
        // Collar top horizontal bar
        canvas.drawRoundRect(cx - collarWidth / 2, collarTop, cx + collarWidth / 2, collarTop + topBarHeight, 4f * scale, 4f * scale, handlePaint)

        // --- Valve (based on reference image) ---
        val valveWidth = tankWidth * 0.2f
        val valveTop = collarTop + topBarHeight + 10f * scale
        val valveBottom = tankTop + cornerRadius * 0.1f
        // Stem
        canvas.drawRect(cx - 4f * scale, valveTop, cx + 4f * scale, valveBottom, valveBodyPaint)
        // Valve Cap (T-shape)
        canvas.drawRect(cx - valveWidth / 2, valveTop, cx + valveWidth / 2, valveTop + 7f * scale, valveCapPaint)

        // --- Tank Body ---
        tankPath.reset()
        tankPath.addRoundRect(
            RectF(tankLeft, tankTop, tankRight, tankBottom),
            cornerRadius, cornerRadius, Path.Direction.CW
        )

        // Drop shadow
        if (!dark) {
            canvas.drawRoundRect(
                RectF(tankLeft + 3f * scale, tankTop + 3f * scale,
                    tankRight + 3f * scale, tankBottom + 3f * scale),
                cornerRadius, cornerRadius, shadowPaint
            )
        }

        canvas.drawPath(tankPath, tankBodyPaint)

        // --- Liquid Fill ---
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

        // --- Horizontal Bands (based on reference image) ---
        // These bands appear as gaps/lines in the silhouette
        val bandThickness = 10f * scale
        val band1Y = tankTop + tankHeight * 0.32f
        val band2Y = tankTop + tankHeight * 0.68f
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        }
        canvas.drawRect(tankLeft, band1Y, tankRight, band1Y + bandThickness, bandPaint)
        canvas.drawRect(tankLeft, band2Y, tankRight, band2Y + bandThickness, bandPaint)

        // Outline
        canvas.drawPath(tankPath, outlinePaint)

        // --- Foot Ring (based on reference image) ---
        val footWidth = tankWidth * 0.75f
        val footHeight = tankHeight * 0.1f
        val footTop = tankBottom - cornerRadius * 0.2f
        val footPath = Path()
        val footRect = RectF(cx - footWidth / 2, footTop, cx + footWidth / 2, footTop + footHeight)
        footPath.addRoundRect(footRect, floatArrayOf(0f, 0f, 0f, 0f, cornerRadius * 0.5f, cornerRadius * 0.5f, cornerRadius * 0.5f, cornerRadius * 0.5f), Path.Direction.CW)
        canvas.drawPath(footPath, footPaint)

        // --- Percentage Badge ---
        val badgeRadius = w * 0.11f
        val badgeCx = tankRight + badgeRadius * 0.15f
        val badgeCy = tankTop + tankHeight * 0.45f

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
        val height = (width * 1.15f).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }
}
