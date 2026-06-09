package com.example.friday_helper.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.friday_helper.R
import com.example.friday_helper.settings.ThemeApplier.textColorOnBackground
import com.google.android.material.appbar.MaterialToolbar
import android.graphics.Color

class SettingsPersonalizationActivity : AppCompatActivity() {

    private val fonts: List<FontOption> by lazy {
        listOf(
            FontOption("Open Sans", R.font.opensans),
            FontOption("Merriweather", R.font.merriweather),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_personalization)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val root = findViewById<android.view.View>(R.id.rootPersonalization)
        val fontDropdown: AutoCompleteTextView = findViewById(R.id.fontDropdown)
        val applyButton: Button = findViewById(R.id.applyButton)
        val greetingNameInput = findViewById<android.widget.EditText>(R.id.greetingNameInput)
        val greetingNameLabel = findViewById<android.widget.TextView>(R.id.greetingNameLabel)
        val wakeWordInput = findViewById<android.widget.EditText>(R.id.wakeWordInput)
        val wakeWordLabel = findViewById<android.widget.TextView>(R.id.wakeWordLabel)
        val fontLabel = findViewById<android.widget.TextView>(R.id.fontLabel)
        val rbCenterCircleMenu: RadioButton = findViewById(R.id.rbCenterCircleMenu)
        val rbCenterWaves: RadioButton = findViewById(R.id.rbCenterWaves)
        val switchWelcomeAnimations: androidx.appcompat.widget.SwitchCompat = findViewById(R.id.switchWelcomeAnimations)
        val rbVoiceEditThenSend: RadioButton = findViewById(R.id.rbVoiceEditThenSend)
        val rbVoiceSendImmediately: RadioButton = findViewById(R.id.rbVoiceSendImmediately)
        val rbCallModeContacts: RadioButton = findViewById(R.id.rbCallModeContacts)
        val rbCallModeDirect: RadioButton = findViewById(R.id.rbCallModeDirect)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.title = ""
        toolbar.title = ""
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fonts.map { it.name })
        fontDropdown.setAdapter(adapter)
        fontDropdown.inputType = InputType.TYPE_NULL
        fontDropdown.keyListener = null
        fontDropdown.isCursorVisible = false
        fontDropdown.setOnClickListener { fontDropdown.showDropDown() }
        fontDropdown.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) fontDropdown.showDropDown()
        }

        ThemeManager.init(this)
        val repo = SettingsRepository(this)
        greetingNameInput.setText(repo.getGreetingName())
        wakeWordInput.setText(repo.getWakeWord())
        val current = ThemeManager.theme.value ?: repo.load()
        val idx = fonts.indexOfFirst { it.resId == current.fontResId }
        if (idx >= 0) fontDropdown.setText(fonts[idx].name, false)
        rbCenterCircleMenu.isChecked = repo.isUseCenterCircleMenu()
        rbCenterWaves.isChecked = !repo.isUseCenterCircleMenu()
        switchWelcomeAnimations.isChecked = repo.isWelcomeAnimationsEnabled()
        rbVoiceSendImmediately.isChecked = repo.isVoiceSendImmediately()
        rbVoiceEditThenSend.isChecked = !repo.isVoiceSendImmediately()
        rbCallModeDirect.isChecked = repo.isDirectCallMode()
        rbCallModeContacts.isChecked = !repo.isDirectCallMode()

        ThemeManager.theme.observe(this) { cfg ->
            ThemeApplier.applyToToolbar(toolbar, cfg)
            ThemeApplier.applyBackground(root, cfg)
            ThemeApplier.applyFont(root, this, cfg.fontResId)
            val surface = ThemeApplier.surfaceColorFor(cfg.backgroundColor)
            val txtColor = cfg.accentColor
            val darkBg = txtColor == android.graphics.Color.WHITE
            val btnFill = if (darkBg) surface else android.graphics.Color.WHITE
            val btnStroke = if (darkBg) cfg.accentColor else android.graphics.Color.BLACK

            ThemeApplier.styleRoundedBackground(applyButton.background, btnFill, btnStroke)
            applyButton.setTextColor(if (darkBg) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
            applyButton.backgroundTintList = null

            ThemeApplier.styleRoundedBackground(greetingNameInput.background, surface, cfg.accentColor)
            greetingNameInput.setTextColor(txtColor)
            greetingNameInput.setHintTextColor(ColorStateList.valueOf(txtColor))
            greetingNameLabel.setTextColor(txtColor)
            ThemeApplier.styleRoundedBackground(wakeWordInput.background, surface, cfg.accentColor)
            wakeWordInput.setTextColor(txtColor)
            wakeWordInput.setHintTextColor(ColorStateList.valueOf(txtColor))
            wakeWordLabel.setTextColor(txtColor)

            ThemeApplier.styleRoundedBackground(fontDropdown.background, surface, cfg.accentColor)
            fontDropdown.setTextColor(txtColor)
            fontDropdown.setHintTextColor(ColorStateList.valueOf(txtColor))

            val tint = ColorStateList.valueOf(cfg.accentColor)
            fontLabel.setTextColor(txtColor)
            findViewById<android.widget.TextView>(R.id.tvTitle).setTextColor(txtColor)
            findViewById<android.widget.TextView>(R.id.centerButtonLabel).setTextColor(txtColor)
            rbCenterCircleMenu.setTextColor(txtColor)
            rbCenterWaves.setTextColor(txtColor)
            rbCenterCircleMenu.buttonTintList = tint
            rbCenterWaves.buttonTintList = tint
            findViewById<android.widget.TextView>(R.id.welcomeAnimationsLabel).setTextColor(txtColor)
            switchWelcomeAnimations.thumbTintList = ColorStateList.valueOf(if (darkBg) surface else cfg.accentColor)
            switchWelcomeAnimations.trackTintList = ColorStateList.valueOf(android.graphics.Color.argb(120, Color.red(txtColor), Color.green(txtColor), Color.blue(txtColor)))
            findViewById<android.widget.TextView>(R.id.voiceInputFormatLabel).setTextColor(txtColor)
            rbVoiceEditThenSend.setTextColor(txtColor)
            rbVoiceSendImmediately.setTextColor(txtColor)
            rbVoiceEditThenSend.buttonTintList = tint
            rbVoiceSendImmediately.buttonTintList = tint
            findViewById<android.widget.TextView>(R.id.callModeLabel).setTextColor(txtColor)
            rbCallModeContacts.setTextColor(txtColor)
            rbCallModeDirect.setTextColor(txtColor)
            rbCallModeContacts.buttonTintList = tint
            rbCallModeDirect.buttonTintList = tint
        }

        applyButton.setOnClickListener {
            val greetingName = greetingNameInput.text?.toString()?.trim().orEmpty()
            val wakeWord = wakeWordInput.text?.toString()?.trim().orEmpty()
            val wasCenterCircleMenu = repo.isUseCenterCircleMenu()
            val newCenterCircleMenu = rbCenterCircleMenu.isChecked
            repo.setGreetingName(greetingName)
            repo.setWakeWord(wakeWord)
            repo.setUseCenterCircleMenu(newCenterCircleMenu)
            repo.setWelcomeAnimationsEnabled(switchWelcomeAnimations.isChecked)
            repo.setVoiceSendImmediately(rbVoiceSendImmediately.isChecked)
            repo.setDirectCallMode(rbCallModeDirect.isChecked)
            if (repo.isMicrophoneEnabled()) {
                VoiceWakeService.updateRunningState(this, true)
            } else {
                VoiceWakeService.updateRunningState(this, false)
            }

            val fontName = fontDropdown.text?.toString()?.trim().orEmpty()
            val font = fonts.find { it.name.equals(fontName, ignoreCase = true) } ?: fonts.first()
            val type = ThemeManager.ThemeType.fromInt(
                getSharedPreferences("user_theme", MODE_PRIVATE).getInt("theme_type", 0)
            )
            if (type == ThemeManager.ThemeType.CUSTOM) {
                ThemeManager.updateThemeCustom(
                    repo.getCustomBackground(),
                    repo.getCustomAccent(),
                    repo.getCustomThemeName(),
                    repo.getCustomBackgroundEmoji(),
                    font.resId
                )
            } else {
                ThemeManager.updateTheme(type, font.resId)
            }
            val centerButtonChanged = (wasCenterCircleMenu != newCenterCircleMenu)
            if (centerButtonChanged) {
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.msg_apply_after_restart))
                    .setCancelable(true)
                    .setPositiveButton(R.string.action_restart_app) { _, _ ->
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finishAffinity()
                    }
                    .show()
            } else {
                Toast.makeText(this, R.string.action_apply, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    data class FontOption(val name: String, val resId: Int)
}
