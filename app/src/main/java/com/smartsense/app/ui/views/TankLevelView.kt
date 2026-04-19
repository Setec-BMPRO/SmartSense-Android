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
import com.smartsense.app.domain.model.TankType

class TankLevelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val FILL_TOP_RATIO = 63.5f / 320f
        private const val FILL_BOTTOM_RATIO = 288.5f / 320f
        private const val BODY_TOP_RATIO = 62f / 320f
        private const val BODY_BOTTOM_RATIO = 290f / 320f
        private const val SVG_TANK_LEFT_RATIO = 40f / 320f
        private const val SVG_TANK_RIGHT_RATIO = 280f / 320f

        private const val H_FILL_TOP_RATIO = 7f / 32f
        private const val H_FILL_BOTTOM_RATIO = 25f / 32f
        private const val H_BODY_TOP_RATIO = 7f / 32f
        private const val H_BODY_BOTTOM_RATIO = 25f / 32f
        private const val H_SVG_TANK_LEFT_RATIO = 3f / 48f
        private const val H_SVG_TANK_RIGHT_RATIO = 45f / 48f

        private val VERTICAL_THRESHOLD_MM = ((TankType.KG_4.heightMeters + TankType.KG_9.heightMeters) / 2.0 * 1000.0).toFloat()
    }

    // --- State & Config ---
    private var percentage: Float = 0f
    private var levelStatus: LevelStatus = LevelStatus.RED
    private var levelUnit: TankLevelUnit = TankLevelUnit.PERCENT
    private var tankHeightMm: Float = 0f
    private var aspectRatio: Float = 1.33f
    private var tankTypeLabel: String = ""

    var isBiggerMode: Boolean = false
        set(value) {
            field = value
            lastWidth = 0
            if (!isHorizontal) {
                aspectRatio = if (value) 1.3f else 1.0f
            }
            requestLayout()
            invalidate()
        }

    var isHorizontal: Boolean = false
        set(value) {
            field = value
            if (value) {
                isBiggerMode = false
                aspectRatio = 0.695f
            } else {
                if (tankHeightMm > 0) {
                    isBiggerMode = tankHeightMm > VERTICAL_THRESHOLD_MM
                }
                aspectRatio = if (isBiggerMode) 1.3f else 1.0f
            }
            lastWidth = 0
            requestLayout()
            invalidate()
        }

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.TankLevelView, 0, 0)
            try {
                isHorizontal = a.getBoolean(R.styleable.TankLevelView_isHorizontal, false)
                isBiggerMode = a.getBoolean(R.styleable.TankLevelView_isBiggerMode, false)
            } finally {
                a.recycle()
            }
        }
    }

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
    private var lastHeight = 0
    private var lastDarkMode: Boolean? = null

    private fun isDarkMode(): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyThemeColors() {
        val dark = isDarkMode()
        circleFillPaint.color = if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
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
        if (!isHorizontal) {
            isBiggerMode = height > VERTICAL_THRESHOLD_MM
        }
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        
        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> widthSize
            else -> 400
        }
        
        val desiredHeight = (width * aspectRatio).toInt()
        
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }
        
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            buildBitmaps()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isInEditMode) {
            percentage = 65f
            levelStatus = LevelStatus.GREEN
            if (tankTypeLabel.isEmpty() || tankTypeLabel == "Vertical Tank" || tankTypeLabel == "Horizontal Tank" || tankTypeLabel == "Tall Vertical Tank") {
                tankTypeLabel = if (isHorizontal) {
                    "Horizontal Tank"
                } else if (isBiggerMode) {
                    "Tall Vertical Tank"
                } else {
                    "Vertical Tank"
                }
            }
        }

        val dark = isDarkMode()
        if (width <= 0 || height <= 0) return

        if (width != lastWidth || height != lastHeight || tankBitmap == null || lastDarkMode != dark) {
            lastWidth = width
            lastHeight = height
            applyThemeColors()
            buildBitmaps()

            val w = width.toFloat()
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

        val w = width.toFloat()
        val bounds = RectF(0f, 0f, w, height.toFloat())
        val topRatio = if (isHorizontal) H_FILL_TOP_RATIO else FILL_TOP_RATIO
        val bottomRatio = if (isHorizontal) H_FILL_BOTTOM_RATIO else FILL_BOTTOM_RATIO
        val bodyTopRatio = if (isHorizontal) H_BODY_TOP_RATIO else BODY_TOP_RATIO
        val bodyBottomRatio = if (isHorizontal) H_BODY_BOTTOM_RATIO else BODY_BOTTOM_RATIO

        val fillTopY = tankDrawTop + (tankDrawHeight * topRatio)
        val fillBottomY = tankDrawTop + (tankDrawHeight * bottomRatio)
        val bodyTopY = tankDrawTop + (tankDrawHeight * bodyTopRatio)
        val bodyBottomY = tankDrawTop + (tankDrawHeight * bodyBottomRatio)

        val tankLeftBound = (w - tankDrawWidth) / 2f
        val leftRatio = if (isHorizontal) H_SVG_TANK_LEFT_RATIO else SVG_TANK_LEFT_RATIO
        val rightRatio = if (isHorizontal) H_SVG_TANK_RIGHT_RATIO else SVG_TANK_RIGHT_RATIO
        val tankLeft = tankLeftBound + tankDrawWidth * leftRatio
        val tankWidthActual = tankDrawWidth * (rightRatio - leftRatio)

        // 1. Draw Tank body
        val saveBody = canvas.saveLayer(bounds, null)
        // Use full width for the body background, the mask will handle the rounded corners
        canvas.drawRect(tankLeftBound, bodyTopY, tankLeftBound + tankDrawWidth, bodyBottomY, tankGradientPaint)
        tankBitmap?.let { canvas.drawBitmap(it, 0f, 0f, maskPaint) }
        canvas.restoreToCount(saveBody)

        // 2. Draw Liquid
        if (percentage > 0f) {
            val totalHeight = fillBottomY - fillTopY
            var liquidTopY = fillBottomY - (totalHeight * (percentage / 100f))

            // Ensure at least a minimal sliver is visible for very low values (e.g. 1-5%)
            // especially when hardware strokes (1.2dp) might overlap the bottom
            val minHeight = 2.5f * context.resources.displayMetrics.density
            if (fillBottomY - liquidTopY < minHeight && percentage > 0f) {
                liquidTopY = fillBottomY - minHeight
            }

            val bottomColor = 0xFF1E88E5.toInt()
            val topColor = if (dark) 0xFFFF5252.toInt() else 0xFFD24520.toInt()

            fillPaint.shader = LinearGradient(0f, fillBottomY, 0f, fillTopY,
                bottomColor, topColor, Shader.TileMode.CLAMP)

            val saveFill = canvas.saveLayer(bounds, null)
            // Draw liquid rectangle across the full tank width; the mask will clip it to the rounded ends
            canvas.drawRect(tankLeftBound, liquidTopY, tankLeftBound + tankDrawWidth, fillBottomY, fillPaint)

            canvas.drawRect(
                tankLeft, liquidTopY,
                tankLeft + tankWidthActual * 0.35f, fillBottomY,
                fillHighlightPaint
            )

            tankBitmap?.let { canvas.drawBitmap(it, 0f, 0f, maskPaint) }
            canvas.restoreToCount(saveFill)
        }

        // 3. Hardware
        hardwareBitmap?.let { canvas.drawBitmap(it, 0f, 0f, hardwarePaint) }

        // 4. Labels & Badge
        drawLabelsAndBadge(canvas, w, fillTopY, fillBottomY, dark)
    }

    private fun drawLabelsAndBadge(canvas: Canvas, w: Float, fillTopY: Float, fillBottomY: Float, dark: Boolean) {
        if (tankTypeLabel.isNotEmpty()) {
            val labelCx = w / 2f
            val labelCy = (fillTopY + fillBottomY) / 2f

            val textToDraw = tankTypeLabel.uppercase()
            val lines = if (textToDraw.contains(" (")) {
                val index = textToDraw.indexOf(" (")
                listOf(textToDraw.substring(0, index), textToDraw.substring(index + 1))
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

        val badgeRadius = tankDrawWidth * 0.14f
        val left = (w - tankDrawWidth) / 2f
        val badgeCx = if (isHorizontal) left + tankDrawWidth * 0.90f else left + tankDrawWidth * 0.85f
        val badgeCy = if (isHorizontal) tankDrawTop + tankDrawHeight * 0.35f else tankDrawTop + tankDrawHeight * 0.35f

        circleBorderPaint.color = when (levelStatus) {
            LevelStatus.GREEN -> ContextCompat.getColor(context, R.color.level_green)
            LevelStatus.YELLOW -> ContextCompat.getColor(context, R.color.level_yellow)
            LevelStatus.RED -> ContextCompat.getColor(context, R.color.level_red)
        }
        circleBorderPaint.strokeWidth = 4f * (tankDrawWidth / 320f)

        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, circleFillPaint)
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, circleBorderPaint)

        circleTextPaint.color = circleBorderPaint.color
        circleTextPaint.isFakeBoldText = true

        if (percentage <= 0f) {
            circleTextPaint.textSize = badgeRadius * 0.5f
            circleTextPaint.textAlign = Paint.Align.CENTER
            val text = "Empty"
            val textY = badgeCy - (circleTextPaint.descent() + circleTextPaint.ascent()) / 2f
            canvas.drawText(text, badgeCx, textY, circleTextPaint)
        } else {
            val valueText = when (levelUnit) {
                TankLevelUnit.INCHES -> {
                    val inchValue = ((tankHeightMm * 0.0393701f) * (percentage / 100f)).toInt()
                    if (inchValue == 0 && percentage > 0) "1" else inchValue.toString()
                }
                TankLevelUnit.PERCENT -> {
                    val rounded = (percentage.toDouble()).toInt()
                    val displayValue = if (rounded == 0 && percentage > 0) 1 else rounded
                    displayValue.coerceIn(1, 100).toString()
                }
                else -> {
                    val cmValue = ((tankHeightMm / 10f) * (percentage / 100f)).toInt()
                    if (cmValue == 0 && percentage > 0) "1" else cmValue.toString()
                }
            }
            val unitText = levelUnit.shortName

            val valueSize = badgeRadius * 0.7f
            val unitSize = badgeRadius * 0.6f

            circleTextPaint.textSize = valueSize
            val valueWidth = circleTextPaint.measureText(valueText)

            circleTextPaint.textSize = unitSize
            val unitWidth = circleTextPaint.measureText(unitText)

            val spacing = badgeRadius * 0.1f
            val totalWidth = valueWidth + (if (unitText.isNotEmpty()) spacing + unitWidth else 0f)

            val startX = badgeCx - totalWidth / 2f

            circleTextPaint.textAlign = Paint.Align.LEFT
            circleTextPaint.textSize = valueSize
            val valueY = badgeCy - (circleTextPaint.descent() + circleTextPaint.ascent()) / 2f
            canvas.drawText(valueText, startX, valueY, circleTextPaint)

            if (unitText.isNotEmpty()) {
                circleTextPaint.textSize = unitSize
                val unitY = badgeCy - (circleTextPaint.descent() + circleTextPaint.ascent()) / 2f
                canvas.drawText(unitText, startX + valueWidth + spacing, unitY, circleTextPaint)
            }
            circleTextPaint.textAlign = Paint.Align.CENTER
        }
    }

    private fun buildBitmaps() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val scale = if (isHorizontal) 0.84f else if (isBiggerMode) 0.95f else 0.9f

        if (isHorizontal) {
            tankDrawWidth = w * scale
            tankDrawHeight = tankDrawWidth * (32f / 48f)
            if (tankDrawHeight > h) {
                val adjust = h / tankDrawHeight
                tankDrawWidth *= adjust
                tankDrawHeight *= adjust
            }
        } else {
            val baseSize = minOf(w, h)
            tankDrawWidth = baseSize * scale
            tankDrawHeight = if (isBiggerMode) tankDrawWidth * 1.3f else tankDrawWidth
        }

        val left = (width - tankDrawWidth) / 2f
        tankDrawTop = (height - tankDrawHeight) / 2f

        val silhouetteRes = if (isHorizontal) R.drawable.ic_tank_silhouette_horizontal else R.drawable.ic_tank_silhouette
        val hardwareRes = if (isHorizontal) R.drawable.ic_tank_hardware_horizontal else R.drawable.ic_tank_hardware

        tankBitmap?.recycle()
        tankBitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                val c = Canvas(this)
                ContextCompat.getDrawable(context, silhouetteRes)?.apply {
                    setBounds(left.toInt(), tankDrawTop.toInt(), (left + tankDrawWidth).toInt(), (tankDrawTop + tankDrawHeight).toInt())
                    draw(c)
                }
            }
        } catch (e: Exception) {
            null
        }

        hardwareBitmap?.recycle()
        hardwareBitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                val c = Canvas(this)
                ContextCompat.getDrawable(context, hardwareRes)?.apply {
                    setBounds(left.toInt(), tankDrawTop.toInt(), (left + tankDrawWidth).toInt(), (tankDrawTop + tankDrawHeight).toInt())
                    draw(c)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
