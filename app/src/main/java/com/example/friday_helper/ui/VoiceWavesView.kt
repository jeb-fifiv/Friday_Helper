package com.example.friday_helper.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnEnd
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class VoiceWavesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        // Гарантируем отрисовку поверх и корректную анимацию
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var waveColor: Int = 0xFF3F51B5.toInt()
    private var progress: Float = 0f
    private var animator: ValueAnimator? = null
    private var continuous: Boolean = false
    private var originX: Float? = null
    private var originY: Float? = null
    private var startRadiusPx: Float = 0f
    private var radiusMultiplier: Float = 2f

    fun setWaveColor(color: Int) {
        waveColor = (color or 0xFF000000.toInt()) // ensure opaque base, we control alpha per wave
        invalidate()
    }

    fun playBurst(durationMs: Long = 900) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                progress = 0f
                invalidate()
            }
        }
        animator?.start()
    }

    fun startContinuous(periodMs: Long = 800) {
        continuous = true
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = periodMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
        }
        animator?.start()
    }

    fun stopContinuous() {
        continuous = false
        animator?.cancel()
        progress = 0f
        invalidate()
    }

    fun setOriginFrom(anchor: View) {
        val waveLoc = IntArray(2)
        val anchorLoc = IntArray(2)
        getLocationInWindow(waveLoc)
        anchor.getLocationInWindow(anchorLoc)
        originX = (anchorLoc[0] - waveLoc[0]) + anchor.width / 2f
        originY = (anchorLoc[1] - waveLoc[1]) + anchor.height / 2f
        startRadiusPx = maxOf(anchor.width, anchor.height) / 2f
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (progress <= 0f && !continuous) return
        val cx = originX ?: (width / 2f)
        val cy = originY ?: (height / 2f)
        // По требованию: радиус анимации = 2 * радиус кнопки (если задана origin)
        val maxR = if (originX != null && originY != null && startRadiusPx > 0f) {
            startRadiusPx * radiusMultiplier
        } else {
            // fallback
            (min(width, height) * 0.9f)
        }

        val baseAlpha = 240
        if (continuous) {
            // Режим Б: один набор, непрерывно расширяется и затухает, потом перезапускается
            val p = progress.coerceIn(0f, 1f)
            val r = startRadiusPx + (maxR - startRadiusPx) * p
            val alpha = (baseAlpha * (1f - p)).toInt().coerceIn(0, 255)
            paint.color = (waveColor and 0x00FFFFFF) or (alpha shl 24)
            drawRadialSine(canvas, cx, cy, r, 12, 14f * (1 - p))
            canvas.drawCircle(cx, cy, r, paint)
        } else {
            // Короткий всплеск: несколько колец
            val waves = 7
            for (i in 0 until waves) {
                val localP = (progress - i * 0.1f).coerceIn(0f, 1f)
                if (localP == 0f) continue
                val r = startRadiusPx + (maxR - startRadiusPx) * localP
                val alpha = (baseAlpha * (1f - localP)).toInt().coerceIn(0, 255)
                paint.color = (waveColor and 0x00FFFFFF) or (alpha shl 24)
                drawRadialSine(canvas, cx, cy, r, 12, 12f * (1 - localP))
                canvas.drawCircle(cx, cy, r, paint)
            }
        }
    }

    private fun drawRadialSine(canvas: Canvas, cx: Float, cy: Float, radius: Float, lobes: Int, amplitude: Float) {
        val path = Path()
        val steps = 360
        var started = false
        for (deg in 0..steps) {
            val rad = deg * PI / 180.0
            val mod = (sin(lobes * rad) * amplitude).toFloat()
            val r = radius + mod
            val x = cx + r * cos(rad).toFloat()
            val y = cy + r * sin(rad).toFloat()
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

