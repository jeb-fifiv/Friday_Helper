package com.example.friday_helper.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Восемь щупалец из центра. При удержании кнопки — выезжают и хаотично двигаются.
 */
class TentaclesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE6000000.toInt()
    }
    private val path = Path()
    private var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val baseAngles = floatArrayOf(
        0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f
    ).map { Math.toRadians(it.toDouble()).toFloat() }

    private val angleWiggle = FloatArray(8)
    private val lengthWiggle = FloatArray(8)
    private var bobPhase = 0f

    private var animator: ValueAnimator? = null
    private val retractRunnable = Runnable { runRetract() }
    private val handler = Handler(Looper.getMainLooper())
    private var wiggleRunnable: Runnable? = null
    private var isHolding = false

    /** Радиус уменьшён на 30% (0.85 * 0.7 ≈ 0.595). */
    private fun maxLength(w: Float, h: Float) = (minOf(w, h) / 2f) * 0.85f * 0.7f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val maxLen = maxLength(w, h)
        val len = maxLen * progress
        val baseWidth = 14f * resources.displayMetrics.density
        val tipWidth = 4f * resources.displayMetrics.density
        val bobAmp = 6f * resources.displayMetrics.density
        val cy = h / 2f + sin(bobPhase) * bobAmp

        baseAngles.forEachIndexed { i, baseAngle ->
            val angle = baseAngle + angleWiggle[i]
            val tentacleLen = len * (1f + lengthWiggle[i])
            val cosA = cos(angle)
            val sinA = sin(angle)
            val dx = tentacleLen * cosA
            val dy = tentacleLen * sinA
            val perpX = -sinA
            val perpY = cosA
            val halfBase = baseWidth / 2f
            val halfTip = tipWidth / 2f
            path.reset()
            path.moveTo(cx + perpX * halfBase, cy + perpY * halfBase)
            path.lineTo(cx + dx + perpX * halfTip, cy + dy + perpY * halfTip)
            path.lineTo(cx + dx - perpX * halfTip, cy + dy - perpY * halfTip)
            path.lineTo(cx - perpX * halfBase, cy - perpY * halfBase)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    /** Удержание: выезд щупалец и хаотичное движение, пока держат. */
    fun startHold() {
        removeCallbacks(retractRunnable)
        isHolding = true
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, 1f).apply {
            duration = 280
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float }
            start()
        }
        startWiggle()
    }

    /** Отпустили кнопку: прекращаем движение и убираем щупальца. */
    fun endHold() {
        isHolding = false
        stopWiggle()
        removeCallbacks(retractRunnable)
        runRetract()
    }

    private fun startWiggle() {
        stopWiggle()
        wiggleRunnable = object : Runnable {
            override fun run() {
                if (!isHolding) return
                for (i in 0..7) {
                    angleWiggle[i] = (angleWiggle[i] + (Math.random().toFloat() - 0.5f) * 0.22f).coerceIn(-0.35f, 0.35f)
                    lengthWiggle[i] = (lengthWiggle[i] + (Math.random().toFloat() - 0.5f) * 0.18f).coerceIn(-0.28f, 0.28f)
                }
                bobPhase += 0.2f
                invalidate()
                handler.postDelayed(this, 70)
            }
        }
        handler.post(wiggleRunnable!!)
    }

    private fun stopWiggle() {
        wiggleRunnable?.let { handler.removeCallbacks(it) }
        wiggleRunnable = null
        for (i in 0..7) {
            angleWiggle[i] = 0f
            lengthWiggle[i] = 0f
        }
    }

    private fun runRetract() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, 0f).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float }
            start()
        }
    }
}
