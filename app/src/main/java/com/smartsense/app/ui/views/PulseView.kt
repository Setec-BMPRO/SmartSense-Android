package com.smartsense.app.ui.views

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.smartsense.app.R

class PulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val pulseCount = 3
    private val pulseDuration = 2800L
    private val minRadiusFraction = 0.28f  // start just outside the icon
    private val maxRadiusFraction = 0.50f  // expand to edge of view

    private val pulseScales = FloatArray(pulseCount) { 0f }
    private val pulseAlphas = FloatArray(pulseCount) { 0f }

    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(16f)
        color = resolveColor()
    }

    private var animatorSet: AnimatorSet? = null

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density

    private fun resolveColor(): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorPrimary, typedValue, true
            )
        ) {
            typedValue.data
        } else {
            androidx.core.content.ContextCompat.getColor(context, R.color.primary)
        }
    }

    fun startPulse() {
        stopPulse()

        val animators = mutableListOf<ValueAnimator>()

        for (i in 0 until pulseCount) {
            val scaleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = pulseDuration
                startDelay = (i * pulseDuration / pulseCount)
                repeatCount = ValueAnimator.INFINITE
                interpolator = DecelerateInterpolator(1.2f)
                addUpdateListener { anim ->
                    pulseScales[i] = anim.animatedValue as Float
                    invalidate()
                }
            }

            val alphaAnimator = ValueAnimator.ofFloat(0.6f, 0f).apply {
                duration = pulseDuration
                startDelay = (i * pulseDuration / pulseCount)
                repeatCount = ValueAnimator.INFINITE
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener { anim ->
                    pulseAlphas[i] = anim.animatedValue as Float
                }
            }

            animators.add(scaleAnimator)
            animators.add(alphaAnimator)
        }

        animatorSet = AnimatorSet().apply {
            playTogether(animators.toList())
            start()
        }
        visibility = VISIBLE
    }

    fun stopPulse() {
        animatorSet?.cancel()
        animatorSet = null
        for (i in 0 until pulseCount) {
            pulseScales[i] = 0f
            pulseAlphas[i] = 0f
        }
        visibility = INVISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val size = minOf(width, height).toFloat()
        val minRadius = size * minRadiusFraction
        val maxRadius = size * maxRadiusFraction

        for (i in 0 until pulseCount) {
            if (pulseAlphas[i] > 0f) {
                pulsePaint.alpha = (pulseAlphas[i] * 255).toInt()
                val radius = minRadius + pulseScales[i] * (maxRadius - minRadius)
                canvas.drawCircle(cx, cy, radius, pulsePaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPulse()
    }
}
