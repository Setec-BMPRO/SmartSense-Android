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
import android.view.View
import androidx.core.content.ContextCompat
import com.smartsense.app.R
import com.smartsense.app.domain.model.LevelStatus

class TankLevelMiniView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var percentage: Float = 0f
    private var levelStatus: LevelStatus = LevelStatus.RED

    private val tankGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val outlineBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hardwareTintPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var tankBitmap: Bitmap? = null
    private var hardwareBitmap: Bitmap? = null
    private var lastWidth = 0
    private var lastDarkMode: Boolean? = null

    companion object {
        private const val FILL_TOP_RATIO = 0.300f
        private const val FILL_BOTTOM_RATIO = 0.778f
        private const val SVG_TANK_LEFT_RATIO = 68f / 447f
        private const val SVG_TANK_RIGHT_RATIO = 379f / 447f
    }

    private fun isDarkMode(): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    fun setLevel(percentage: Float, status: LevelStatus) {
        this.percentage = percentage.coerceIn(0f, 100f)
        this.levelStatus = status

        invalidate()
    }

    private fun buildBitmap(size: Int) {
        tankBitmap?.recycle()
        hardwareBitmap?.recycle()

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_tank_silhouette) ?: return
        drawable.setBounds(0, 0, size, size)
        drawable.draw(c)
        tankBitmap = bitmap

        val hwBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val hwCanvas = Canvas(hwBitmap)
        val hwDrawable = ContextCompat.getDrawable(context, R.drawable.ic_tank_hardware) ?: return
        hwDrawable.setBounds(0, 0, size, size)
        hwDrawable.draw(hwCanvas)
        hardwareBitmap = hwBitmap
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = minOf(width, height)
        val dark = isDarkMode()

        if (lastDarkMode != dark) {
            lastDarkMode = dark
            lastWidth = 0
            val outlineColor = if (dark) 0xFFAAAAAA.toInt() else 0xFF5A5A5A.toInt()
            outlineBitmapPaint.colorFilter =
                PorterDuffColorFilter(outlineColor, PorterDuff.Mode.SRC_IN)
            hardwareTintPaint.colorFilter = PorterDuffColorFilter(
                if (dark) 0xFFBDBDBD.toInt() else 0xFF000000.toInt(),
                PorterDuff.Mode.SRC_IN
            )
        }

        if (size != lastWidth) {
            lastWidth = size
            buildBitmap(size)

            val tankLeft = size * SVG_TANK_LEFT_RATIO
            val tankRight = size * SVG_TANK_RIGHT_RATIO
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
        val hwBitmap = hardwareBitmap
        val s = size.toFloat()
        val bounds = RectF(0f, 0f, s, s)

        val fillTopY = s * FILL_TOP_RATIO
        val fillBottomY = s * FILL_BOTTOM_RATIO

        // Outline
        val scale = s / 447f
        val outlineWidth = 3f * scale
        val cx = s / 2f
        val outlineScale = 1f + (outlineWidth * 2f / s)
        canvas.save()
        canvas.scale(outlineScale, outlineScale, cx, cx)
        canvas.drawBitmap(bitmap, 0f, 0f, outlineBitmapPaint)
        canvas.restore()

        // Hardware (Handle, Stand)
        hwBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, hardwareTintPaint)
        }

        // Tank body: metallic gradient masked by silhouette, but only between the lines
        val save1 = canvas.saveLayer(bounds, null)
        canvas.drawRect(0f, fillTopY, s, fillBottomY, tankGradientPaint)
        canvas.drawBitmap(bitmap, 0f, 0f, maskPaint)
        canvas.restoreToCount(save1)

        // Liquid fill masked by silhouette
        if (percentage > 0f) {
            // Apply vertical gradient: Blue (Bottom) -> Red (Top)
            val topColor = if (dark) 0xFFFF5252.toInt() else 0xFFD24520.toInt() // Softer red in dark mode
            fillPaint.shader = LinearGradient(
                0f, fillBottomY, 0f, fillTopY,
                0xFF1E88E5.toInt(), // Blue
                topColor, // Red
                Shader.TileMode.CLAMP
            )

            val bandHeight = fillBottomY - fillTopY
            val liquidTopY = fillBottomY - (bandHeight * percentage / 100f)

            val tankLeft = s * SVG_TANK_LEFT_RATIO
            val tankRight = s * SVG_TANK_RIGHT_RATIO
            val tankWidth = tankRight - tankLeft

            val save2 = canvas.saveLayer(bounds, null)
            canvas.drawRect(0f, liquidTopY, s, fillBottomY, fillPaint)

            fillHighlightPaint.shader = LinearGradient(
                tankLeft, 0f, tankLeft + tankWidth * 0.35f, 0f,
                0x30FFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(
                tankLeft, liquidTopY,
                tankLeft + tankWidth * 0.35f, fillBottomY,
                fillHighlightPaint
            )

            canvas.drawBitmap(bitmap, 0f, 0f, maskPaint)
            canvas.restoreToCount(save2)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = minOf(w, h)
        setMeasuredDimension(size, size)
    }
}
