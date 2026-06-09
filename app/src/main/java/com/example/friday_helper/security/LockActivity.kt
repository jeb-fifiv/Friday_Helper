package com.example.friday_helper.security

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.friday_helper.MainActivity
import com.example.friday_helper.R
import com.example.friday_helper.databinding.ActivityLockBinding
import com.example.friday_helper.settings.SettingsRepository
import com.example.friday_helper.settings.VoiceWakeService
import com.example.friday_helper.settings.ThemeApplier
import com.example.friday_helper.settings.ThemeManager
import com.example.friday_helper.tasks.TasksDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private lateinit var sec: SecurityRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sec = SecurityRepository(this)

        binding.btnUnlock.setOnClickListener { unlockByPin() }
        binding.btnBiometric.setOnClickListener { unlockByBiometric() }
        binding.btnGuest.setOnClickListener {
            SecuritySession.enterGuest()
            goMain()
        }
        binding.btnForgot.setOnClickListener { startPasswordRecoveryFlow() }

        // show/hide guest button
        binding.btnGuest.visibility = if (sec.isGuestModeEnabled()) android.view.View.VISIBLE else android.view.View.GONE

        // show/hide biometric button
        binding.btnBiometric.visibility = if (sec.isBiometricEnabled()) android.view.View.VISIBLE else android.view.View.GONE

        applyTheme()
    }

    private fun applyTheme() {
        ThemeManager.init(this)
        ThemeManager.theme.observe(this) { cfg ->
            ThemeApplier.applyBackground(binding.rootLock, cfg)
            ThemeApplier.applyFont(binding.rootLock, this, cfg.fontResId)
            val txt = cfg.accentColor
            val surface = ThemeApplier.surfaceColorFor(cfg.backgroundColor)

            binding.tvTitle.setTextColor(txt)
            binding.tvHint.setTextColor(txt)

            // input
            ThemeApplier.styleTextInput(binding.pinLayout, cfg)
            binding.etPin.setTextColor(txt)

            // buttons
            listOf(binding.btnUnlock, binding.btnBiometric, binding.btnGuest, binding.btnForgot).forEach { b ->
                val d = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 12f * resources.displayMetrics.density
                    setColor(surface)
                    val px = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
                    setStroke(px, cfg.accentColor)
                }
                b.background = d
                b.setTextColor(txt)
                b.backgroundTintList = null
            }
        }
    }

    private fun unlockByPin() {
        val pin = binding.etPin.text?.toString().orEmpty()
        if (sec.verifyPin(pin)) {
            SecuritySession.unlockUser()
            goMain()
        } else {
            Toast.makeText(this, "Неверный PIN", Toast.LENGTH_SHORT).show()
            binding.etPin.text?.clear()
        }
    }

    private fun unlockByBiometric() {
        if (!sec.isBiometricEnabled()) return

        authenticateWithBiometrics(
            title = "Вход",
            subtitle = "Подтвердите отпечаток пальца",
            onSuccess = {
                SecuritySession.unlockUser()
                goMain()
            }
        )
    }

    private fun startPasswordRecoveryFlow() {
        if (isBiometricAvailableOnDevice()) {
            authenticateWithBiometrics(
                title = getString(R.string.lock_bio_reset_title),
                subtitle = getString(R.string.lock_bio_reset_subtitle),
                onSuccess = {
                    sec.clearPin()
                    SecuritySession.unlockUser()
                    Toast.makeText(this, "PIN сброшен. Установите новый в Настройках.", Toast.LENGTH_LONG).show()
                    goMain()
                }
            )
            return
        }
        showResetWarningStep1()
    }

    private fun isBiometricAvailableOnDevice(): Boolean {
        val bm = BiometricManager.from(this)
        val can = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return can == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun authenticateWithBiometrics(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit
    ) {
        if (!isBiometricAvailableOnDevice()) {
            Toast.makeText(this, "Отпечаток недоступен. Включите его в настройках телефона.", Toast.LENGTH_LONG).show()
            runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Отмена")
            .build()
        prompt.authenticate(info)
    }

    private fun showResetWarningStep1() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.lock_reset_title))
            .setMessage(getString(R.string.lock_reset_warning_1))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_ok)) { _, _ -> showResetWarningStep2() }
            .show()
    }

    private fun showResetWarningStep2() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.lock_reset_title))
            .setMessage(getString(R.string.lock_reset_warning_2))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.lock_reset_start)) { _, _ -> performFullSecurityReset() }
            .show()
    }

    private fun performFullSecurityReset() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                clearAllProtectedData()
            }
            Toast.makeText(this@LockActivity, getString(R.string.lock_reset_done), Toast.LENGTH_LONG).show()
            SecuritySession.unlockUser()
            goMain()
        }
    }

    private fun clearAllProtectedData() {
        // PIN / biometric gate
        sec.clearPin()
        sec.setBiometricEnabled(false)
        sec.setNotesEncryptionEnabled(false)
        sec.setNotesProtectionEnabled(false)

        // Notes and folders (plain + encrypted stores)
        getSharedPreferences("notes_data", MODE_PRIVATE).edit().clear().apply()
        runCatching {
            SecurePrefs.encrypted(this, "notes_data_secure").edit().clear().apply()
        }

        // Tasks + subtasks
        runCatching {
            TasksDatabase.getInstance(this).clearAllTables()
        }

        // App-level permissions/features toggles reset
        val settingsRepo = SettingsRepository(this)
        settingsRepo.setLocationEnabled(false)
        settingsRepo.setClockEnabled(false)
        settingsRepo.setMicrophoneEnabled(false)
        settingsRepo.setContactsEnabled(false)
        settingsRepo.setRatesEnabled(false)
        settingsRepo.setCryptoEnabled(false)
        settingsRepo.setDirectCallMode(false)

        // Ask Android to revoke runtime permissions after process kill (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val method = packageManager.javaClass.methods.firstOrNull { it.name == "revokeSelfPermissionsOnKill" }
                if (method != null) {
                    method.invoke(
                        packageManager,
                        setOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    )
                }
            }
        }
    }

    private fun goMain() {
        val i = Intent(this, MainActivity::class.java)
        intent?.extras?.let { ex -> if (ex.getBoolean(VoiceWakeService.EXTRA_OPENED_BY_VOICE, false)) i.putExtras(ex) }
        startActivity(i)
        finish()
    }
}

