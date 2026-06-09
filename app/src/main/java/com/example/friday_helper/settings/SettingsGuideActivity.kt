package com.example.friday_helper.settings

import android.os.Bundle
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.view.View
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.example.friday_helper.R
import com.google.android.material.appbar.MaterialToolbar

class SettingsGuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_user_guide)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.title = ""
        toolbar.title = ""
        findViewById<View>(R.id.btnBackGuide).setOnClickListener { finish() }

        val tvGuideContent = findViewById<android.widget.TextView>(R.id.tvGuideContent)
        val scroll = findViewById<NestedScrollView>(R.id.scroll)
        tvGuideContent.text = formatGuideContent(getString(R.string.user_guide_text), scroll, tvGuideContent)
        tvGuideContent.movementMethod = LinkMovementMethod.getInstance()
        tvGuideContent.highlightColor = android.graphics.Color.TRANSPARENT

        ThemeManager.init(this)
        ThemeManager.theme.observe(this) { cfg ->
            ThemeApplier.applyToToolbar(toolbar, cfg)
            ThemeApplier.applyBackground(findViewById(R.id.rootGuide), cfg)
            ThemeApplier.applyFont(findViewById(R.id.rootGuide), this, cfg.fontResId)
            val txt = cfg.accentColor
            tvGuideContent.setTextColor(txt)
            findViewById<android.widget.TextView>(R.id.tvTitleGuide).setTextColor(txt)
        }
    }

    /** Форматирует текст руководства и делает пункты оглавления кликабельными. */
    private fun formatGuideContent(
        raw: String,
        scroll: NestedScrollView,
        textView: android.widget.TextView
    ): Spanned {
        val result = SpannableStringBuilder(raw)
        val lines = raw.split("\n")
        var cursor = 0
        lines.forEach { line ->
            val start = cursor
            val end = cursor + line.length
            if (line.endsWith(":") || line.startsWith("Глава ")) {
                result.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            // Кликабельные пункты оглавления: "1) ...", "2) ..."
            if (Regex("^\\d+\\)\\s+.+$").matches(line)) {
                val chapterNumber = line.substringBefore(")").trim().toIntOrNull()
                if (chapterNumber != null) {
                    result.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            val chapterTitle = "Глава $chapterNumber."
                            val targetIndex = raw.indexOf(chapterTitle)
                            if (targetIndex >= 0) {
                                textView.post {
                                    val layout = textView.layout ?: return@post
                                    val lineNo = layout.getLineForOffset(targetIndex)
                                    val y = layout.getLineTop(lineNo)
                                    scroll.smoothScrollTo(0, y)
                                }
                            }
                        }
                    }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            cursor = end + 1 // + '\n'
        }
        return result
    }
}

