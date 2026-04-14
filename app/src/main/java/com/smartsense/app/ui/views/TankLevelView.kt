package com.smartsense.app.ui.views

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.smartsense.app.R
import com.smartsense.app.domain.model.LevelStatus
import com.smartsense.app.domain.model.TankLevelUnit
import timber.log.Timber

class TankLevelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var percentage: Float = 0f
    private var levelStatus: LevelStatus = LevelStatus.RED
    private var levelText: String = "Empty"

    private val tankGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val circleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val circleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val shadowBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlineBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val hardwareTintPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var tankBitmap: Bitmap? = null
    private var hardwareBitmap: Bitmap? = null
    private var tankDrawLeft = 0f
    private var tankDrawTop = 0f
    private var tankDrawSize = 0f

    private var lastDarkMode: Boolean? = null
    private var lastWidth = 0

    // Draggable fill markers
    var showMarkers: Boolean = false
        set(value) { field = value; invalidate() }
    private var topRatio: Float = FILL_TOP_RATIO
    private var bottomRatio: Float = FILL_BOTTOM_RATIO
    private var dragging: Int = 0 // 0=none, 1=top, 2=bottom
    private val topMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE53935.toInt(); style = Paint.Style.FILL
    }
    private val bottomMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E88E5.toInt(); style = Paint.Style.FILL
    }
    private val markerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF333333.toInt(); textSize = 28f; textAlign = Paint.Align.LEFT
    }
    var onMarkersChanged: ((topRatio: Float, bottomRatio: Float) -> Unit)? = null

    companion object {
        private const val FILL_TOP_RATIO = 0.17f
        private const val FILL_BOTTOM_RATIO = 0.92f
        private const val SVG_TANK_LEFT_RATIO = 100f / 447f
        private const val SVG_TANK_RIGHT_RATIO = 347f / 447f
    }

    private fun isDarkMode(): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyThemeColors() {
        val dark = isDarkMode()
        if (dark) {
            circleFillPaint.color = 0xFF1E1E1E.toInt()
            shadowPaint.color = 0x30000000
            outlineBitmapPaint.colorFilter =
                PorterDuffColorFilter(0xFFAAAAAA.toInt(), PorterDuff.Mode.SRC_IN)
        } else {
            circleFillPaint.color = 0xFFFFFFFF.toInt()
            shadowPaint.color = 0x18000000
            outlineBitmapPaint.colorFilter =
                PorterDuffColorFilter(0xFF5A5A5A.toInt(), PorterDuff.Mode.SRC_IN)
        }
        shadowBitmapPaint.colorFilter =
            PorterDuffColorFilter(shadowPaint.color, PorterDuff.Mode.SRC_IN)
        hardwareTintPaint.colorFilter = PorterDuffColorFilter(
            if (dark) 0xFF555555.toInt() else 0xFFCCCCCC.toInt(),
            PorterDuff.Mode.SRC_IN
        )
    }

    var levelUnit: TankLevelUnit = TankLevelUnit.default()
    var tankHeightMm: Float = 0F

    fun setLevelUnit(levelUnit: TankLevelUnit, tankHeightMm: Float) {
        this.levelUnit = levelUnit
        this.tankHeightMm = tankHeightMm
    }

    fun setMarkerRatios(top: Float, bottom: Float) {
        topRatio = top
        bottomRatio = bottom
        invalidate()
    }

    fun setLevel(percentage: Float, status: LevelStatus) {
        this.percentage = percentage.coerceIn(0f, 100f)
        this.levelStatus = status
        this.levelText = if (percentage <= 0f) {
            "Empty".also { Timber.d("Level: Empty") }
        } else {
            val result = when (levelUnit) {
                TankLevelUnit.INCHES -> {
                    val currentInches = (tankHeightMm * 0.0393701f) * (percentage / 100f)
                    "${currentInches.toInt()} ${levelUnit.shortName}"
                }
                TankLevelUnit.PERCENT -> {
                    "${percentage.toInt()} ${levelUnit.shortName}"
                }
                else -> {
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

    private fun buildBitmap(w: Int, h: Int) {
        tankBitmap?.recycle()
        hardwareBitmap?.recycle()

        val size = minOf(w, h)
        val left = (w - size) / 2
        val top = (h - size) / 2

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_tank_silhouette) ?: return
        drawable.setBounds(left, top, left + size, top + size)
        drawable.draw(c)
        tankBitmap = bitmap

        val hwBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val hwCanvas = Canvas(hwBitmap)
        val hwDrawable = ContextCompat.getDrawable(context, R.drawable.ic_tank_hardware) ?: return
        hwDrawable.setBounds(left, top, left + size, top + size)
        hwDrawable.draw(hwCanvas)
        hardwareBitmap = hwBitmap

        tankDrawLeft = left.toFloat()
        tankDrawTop = top.toFloat()
        tankDrawSize = size.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val dark = isDarkMode()

        if (lastDarkMode != dark) {
            lastDarkMode = dark
            lastWidth = 0
            applyThemeColors()
        }

        if (width != lastWidth) {
            lastWidth = width
            buildBitmap(width, height)

            val tankLeft = tankDrawLeft + tankDrawSize * SVG_TANK_LEFT_RATIO
            val tankRight = tankDrawLeft + tankDrawSize * SVG_TANK_RIGHT_RATIO

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
        }

        val bitmap = tankBitmap ?: return
        val scale = tankDrawSize / 447f
        val bounds = RectF(0f, 0f, w, h)

        // Drop shadow (light mode only)
        if (!dark) {
            val off = 3f * scale
            canvas.save()
            canvas.translate(off, off)
            canvas.drawBitmap(bitmap, 0f, 0f, shadowBitmapPaint)
            canvas.restore()
        }

        // Outline: slightly enlarged silhouette tinted with border colour
        val outlineWidth = 5f * scale
        val cx = tankDrawLeft + tankDrawSize / 2f
        val cy = tankDrawTop + tankDrawSize / 2f
        val outlineScale = 1f + (outlineWidth * 2f / tankDrawSize)
        canvas.save()
        canvas.scale(outlineScale, outlineScale, cx, cy)
        canvas.drawBitmap(bitmap, 0f, 0f, outlineBitmapPaint)
        canvas.restore()

        // Tank body: metallic gradient masked by silhouette
        val save1 = canvas.saveLayer(bounds, null)
        canvas.drawRect(bounds, tankGradientPaint)
        canvas.drawBitmap(bitmap, 0f, 0f, maskPaint)
        canvas.restoreToCount(save1)

        // Liquid fill masked by silhouette
        if (percentage > 0f) {
            val fillTopY = tankDrawTop + tankDrawSize * topRatio
            val fillBottomY = tankDrawTop + tankDrawSize * bottomRatio
            val bandHeight = fillBottomY - fillTopY
            val liquidTopY = fillBottomY - (bandHeight * percentage / 100f)

            val tankLeft = tankDrawLeft + tankDrawSize * SVG_TANK_LEFT_RATIO
            val tankRight = tankDrawLeft + tankDrawSize * SVG_TANK_RIGHT_RATIO
            val tankWidth = tankRight - tankLeft

            val save2 = canvas.saveLayer(bounds, null)
            canvas.drawRect(0f, liquidTopY, w, fillBottomY + 10f, fillPaint)

            fillHighlightPaint.shader = LinearGradient(
                tankLeft, 0f, tankLeft + tankWidth * 0.35f, 0f,
                0x30FFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(
                tankLeft, liquidTopY,
                tankLeft + tankWidth * 0.35f, fillBottomY + 10f,
                fillHighlightPaint
            )

            canvas.drawBitmap(bitmap, 0f, 0f, maskPaint)
            canvas.restoreToCount(save2)
        }

        // Percentage badge
        val badgeRadius = tankDrawSize * 0.11f
        val badgeCx = tankDrawLeft + tankDrawSize * 0.82f
        val badgeCy = tankDrawTop + tankDrawSize * 0.40f
        circleBorderPaint.strokeWidth = 3f * scale

        if (!dark) {
            val off = 1.5f * scale
            canvas.drawCircle(badgeCx + off, badgeCy + off, badgeRadius, shadowPaint)
        }
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, circleFillPaint)
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, circleBorderPaint)

        circleTextPaint.color = circleBorderPaint.color
        circleTextPaint.textSize = badgeRadius * 0.6f
        circleTextPaint.isFakeBoldText = true
        val textY = badgeCy - (circleTextPaint.descent() + circleTextPaint.ascent()) / 2f
        canvas.drawText(levelText, badgeCx, textY, circleTextPaint)

        // Draggable fill markers
        if (showMarkers) {
            val markerH = 3f * scale
            val topY = tankDrawTop + tankDrawSize * topRatio
            val bottomY = tankDrawTop + tankDrawSize * bottomRatio
            val markerLeft = tankDrawLeft
            val markerRight = tankDrawLeft + tankDrawSize

            // Top marker (red)
            canvas.drawRect(markerLeft, topY - markerH / 2, markerRight, topY + markerH / 2, topMarkerPaint)
            // Bottom marker (blue)
            canvas.drawRect(markerLeft, bottomY - markerH / 2, markerRight, bottomY + markerH / 2, bottomMarkerPaint)

            // Labels
            markerLabelPaint.textSize = 11f * scale
            markerLabelPaint.color = 0xFFE53935.toInt()
            canvas.drawText("Top: %.2f".format(topRatio), markerLeft + 4f * scale, topY - 6f * scale, markerLabelPaint)
            markerLabelPaint.color = 0xFF1E88E5.toInt()
            canvas.drawText("Bottom: %.2f".format(bottomRatio), markerLeft + 4f * scale, bottomY + 16f * scale, markerLabelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!showMarkers) return super.onTouchEvent(event)

        val y = event.y
        val topY = tankDrawTop + tankDrawSize * topRatio
        val bottomY = tankDrawTop + tankDrawSize * bottomRatio
        val touchSlop = 44f * resources.displayMetrics.density / 2f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val distTop = kotlin.math.abs(y - topY)
                val distBottom = kotlin.math.abs(y - bottomY)
                dragging = when {
                    distTop <= touchSlop && distTop <= distBottom -> 1
                    distBottom <= touchSlop -> 2
                    else -> 0
                }
                if (dragging != 0) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging == 1) {
                    topRatio = ((y - tankDrawTop) / tankDrawSize).coerceIn(0f, bottomRatio - 0.02f)
                    onMarkersChanged?.invoke(topRatio, bottomRatio)
                    invalidate()
                    return true
                } else if (dragging == 2) {
                    bottomRatio = ((y - tankDrawTop) / tankDrawSize).coerceIn(topRatio + 0.02f, 1f)
                    onMarkersChanged?.invoke(topRatio, bottomRatio)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging != 0) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    dragging = 0
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * 1.15f).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }
}
