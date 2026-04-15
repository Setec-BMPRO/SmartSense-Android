package com.smartsense.app.ui.views

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.smartsense.app.R
import com.smartsense.app.domain.model.LevelStatus
import com.smartsense.app.domain.model.TankLevelUnit

class TankLevelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val FILL_TOP_RATIO = 0.268f
        private const val FILL_BOTTOM_RATIO = 0.778f
        private const val SVG_TANK_LEFT_RATIO = 68f / 447f
        private const val SVG_TANK_RIGHT_RATIO = 379f / 447f

        private const val H_FILL_TOP_RATIO = 14f / 48f
        private const val H_FILL_BOTTOM_RATIO = 34f / 48f
        private const val H_SVG_TANK_LEFT_RATIO = 2f / 48f
        private const val H_SVG_TANK_RIGHT_RATIO = 46f / 48f
    }

    // --- Configuration Flags ---
    var isTallMode: Boolean = false
        set(value) { field = value; lastWidth = 0; requestLayout() }

    var isHorizontal: Boolean = false
        set(value) {
            field = value
            lastWidth = 0
            if (value && aspectRatio == 1.32f) aspectRatio = 0.85f
            requestLayout()
        }

    private var percentage: Float = 0f
    private var levelStatus: LevelStatus = LevelStatus.RED
    private var levelUnit: TankLevelUnit = TankLevelUnit.PERCENT
    private var tankHeightMm: Float = 0f
    private var aspectRatio: Float = 1.32f
    private var tankTypeLabel: String = ""

    // --- Paints ---
    private val tankGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val hardwarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tankLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val circleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val circleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var tankBitmap: Bitmap? = null
    private var hardwareBitmap: Bitmap? = null
    private var tankDrawTop = 0f
    private var tankDrawWidth = 0f
    private var tankDrawHeight = 0f
    private var lastWidth = 0
    private var lastDarkMode: Boolean? = null

    private fun isDarkMode(): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyThemeColors() {
        val dark = isDarkMode()

        // Background of the percentage badge
        circleFillPaint.color = if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()

        // Hardware tinting (Handle/Feet)
        // In dark mode, we make them a slightly lighter gray so they don't disappear into true black
        hardwarePaint.colorFilter = PorterDuffColorFilter(
            if (dark) 0xFFBDBDBD.toInt() else 0xFF000000.toInt(),
            PorterDuff.Mode.SRC_IN
        )

        lastDarkMode = dark
    }

    // --- Public API ---
    fun setLevel(pct: Float, status: LevelStatus) {
        this.percentage = pct.coerceIn(0f, 100f)
        this.levelStatus = status
        invalidate()
    }

    fun setLevelUnit(unit: TankLevelUnit, height: Float) {
        this.levelUnit = unit
        this.tankHeightMm = height
        invalidate()
    }

    fun setAspectRatio(ratio: Float) {
        this.aspectRatio = ratio
        requestLayout()
    }

    fun setTankTypeLabel(label: String) {
        this.tankTypeLabel = label
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val dark = isDarkMode()
        if (width == 0 || height == 0) return

        val w = width.toFloat()

        // Rebuild if width changed OR if the theme (day/night) toggled
        if (width != lastWidth || tankBitmap == null || lastDarkMode != dark) {
            lastWidth = width
            applyThemeColors()
            buildBitmaps()

            val tankLeftBound = (w - tankDrawWidth) / 2f
            val leftRatio = if (isHorizontal) H_SVG_TANK_LEFT_RATIO else SVG_TANK_LEFT_RATIO
            val rightRatio = if (isHorizontal) H_SVG_TANK_RIGHT_RATIO else SVG_TANK_RIGHT_RATIO

            val tankLeft = tankLeftBound + tankDrawWidth * leftRatio
            val tankRight = tankLeftBound + tankDrawWidth * rightRatio
            val tankWidthActual = tankRight - tankLeft

            tankGradientPaint.shader = if (dark) {
                LinearGradient(
                    tankLeft, 0f, tankRight, 0f,
                    intArrayOf(0xFF3A3A3A.toInt(), 0xFF4A4A4A.toInt(), 0xFF333333.toInt()),
                    floatArrayOf(0f, 0.4f, 1f),
                    Shader.TileMode.CLAMP
                )
            } else {
                LinearGradient(
                    tankLeft, 0f, tankRight, 0f,
                    intArrayOf(0xFFD0D0D0.toInt(), 0xFFF2F2F2.toInt(), 0xFFC4C4C4.toInt()),
                    floatArrayOf(0f, 0.4f, 1f),
                    Shader.TileMode.CLAMP
                )
            }

            fillHighlightPaint.shader = LinearGradient(
                tankLeft, 0f, tankLeft + tankWidthActual * 0.35f, 0f,
                0x30FFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
        }

        val bounds = RectF(0f, 0f, w, height.toFloat())
        val topRatio = if (isHorizontal) H_FILL_TOP_RATIO else FILL_TOP_RATIO
        val bottomRatio = if (isHorizontal) H_FILL_BOTTOM_RATIO else FILL_BOTTOM_RATIO

        val fillTopY = tankDrawTop + (tankDrawHeight * topRatio)
        val fillBottomY = tankDrawTop + (tankDrawHeight * bottomRatio)

        val tankLeftBound = (w - tankDrawWidth) / 2f
        val leftRatio = if (isHorizontal) H_SVG_TANK_LEFT_RATIO else SVG_TANK_LEFT_RATIO
        val rightRatio = if (isHorizontal) H_SVG_TANK_RIGHT_RATIO else SVG_TANK_RIGHT_RATIO
        val tankLeft = tankLeftBound + tankDrawWidth * leftRatio
        val tankWidthActual = tankDrawWidth * (rightRatio - leftRatio)

        // 1. Draw Tank metallic body
        val saveBody = canvas.saveLayer(bounds, null)
        canvas.drawRect(0f, fillTopY, w, fillBottomY, tankGradientPaint)
        tankBitmap?.let { canvas.drawBitmap(it, 0f, 0f, maskPaint) }
        canvas.restoreToCount(saveBody)

        // 2. Draw Liquid Fill
        if (percentage > 0f) {
            val totalHeight = fillBottomY - fillTopY
            val liquidTopY = fillBottomY - (totalHeight * (percentage / 100f))

            // Adjust liquid gradient colors slightly for dark mode for better contrast
            val bottomColor = 0xFF1E88E5.toInt() // Blue
            val topColor = if (dark) 0xFFFF5252.toInt() else 0xFFD24520.toInt() // Softer red in dark mode

            fillPaint.shader = LinearGradient(0f, fillBottomY, 0f, fillTopY,
                bottomColor, topColor, Shader.TileMode.CLAMP)

            val saveFill = canvas.saveLayer(bounds, null)
            canvas.drawRect(0f, liquidTopY, w, fillBottomY, fillPaint)

            // Liquid Highlight
            canvas.drawRect(
                tankLeft, liquidTopY,
                tankLeft + tankWidthActual * 0.35f, fillBottomY,
                fillHighlightPaint
            )

            tankBitmap?.let { canvas.drawBitmap(it, 0f, 0f, maskPaint) }
            canvas.restoreToCount(saveFill)
        }

        // 3. Draw Hardware
        hardwareBitmap?.let { canvas.drawBitmap(it, 0f, 0f, hardwarePaint) }

        // 4. Labels and Badge
        drawLabelsAndBadge(canvas, w, fillTopY, fillBottomY, dark)
    }

    private fun drawLabelsAndBadge(canvas: Canvas, w: Float, fillTopY: Float, fillBottomY: Float, dark: Boolean) {
        if (tankTypeLabel.isNotEmpty()) {
            val labelCx = w / 2f
            val labelCy = (fillTopY + fillBottomY) / 2f

            val textToDraw = tankTypeLabel.uppercase()
            val lines = if (textToDraw.contains(" (")) {
                val index = textToDraw.indexOf(" (")
                listOf(
                    textToDraw.substring(0, index),
                    textToDraw.substring(index + 1)
                )
            } else {
                listOf(textToDraw)
            }

            tankLabelPaint.textSize = if (lines.size > 1) tankDrawWidth * 0.075f else tankDrawWidth * 0.085f
            tankLabelPaint.color = if (dark) 0x90FFFFFF.toInt() else 0x70000000.toInt()

            val fontSpacing = tankLabelPaint.fontSpacing
            val totalTextHeight = (lines.size - 1) * fontSpacing
            val startY = labelCy - (totalTextHeight / 2f) - ((tankLabelPaint.ascent() + tankLabelPaint.descent()) / 2f)

            lines.forEachIndexed { index, line ->
                canvas.drawText(line, labelCx, startY + (index * fontSpacing), tankLabelPaint)
            }
        }

        val badgeRadius = tankDrawWidth * 0.13f
        val left = (w - tankDrawWidth) / 2f
        val badgeCx = left + tankDrawWidth * 0.85f
        val badgeCy = tankDrawTop + tankDrawHeight * 0.35f

        circleBorderPaint.color = when (levelStatus) {
            LevelStatus.GREEN -> ContextCompat.getColor(context, R.color.level_green)
            LevelStatus.YELLOW -> ContextCompat.getColor(context, R.color.level_yellow)
            LevelStatus.RED -> ContextCompat.getColor(context, R.color.level_red)
        }
        circleBorderPaint.strokeWidth = 4f * (tankDrawWidth / 447f)

        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, circleFillPaint)
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, circleBorderPaint)

        circleTextPaint.color = circleBorderPaint.color
        circleTextPaint.textSize = badgeRadius * 0.6f
        circleTextPaint.isFakeBoldText = true
        val textY = badgeCy - (circleTextPaint.descent() + circleTextPaint.ascent()) / 2f

        val levelText = if (percentage <= 0f) "Empty" else {
            when (levelUnit) {
                TankLevelUnit.INCHES -> "${((tankHeightMm * 0.0393701f) * (percentage / 100f)).toInt()} ${levelUnit.shortName}"
                TankLevelUnit.PERCENT -> "${percentage.toInt()} ${levelUnit.shortName}"
                else -> "${((tankHeightMm / 10f) * (percentage / 100f)).toInt()} ${levelUnit.shortName}"
            }
        }
        canvas.drawText(levelText, badgeCx, textY, circleTextPaint)
    }

    private fun buildBitmaps() {
        val baseSize = minOf(width, height).toFloat()
        val scale = if (isTallMode) 1.0f else 0.8f
        tankDrawWidth = baseSize * scale
        tankDrawHeight = if (isTallMode) tankDrawWidth * 1.2f else tankDrawWidth

        val left = (width - tankDrawWidth) / 2f
        val verticalOffsetRatio = if (isHorizontal) (8f / 48f) else (26.5f / 447f)
        val verticalOffset = tankDrawHeight * verticalOffsetRatio
        tankDrawTop = ((height - tankDrawHeight) / 2f) - verticalOffset

        val silhouetteRes = if (isHorizontal) R.drawable.ic_tank_silhouette_horizontal else R.drawable.ic_tank_silhouette
        val hardwareRes = if (isHorizontal) R.drawable.ic_tank_hardware_horizontal else R.drawable.ic_tank_hardware

        tankBitmap?.recycle()
        tankBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            ContextCompat.getDrawable(context, silhouetteRes)?.apply {
                setBounds(left.toInt(), tankDrawTop.toInt(), (left + tankDrawWidth).toInt(), (tankDrawTop + tankDrawHeight).toInt())
                draw(c)
            }
        }

        hardwareBitmap?.recycle()
        hardwareBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            ContextCompat.getDrawable(context, hardwareRes)?.apply {
                setBounds(left.toInt(), tankDrawTop.toInt(), (left + tankDrawWidth).toInt(), (tankDrawTop + tankDrawHeight).toInt())
                draw(c)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val dynamicRatio = if (isTallMode) aspectRatio * 1.2f else aspectRatio
        setMeasuredDimension(w, (w * dynamicRatio).toInt())
    }
}