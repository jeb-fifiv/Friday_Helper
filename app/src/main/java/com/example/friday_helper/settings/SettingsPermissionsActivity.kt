package com.example.friday_helper.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.friday_helper.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsPermissionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_permissions)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val root = findViewById<android.view.View>(R.id.rootPermissions)
        val switchLocation: SwitchMaterial = findViewById(R.id.switchLocation)
        val switchClock: SwitchMaterial = findViewById(R.id.switchClock)
        val switchMicrophone: SwitchMaterial = findViewById(R.id.switchMicrophone)
        val switchContacts: SwitchMaterial = findViewById(R.id.switchContacts)

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
            switchLocation.setTextColor(txtColor)
            findViewById<android.widget.TextView>(R.id.locationHint).setTextColor(txtColor)
            switchClock.setTextColor(txtColor)
            findViewById<android.widget.TextView>(R.id.clockHint).setTextColor(txtColor)
            switchMicrophone.setTextColor(txtColor)
            findViewById<android.widget.TextView>(R.id.microphoneHint).setTextColor(txtColor)
            switchContacts.setTextColor(txtColor)
            findViewById<android.widget.TextView>(R.id.contactsHint).setTextColor(txtColor)
        }

        val repo = SettingsRepository(this)
        switchLocation.isChecked = repo.isLocationEnabled()
        val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Доступ к геолокации отклонён", Toast.LENGTH_SHORT).show()
                switchLocation.isChecked = false
                repo.setLocationEnabled(false)
            } else {
                repo.setLocationEnabled(true)
            }
        }
        switchLocation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                permissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                repo.setLocationEnabled(false)
            }
        }

        switchClock.isChecked = repo.isClockEnabled()
        switchClock.setOnCheckedChangeListener { _, isChecked ->
            repo.setClockEnabled(isChecked)
        }

        switchMicrophone.isChecked = repo.isMicrophoneEnabled()
        val microphoneLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.toast_microphone_denied, Toast.LENGTH_SHORT).show()
                switchMicrophone.isChecked = false
                repo.setMicrophoneEnabled(false)
            } else {
                repo.setMicrophoneEnabled(true)
                VoiceWakeService.updateRunningState(this, true)
            }
        }
        switchMicrophone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    repo.setMicrophoneEnabled(true)
                    VoiceWakeService.updateRunningState(this, true)
                } else {
                    microphoneLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            } else {
                repo.setMicrophoneEnabled(false)
                VoiceWakeService.updateRunningState(this, false)
            }
        }

        switchContacts.isChecked = repo.isContactsEnabled()
        val contactsLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.toast_contacts_denied, Toast.LENGTH_SHORT).show()
                switchContacts.isChecked = false
                repo.setContactsEnabled(false)
            } else {
                repo.setContactsEnabled(true)
            }
        }
        switchContacts.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    repo.setContactsEnabled(true)
                } else {
                    contactsLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                }
            } else {
                repo.setContactsEnabled(false)
            }
        }
    }
}
