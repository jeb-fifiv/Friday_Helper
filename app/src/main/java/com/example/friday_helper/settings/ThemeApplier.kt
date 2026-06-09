package com.example.friday_helper.settings

import android.content.Context
import android.content.res.Resources
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.google.android.material.textfield.TextInputLayout
import com.example.friday_helper.R

object ThemeApplier {

    private const val TAG = "ThemeApplier"

    fun applyBackground(root: View, color: Int) {
        applyBackground(root, color, 0)
    }

    /** Применить фон: color + модификатор (0=сплошной, 1–4=разделение по горизонтали/диагонали). */
    fun applyBackground(root: View, color: Int, modifier: Int) {
        root.background = backgroundDrawableFor(color, modifier, null)
    }

    fun applyBackground(root: View, config: ThemeManager.ThemeConfig) {
        root.background = backgroundDrawableFor(config.backgroundColor, config.backgroundModifier, config.backgroundEmoji)
    }

    /** Создать Drawable для фона по цвету и модификатору. */
    fun backgroundDrawableFor(backgroundColor: Int, modifier: Int, emoji: String? = null): Drawable {
        val mod = modifier.coerceIn(0, 4)
        if (mod == 0) return ColorDrawable(backgroundColor)
        val darker = darkenColor(backgroundColor, 0.75f)
        val orientation = when (mod) {
            1 -> GradientDrawable.Orientation.TOP_BOTTOM
            2 -> GradientDrawable.Orientation.TOP_BOTTOM
            3 -> GradientDrawable.Orientation.TR_BL
            4 -> GradientDrawable.Orientation.TR_BL
            else -> GradientDrawable.Orientation.TOP_BOTTOM
        }
        val colors = when (mod) {
            1, 3 -> intArrayOf(backgroundColor, darker)
            2, 4 -> intArrayOf(darker, backgroundColor)
            else -> intArrayOf(backgroundColor, darker)
        }
        val gradient = GradientDrawable(orientation, colors)
        val emojiText = emoji?.trim().orEmpty()
        if (emojiText.isBlank()) return gradient
        return EmojiGradientDrawable(gradient, mod, emojiText, darker)
    }

