package com.example.friday_helper.settings

import android.os.Bundle
import android.widget.EditText
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.friday_helper.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsExtraActivity : AppCompatActivity() {

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var switchRates: SwitchMaterial
    private lateinit var switchCrypto: SwitchMaterial
    private lateinit var openaiKeyInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_extra)

        settingsRepo = SettingsRepository(this)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val root = findViewById<android.view.View>(R.id.rootExtra)
        switchRates = findViewById(R.id.switchRates)
        switchCrypto = findViewById(R.id.switchCrypto)
        openaiKeyInput = findViewById(R.id.openaiKeyInput)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        toolbar.title = ""
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // Load saved state
        switchRates.isChecked = settingsRepo.isRatesEnabled()
        switchCrypto.isChecked = settingsRepo.isCryptoEnabled()
        openaiKeyInput.setText(settingsRepo.getOpenAiApiKey())

        switchRates.setOnCheckedChangeListener { _, isChecked ->
            settingsRepo.setRatesEnabled(isChecked)
        }
        switchCrypto.setOnCheckedChangeListener { _, isChecked ->
            settingsRepo.setCryptoEnabled(isChecked)
        }

        ThemeManager.init(this)
        ThemeManager.theme.observe(this) { config ->
            config?.let { cfg ->
                ThemeApplier.applyToToolbar(toolbar, cfg)
                ThemeApplier.applyBackground(root, cfg)
                ThemeApplier.applyFont(root, this, cfg.fontResId)
                val txtColor = cfg.accentColor
                findViewById<TextView>(R.id.tvTitle).setTextColor(txtColor)
                switchRates.setTextColor(txtColor)
                switchCrypto.setTextColor(txtColor)
                findViewById<TextView>(R.id.ratesHint).setTextColor(txtColor)
                findViewById<TextView>(R.id.cryptoHint).setTextColor(txtColor)
                findViewById<TextView>(R.id.openaiKeyLabel).setTextColor(txtColor)
                openaiKeyInput.setTextColor(txtColor)
                openaiKeyInput.setHintTextColor(android.content.res.ColorStateList.valueOf(txtColor))
                val surface = ThemeApplier.surfaceColorFor(cfg.backgroundColor)
                ThemeApplier.styleRoundedBackground(openaiKeyInput.background, surface, cfg.accentColor)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        settingsRepo.setOpenAiApiKey(openaiKeyInput.text?.toString()?.trim().orEmpty())
    }
}
