package com.example.friday_helper.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.friday_helper.R
import com.example.friday_helper.settings.ThemeApplier.textColorOnBackground
import com.google.android.material.appbar.MaterialToolbar

class SettingsActivity : AppCompatActivity() {

    /** Параметр настроек: отображаемое название (по нему идёт поиск) и действие при нажатии. */
    private data class SettingItem(
        val displayName: String,
        val openAction: () -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val root = findViewById<android.view.View>(R.id.rootSettings)
        val searchEdit: EditText = findViewById(R.id.settingsSearch)
        val searchMatchedParam = findViewById<TextView>(R.id.settingsSearchMatchedParam)
        val btnPersonalization: Button = findViewById(R.id.btnPersonalization)
        val btnAppearance: Button = findViewById(R.id.btnAppearance)
        val btnPermissions: Button = findViewById(R.id.btnPermissions)
        val btnSecurity: Button = findViewById(R.id.btnSecurity)
        val btnExtra: Button = findViewById(R.id.btnExtra)
        val guideButton: Button = findViewById(R.id.guideButton)
        val searchResultsContainer = findViewById<LinearLayout>(R.id.settingsSearchResultsContainer)
        val buttonsContainer = findViewById<LinearLayout>(R.id.settingsButtonsContainer)
        val settingsScroll = findViewById<android.widget.ScrollView>(R.id.settingsScroll)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.title = ""
        toolbar.title = ""
        findViewById<View>(R.id.btnBackSettings).setOnClickListener { finish() }

        // Плоский список параметров: поиск по сравнению введённого текста с названием параметра
        val allItems: List<SettingItem> = listOf(
            SettingItem(getString(R.string.btn_personalization)) { startActivity(Intent(this, SettingsPersonalizationActivity::class.java)) },
            SettingItem(getString(R.string.btn_appearance)) { startActivity(Intent(this, SettingsAppearanceActivity::class.java)) },
            SettingItem(getString(R.string.label_greeting_name)) { startActivity(Intent(this, SettingsPersonalizationActivity::class.java)) },
            SettingItem(getString(R.string.label_wake_word)) { startActivity(Intent(this, SettingsPersonalizationActivity::class.java)) },
            SettingItem(getString(R.string.label_font)) { startActivity(Intent(this, SettingsPersonalizationActivity::class.java)) },
            SettingItem(getString(R.string.label_background_color)) { startActivity(Intent(this, SettingsAppearanceActivity::class.java)) },
            SettingItem(getString(R.string.label_background_modifier)) { startActivity(Intent(this, SettingsAppearanceActivity::class.java)) },
            SettingItem(getString(R.string.btn_permissions)) { startActivity(Intent(this, SettingsPermissionsActivity::class.java)) },
            SettingItem(getString(R.string.label_location)) { startActivity(Intent(this, SettingsPermissionsActivity::class.java)) },
            SettingItem(getString(R.string.label_clock)) { startActivity(Intent(this, SettingsPermissionsActivity::class.java)) },
            SettingItem(getString(R.string.label_microphone)) { startActivity(Intent(this, SettingsPermissionsActivity::class.java)) },
            SettingItem(getString(R.string.btn_security)) { startActivity(Intent(this, SettingsSecurityActivity::class.java)) },
            SettingItem(getString(R.string.label_pin)) { startActivity(Intent(this, SettingsSecurityActivity::class.java)) },
            SettingItem(getString(R.string.label_biometric)) { startActivity(Intent(this, SettingsSecurityActivity::class.java)) },
            SettingItem(getString(R.string.label_notes_protection)) { startActivity(Intent(this, SettingsSecurityActivity::class.java)) },
            SettingItem(getString(R.string.label_notes_encryption)) { startActivity(Intent(this, SettingsSecurityActivity::class.java)) },
            SettingItem(getString(R.string.label_guest_mode)) { startActivity(Intent(this, SettingsSecurityActivity::class.java)) },
            SettingItem(getString(R.string.btn_extra)) { startActivity(Intent(this, SettingsExtraActivity::class.java)) },
            SettingItem(getString(R.string.label_rates)) { startActivity(Intent(this, SettingsExtraActivity::class.java)) },
            SettingItem(getString(R.string.label_crypto)) { startActivity(Intent(this, SettingsExtraActivity::class.java)) },
            SettingItem(getString(R.string.label_openai_api_key)) { startActivity(Intent(this, SettingsExtraActivity::class.java)) },
            SettingItem(getString(R.string.user_guide)) { startActivity(Intent(this, SettingsGuideActivity::class.java)) }
        )

        ThemeManager.init(this)
        ThemeManager.theme.observe(this) { cfg ->
            ThemeApplier.applyToToolbar(toolbar, cfg)
            ThemeApplier.applyBackground(root, cfg)
            ThemeApplier.applyFont(root, this, cfg.fontResId)
            val txtColor = cfg.accentColor
            val darkBg = txtColor == android.graphics.Color.WHITE
            val surface = ThemeApplier.surfaceColorFor(cfg.backgroundColor)
            val btnFill = if (darkBg) surface else android.graphics.Color.WHITE
            val btnStroke = if (darkBg) cfg.accentColor else android.graphics.Color.BLACK

            findViewById<TextView>(R.id.tvTitleSettings).setTextColor(txtColor)
            ThemeApplier.styleRoundedBackground(searchEdit.background, surface, cfg.accentColor)
            searchEdit.setTextColor(txtColor)
            searchEdit.setHintTextColor(ColorStateList.valueOf(txtColor))
            searchMatchedParam.setTextColor(txtColor)

            listOf(btnPersonalization, btnAppearance, btnPermissions, btnSecurity, btnExtra, guideButton).forEach { btn ->
                ThemeApplier.styleRoundedBackground(btn.background, btnFill, btnStroke)
                btn.setTextColor(if (darkBg) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                btn.backgroundTintList = null
            }
        }

        fun filterAndShowResults(query: String) {
            val q = query.trim().lowercase()
            searchMatchedParam.visibility = android.view.View.GONE

            if (q.isEmpty()) {
                searchResultsContainer.visibility = android.view.View.GONE
                searchResultsContainer.removeAllViews()
                buttonsContainer.visibility = android.view.View.VISIBLE
                return
            }

            // Поиск: введённый текст сравниваем с названием параметра (подстрока в названии)
            val matches = allItems.filter { it.displayName.lowercase().contains(q) }
                .distinctBy { it.displayName } // один параметр — одна строка

            if (matches.isEmpty()) {
                searchResultsContainer.visibility = android.view.View.VISIBLE
                buttonsContainer.visibility = android.view.View.GONE
                searchResultsContainer.removeAllViews()
                val noResults = TextView(this).apply {
                    text = getString(R.string.settings_search_no_results)
                    setPadding(0, (32 * resources.displayMetrics.density).toInt(), 0, 0)
                    setTextColor(ThemeManager.theme.value?.accentColor ?: android.graphics.Color.parseColor("#212121"))
                }
                searchResultsContainer.addView(noResults)
            } else {
                searchResultsContainer.visibility = android.view.View.VISIBLE
                buttonsContainer.visibility = android.view.View.GONE
                searchResultsContainer.removeAllViews()
                val txtColor = ThemeManager.theme.value?.accentColor ?: android.graphics.Color.parseColor("#212121")
                val darkBg = txtColor == android.graphics.Color.WHITE
                val surface = ThemeApplier.surfaceColorFor(ThemeManager.theme.value?.backgroundColor ?: android.graphics.Color.parseColor("#202020"))
                val btnFill = if (darkBg) surface else android.graphics.Color.WHITE
                val btnStroke = if (darkBg) (ThemeManager.theme.value?.accentColor ?: android.graphics.Color.parseColor("#3F51B5")) else android.graphics.Color.BLACK
                matches.forEach { item ->
                    val btn = Button(this).apply {
                        text = item.displayName
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).toInt()).apply {
                            bottomMargin = (12 * resources.displayMetrics.density).toInt()
                        }
                        setBackgroundResource(R.drawable.bg_button_warm_gray)
                        setTextColor(if (darkBg) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                        setOnClickListener { item.openAction() }
                    }
                    ThemeApplier.styleRoundedBackground(btn.background, btnFill, btnStroke)
                    btn.backgroundTintList = null
                    searchResultsContainer.addView(btn)
                }
            }
            settingsScroll.post { settingsScroll.scrollTo(0, 0) }
        }

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAndShowResults(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {
                filterAndShowResults(s?.toString().orEmpty())
            }
        })

        btnPersonalization.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsPersonalizationActivity::class.java))
        }
        btnAppearance.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsAppearanceActivity::class.java))
        }
        btnPermissions.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsPermissionsActivity::class.java))
        }
        btnSecurity.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsSecurityActivity::class.java))
        }
        btnExtra.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsExtraActivity::class.java))
        }
        guideButton.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsGuideActivity::class.java))
        }
    }
}