    fun darkenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), r, g, b)
    }

    /** Светлый фон с лёгким оттенком цвета темы для экрана задач. */
    fun lightTintBackground(themeColor: Int, mixRatio: Float = 0.08f): Int {
        val base = Color.parseColor("#FFFFFF")
        val r = (Color.red(base) * (1 - mixRatio) + Color.red(themeColor) * mixRatio).toInt().coerceIn(0, 255)
        val g = (Color.green(base) * (1 - mixRatio) + Color.green(themeColor) * mixRatio).toInt().coerceIn(0, 255)
        val b = (Color.blue(base) * (1 - mixRatio) + Color.blue(themeColor) * mixRatio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    /**
     * Цвет «среза» фона по модификатору: для верхней полосы экрана (тулбар) и для нижней (панель ввода).
     * Так верхний и нижний тулбары визуально продолжают общий градиент.
     */
    fun topBarColorFor(backgroundColor: Int, modifier: Int): Int {
        val mod = modifier.coerceIn(0, 4)
        if (mod == 0) return backgroundColor
        val darker = darkenColor(backgroundColor, 0.75f)
        return when (mod) {
            1, 3 -> backgroundColor  // сверху основной цвет
            2, 4 -> darker          // сверху затемнённый
            else -> backgroundColor
        }
    }

    fun bottomBarColorFor(backgroundColor: Int, modifier: Int): Int {
        val mod = modifier.coerceIn(0, 4)
        if (mod == 0) return backgroundColor
        val darker = darkenColor(backgroundColor, 0.75f)
        return when (mod) {
            1, 3 -> darker         // снизу затемнённый
            2, 4 -> backgroundColor // снизу основной
            else -> darker
        }
    }

    /** Жёстко задаёт кнопке «назад» чёрный круг и иконку back_1, чтобы тема не перекрашивала в белый квадрат. */
    fun applyBackButton(button: ImageButton?) {
        button ?: return
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.BLACK)
        }
        button.setImageResource(R.drawable.back_1)
        button.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        button.backgroundTintList = null
        button.imageTintList = null
    }

    fun applyWarmButtonsAccent(accentColor: Int, buttons: List<Button>) {
        buttons.forEach { btn ->
            tintDrawableBackgroundStroke(btn.background, accentColor)
            btn.setTextColor(accentColor)
        }
    }

    fun tintDrawableBackgroundStroke(background: Drawable?, strokeColor: Int) {
        val bg = background
        if (bg is GradientDrawable) {
            bg.mutate()
            val px = (3 * android.content.res.Resources.getSystem().displayMetrics.density).toInt().coerceAtLeast(2)
            bg.setStroke(px, strokeColor)
        }
    }

    fun applyFont(root: View, context: Context, fontResId: Int) {
        val typeface = try {
            ResourcesCompat.getFont(context, fontResId)
        } catch (e: Resources.NotFoundException) {
            Log.w(TAG, "Font resource not found: $fontResId. Falling back to system font.", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load font: $fontResId. Falling back to system font.", e)
            null
        }
        // Use fallback system typeface mapped by family if downloadable font is unavailable
        val resolved = typeface ?: fallbackTypefaceFor(fontResId)
        applyFontRecursive(root, resolved)
    }

    private fun applyFontRecursive(view: View, typeface: Typeface?) {
        if (view is TextView && typeface != null) {
            view.typeface = typeface
        }
        if (view is ViewGroup) {
            view.children.forEach { child -> applyFontRecursive(child, typeface) }
        }
    }

    private fun fallbackTypefaceFor(fontResId: Int): Typeface {
        return when (fontResId) {
            R.font.merriweather -> Typeface.SERIF
            R.font.opensans -> Typeface.SANS_SERIF
            else -> Typeface.SANS_SERIF
        }
    }

    fun applyToToolbar(toolbar: Toolbar, config: ThemeManager.ThemeConfig) {
        toolbar.setBackgroundColor(Color.TRANSPARENT)
        val titleColor = if (isDark(config.backgroundColor)) Color.WHITE else Color.BLACK
        toolbar.setTitleTextColor(titleColor)
        toolbar.navigationIcon?.let { icon ->
            DrawableCompat.setTint(icon.mutate(), config.accentColor)
            toolbar.navigationIcon = icon
        }
    }

    // Theming helpers
    fun surfaceColorFor(backgroundColor: Int): Int {
        return if (isDark(backgroundColor)) {
            blend(backgroundColor, Color.WHITE, 0.08f) // lighten a bit on dark bg
        } else {
            blend(backgroundColor, Color.BLACK, 0.06f) // darken a bit on light bg
        }
    }

    fun styleRoundedBackground(drawable: Drawable?, fillColor: Int, strokeColor: Int) {
        if (drawable is GradientDrawable) {
            drawable.mutate()
            drawable.setColor(fillColor)
            val px = (2 * Resources.getSystem().displayMetrics.density).toInt().coerceAtLeast(2)
            drawable.setStroke(px, strokeColor)
            drawable.cornerRadius = 12f * Resources.getSystem().displayMetrics.density
        }
    }

    fun styleTextInput(inputLayout: TextInputLayout, config: ThemeManager.ThemeConfig) {
        val surface = surfaceColorFor(config.backgroundColor)
        inputLayout.setBoxBackgroundColorStateList(ColorStateList.valueOf(surface))
        inputLayout.boxStrokeColor = config.accentColor
        inputLayout.hintTextColor = ColorStateList.valueOf(config.accentColor)
    }

    fun tintImageButtonIcon(imageButton: android.widget.ImageButton, color: Int) {
        imageButton.imageTintList = ColorStateList.valueOf(color)
    }

    fun textColorOnBackground(backgroundColor: Int): Int {
        return if (isDark(backgroundColor)) Color.WHITE else Color.BLACK
    }

    private fun isDark(color: Int): Boolean {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return luminance < 0.5
    }

    private fun blend(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val a = (Color.alpha(c1) * inv + Color.alpha(c2) * ratio).toInt()
        val r = (Color.red(c1) * inv + Color.red(c2) * ratio).toInt()
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).toInt()
        val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }

    private class EmojiGradientDrawable(
        private val base: GradientDrawable,
        private val modifier: Int,
        private val emoji: String,
        darkerColor: Int
    ) : Drawable() {
        private val clipPath = Path()
        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDarkStatic(darkerColor)) Color.argb(130, 255, 255, 255) else Color.argb(120, 0, 0, 0)
            textAlign = Paint.Align.CENTER
        }

        override fun draw(canvas: Canvas) {
            base.bounds = bounds
            base.draw(canvas)
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) return

            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val emojiSize = (minOf(w, h) * 0.11f).coerceIn(18f, 34f)
            textPaint.textSize = emojiSize
            val metrics = textPaint.fontMetrics
            val baselineAdjust = (metrics.ascent + metrics.descent) / 2f

            clipPath.reset()
            when (modifier) {
                1 -> clipPath.addRect(0f, h / 2f, w, h, Path.Direction.CW) // darker in bottom half
                2 -> clipPath.addRect(0f, 0f, w, h / 2f, Path.Direction.CW) // darker in top half
                3 -> { // darker in bottom-left triangle
                    clipPath.moveTo(0f, h)
                    clipPath.lineTo(0f, 0f)
                    clipPath.lineTo(w, h)
                    clipPath.close()
                }
                4 -> { // darker in top-right triangle
                    clipPath.moveTo(0f, 0f)
                    clipPath.lineTo(w, 0f)
                    clipPath.lineTo(w, h)
                    clipPath.close()
                }
                else -> clipPath.addRect(0f, 0f, w, h, Path.Direction.CW)
            }

            canvas.save()
            canvas.clipPath(clipPath)
            val stripeStepX = emojiSize * 2.35f
            val itemStep = emojiSize * 1.65f
            val stripeCount = ((w / stripeStepX).toInt() + 2).coerceAtMost(10)
            val startX = w - emojiSize * 0.55f
            val startY = emojiSize * 0.65f
            repeat(stripeCount) { stripe ->
                var x = startX - stripe * stripeStepX
                var y = startY
                while (x > -emojiSize && y < h + emojiSize) {
                    canvas.drawText(emoji, x, y - baselineAdjust, textPaint)
                    x -= itemStep
                    y += itemStep
                }
            }
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            base.alpha = alpha
            textPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            base.colorFilter = colorFilter
            textPaint.colorFilter = colorFilter
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun isDarkStatic(color: Int): Boolean {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return luminance < 0.5
    }
}

