package com.example.friday_helper.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import com.example.friday_helper.R
import com.example.friday_helper.security.SecurityRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsSecurityActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_security)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val root = findViewById<android.view.View>(R.id.rootSecurity)
        val switchPin: SwitchMaterial = findViewById(R.id.switchPin)
        val switchBiometric: SwitchMaterial = findViewById(R.id.switchBiometric)
        val switchNotesProtection: SwitchMaterial = findViewById(R.id.switchNotesProtection)
        val switchNotesEncryption: SwitchMaterial = findViewById(R.id.switchNotesEncryption)
        val switchGuest: SwitchMaterial = findViewById(R.id.switchGuest)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.title = ""
        toolbar.title = ""
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        ThemeManager.init(this)
        ThemeManager.theme.observe(this) { cfg ->
            ThemeApplier.applyToToolbar(toolbar, cfg)
            ThemeApplier.applyBackground(root, cfg)
            ThemeApplier.applyFont(root, this, cfg.fontResId)
            val txtColor = cfg.accentColor
            findViewById<android.widget.TextView>(R.id.tvTitle).setTextColor(txtColor)
            switchPin.setTextColor(txtColor)
            findViewById<android.widget.TextView>(R.id.pinHint).setTextColor(txtColor)
            switchBiometric.setTextColor(txtColor)
            findViewById<android.widget.TextView>(R.id.biometricHint).setTextColor(txtColor)
            switchNotesProtection.setTextColor(txtColor)
            findViewById<android.widget.TextView>(R.id.notesProtectionHint).setTextColor(txtColor)
            switchNotesEncryption.setTextColor(txtColor)
            findViewById<android.widget.TextView>(R.id.notesEncryptionHint).setTextColor(txtColor)
            switchGuest.setTextColor(txtColor)
            findViewById<android.widget.TextView>(R.id.guestHint).setTextColor(txtColor)
        }

        val sec = SecurityRepository(this)

        fun syncBiometricEnabledState() {
            val pinOn = switchPin.isChecked && sec.hasPin()
            switchBiometric.isEnabled = pinOn
            findViewById<android.widget.TextView>(R.id.biometricHint).alpha = if (pinOn) 0.8f else 0.4f
        }

        switchPin.isChecked = sec.isPinEnabled()
        switchBiometric.isChecked = sec.isBiometricEnabled()
        switchNotesProtection.isChecked = sec.isNotesProtectionEnabled()
        switchNotesEncryption.isChecked = sec.isNotesEncryptionEnabled()
        switchGuest.isChecked = sec.isGuestModeEnabled()
        syncBiometricEnabledState()

        switchPin.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                promptSetPin(
                    onSaved = {
                        sec.setPinEnabled(true)
                        switchPin.isChecked = true
                        syncBiometricEnabledState()
                    },
                    onCancelled = {
                        sec.setPinEnabled(false)
                        switchPin.isChecked = false
                        syncBiometricEnabledState()
                    }
                )
            } else {
                sec.clearPin()
                switchBiometric.isChecked = false
                syncBiometricEnabledState()
            }
        }

        switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!sec.hasPin()) {
                    Toast.makeText(this, "Сначала задайте PIN-код", Toast.LENGTH_SHORT).show()
                    switchBiometric.isChecked = false
                    return@setOnCheckedChangeListener
                }
                val bm = BiometricManager.from(this)
                val can = bm.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                if (can != BiometricManager.BIOMETRIC_SUCCESS) {
                    Toast.makeText(this, "Отпечаток недоступен. Добавьте отпечаток в настройках телефона.", Toast.LENGTH_LONG).show()
                    switchBiometric.isChecked = false
                    sec.setBiometricEnabled(false)
                } else {
                    sec.setBiometricEnabled(true)
                }
            } else {
                sec.setBiometricEnabled(false)
            }
        }

        switchNotesProtection.setOnCheckedChangeListener { _, isChecked ->
            sec.setNotesProtectionEnabled(isChecked)
        }
        switchNotesEncryption.setOnCheckedChangeListener { _, isChecked ->
            sec.setNotesEncryptionEnabled(isChecked)
            Toast.makeText(this, "Шифрование применится при следующем открытии заметок", Toast.LENGTH_SHORT).show()
        }
        switchGuest.setOnCheckedChangeListener { _, isChecked ->
            sec.setGuestModeEnabled(isChecked)
        }
    }

    private fun promptSetPin(onSaved: () -> Unit, onCancelled: () -> Unit) {
        val first = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN (4-12 цифр)"
        }
        val second = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Повторите PIN"
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(first)
            addView(android.widget.Space(this@SettingsSecurityActivity).apply { minimumHeight = (8 * resources.displayMetrics.density).toInt() })
            addView(second)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Установить PIN")
            .setView(box)
            .setPositiveButton("Сохранить") { _, _ ->
                val p1 = first.text?.toString().orEmpty().trim()
                val p2 = second.text?.toString().orEmpty().trim()
                if (p1.length !in 4..12 || !p1.all { it.isDigit() }) {
                    Toast.makeText(this, "PIN должен быть 4-12 цифр", Toast.LENGTH_SHORT).show()
                    onCancelled()
                    return@setPositiveButton
                }
                if (p1 != p2) {
                    Toast.makeText(this, "PIN не совпадает", Toast.LENGTH_SHORT).show()
                    onCancelled()
                    return@setPositiveButton
                }
                val sec = SecurityRepository(this)
                sec.setPin(p1)
                onSaved()
            }
            .setNegativeButton("Отмена") { _, _ ->
                onCancelled()
            }
            .setOnCancelListener { onCancelled() }
            .show()
    }
}
