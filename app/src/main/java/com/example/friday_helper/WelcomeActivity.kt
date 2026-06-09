package com.example.friday_helper

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.friday_helper.settings.SettingsRepository
import com.example.friday_helper.settings.ThemeManager

/**
 * Экран приветствия при запуске: градиент от цвета темы по краям к белому в центре,
 * белый круг с GIF-персонажем, слева «S.G.A.», справа «Created by» / «Cursor».
 */
class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SettingsRepository(this)
        if (!repo.isWelcomeAnimationsEnabled()) {
            startActivity(Intent(this, EntryActivity::class.java).apply {
                intent?.extras?.let { putExtras(it) }
            })
            finish()
            return
        }
        setContentView(R.layout.activity_welcome)

        ThemeManager.init(this)
        val themeColor = ThemeManager.theme.value?.backgroundColor
            ?: repo.load().backgroundColor

        window.statusBarColor = themeColor
        window.navigationBarColor = themeColor
        val r = Color.red(themeColor) / 255.0
        val g = Color.green(themeColor) / 255.0
        val b = Color.blue(themeColor) / 255.0
        val darkBg = (0.299 * r + 0.587 * g + 0.114 * b) < 0.5
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkBg
            isAppearanceLightNavigationBars = !darkBg
        }

        setupGradient(themeColor)
        setupGifAndSequence()
    }

    private fun setupGradient(themeColor: Int) {
        val dm = resources.displayMetrics
        val minSide = minOf(dm.widthPixels, dm.heightPixels).toFloat()
        val radiusPx = 0.45f * minSide
        val gradient = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.5f, 0.5f)
            setGradientRadius(radiusPx)
            setColors(intArrayOf(Color.WHITE, themeColor))
        }
        findViewById<ImageView>(R.id.welcomeGradient).setImageDrawable(gradient)
    }

    private fun setupGifAndSequence() {
        val gifView = findViewById<ImageView>(R.id.welcomeGif)
        val gsaText = findViewById<TextView>(R.id.welcomeGsa)
        val createdByBlock = findViewById<View>(R.id.welcomeCreatedByBlock)

        gsaText.animate().alpha(1f).setDuration(520).setStartDelay(936).start()

        val dimOverlay = findViewById<View>(R.id.welcomeDimOverlay)
        val proceedToOctopus: () -> Unit = {
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
            dimOverlay.animate()
                .alpha(1f)
                .setDuration(450)
                .withEndAction {
                    startActivity(Intent(this, WelcomeOctopusActivity::class.java).apply {
                        intent?.extras?.let { putExtras(it) }
                    })
                    finish()
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
                .start()
        }

        Glide.with(this)
            .asGif()
            .load(R.drawable.cyborg_11260851)
            .into(object : CustomTarget<GifDrawable>() {
                override fun onResourceReady(resource: GifDrawable, transition: Transition<in GifDrawable>?) {
                    gifView.setImageDrawable(resource)
                    resource.setLoopCount(1)
                    resource.start()
                    createdByBlock.postDelayed({
                        createdByBlock.animate().alpha(1f).setDuration(500).start()
                        createdByBlock.postDelayed({ proceedToOctopus() }, 2200)
                    }, 1320)
                }
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
            })
    }
}
