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
    }

    // --- Configuration Flags ---
    var isSmallMode: Boolean = true
        set(value) { field = value; lastWidth = 0; requestLayout() }

    var isTallMode: Boolean = false
        set(value) { field = value; lastWidth = 0; requestLayout() }

    private var percentage: Float = 0f
    private var levelStatus: LevelStatus = LevelStatus.RED
    private var levelUnit: TankLevelUnit = TankLevelUnit.PERCENT
    private var tankHeightMm: Float = 0f
    private var aspectRatio: Float = 1.32f
    private var tankTypeLabel: String = ""

    // --- Paints ---
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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

        // Rebuild if width changed OR if the theme (day/night) toggled
        if (width != lastWidth || tankBitmap == null || lastDarkMode != dark) {
            lastWidth = width
            applyThemeColors()
            buildBitmaps()
        }

        val w = width.toFloat()
        val bounds = RectF(0f, 0f, w, height.toFloat())

        val fillTopY = tankDrawTop + (tankDrawHeight * FILL_TOP_RATIO)
        val fillBottomY = tankDrawTop + (tankDrawHeight * FILL_BOTTOM_RATIO)

        // 1. Draw Liquid Fill
        if (percentage >= 0f) {
            val totalHeight = fillBottomY - fillTopY
            val liquidTopY = fillBottomY - (totalHeight * (percentage / 100f))

            // Adjust liquid gradient colors slightly for dark mode for better contrast
            val bottomColor = 0xFF1E88E5.toInt() // Blue
            val topColor = if (dark) 0xFFFF5252.toInt() else 0xFFD24520.toInt() // Softer red in dark mode

            fillPaint.shader = LinearGradient(0f, fillBottomY, 0f, fillTopY,
                bottomColor, topColor, Shader.TileMode.CLAMP)

            val save = canvas.saveLayer(bounds, null)
            canvas.drawRect(0f, liquidTopY, w, fillBottomY, fillPaint)
            tankBitmap?.let { canvas.drawBitmap(it, 0f, 0f, maskPaint) }
            canvas.restoreToCount(save)
        }

        // 2. Draw Hardware
        hardwareBitmap?.let { canvas.drawBitmap(it, 0f, 0f, hardwarePaint) }

        // 3. Labels and Badge
        drawLabelsAndBadge(canvas, w, fillTopY, fillBottomY, dark)
    }

    private fun drawLabelsAndBadge(canvas: Canvas, w: Float, fillTopY: Float, fillBottomY: Float, dark: Boolean) {
        if (tankTypeLabel.isNotEmpty()) {
            val labelCx = w / 2f
            val labelCy = (fillTopY + fillBottomY) / 2f
            tankLabelPaint.textSize = tankDrawWidth * 0.085f
            tankLabelPaint.color = if (dark) 0x90FFFFFF.toInt() else 0x70000000.toInt()
            val labelY = labelCy - (tankLabelPaint.descent() + tankLabelPaint.ascent()) / 2f
            canvas.drawText(tankTypeLabel.uppercase(), labelCx, labelY, tankLabelPaint)
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
        val scale = if (isSmallMode) 0.8f else 1.0f
        tankDrawWidth = baseSize * scale
        tankDrawHeight = if (isTallMode) tankDrawWidth * 1.2f else tankDrawWidth

        val left = (width - tankDrawWidth) / 2f
        val verticalOffset = tankDrawHeight * (26.5f / 447f)
        tankDrawTop = ((height - tankDrawHeight) / 2f) - verticalOffset

        tankBitmap?.recycle()
        tankBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            ContextCompat.getDrawable(context, R.drawable.ic_tank_silhouette)?.apply {
                setBounds(left.toInt(), tankDrawTop.toInt(), (left + tankDrawWidth).toInt(), (tankDrawTop + tankDrawHeight).toInt())
                draw(c)
            }
        }

        hardwareBitmap?.recycle()
        hardwareBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            ContextCompat.getDrawable(context, R.drawable.ic_tank_hardware)?.apply {
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