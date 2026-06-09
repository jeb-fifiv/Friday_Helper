package com.example.friday_helper

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 * Второй экран приветствия: градиент, белый круг с octopus_12893132.gif,
 * надпись «HOCTOPUS» по буквам чуть выше гифки.
 */
class WelcomeOctopusActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome_octopus)

        ThemeManager.init(this)
        val repo = SettingsRepository(this)
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
        setupGifAndTitle()
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
        findViewById<ImageView>(R.id.welcomeOctopusGradient).setImageDrawable(gradient)
    }

    private fun setupGifAndTitle() {
        val titleView = findViewById<TextView>(R.id.welcomeOctopusTitle)
        val fullText = "HOCTOPUS"
        val delayMs = 120L
        val stayAfterMs = 1800L
        val handler = Handler(Looper.getMainLooper())
        val dimOverlay = findViewById<View>(R.id.welcomeOctopusDimOverlay)
        var index = 0

        val run = object : Runnable {
            override fun run() {
                if (index <= fullText.length) {
                    titleView.text = fullText.substring(0, index)
                    index++
                    handler.postDelayed(this, delayMs)
                } else {
                    handler.postDelayed({
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
                                startActivity(Intent(this@WelcomeOctopusActivity, EntryActivity::class.java).apply {
                                    intent?.extras?.let { putExtras(it) }
                                })
                                finish()
                                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            }
                            .start()
                    }, stayAfterMs)
                }
            }
        }
        handler.postDelayed(run, 400)

        Glide.with(this)
            .asGif()
            .load(R.drawable.octopus_12893132)
            .into(object : CustomTarget<GifDrawable>() {
                override fun onResourceReady(resource: GifDrawable, transition: Transition<in GifDrawable>?) {
                    findViewById<ImageView>(R.id.welcomeOctopusGif).setImageDrawable(resource)
                    resource.setLoopCount(1)
                    resource.start()
                }
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
            })
    }
}
