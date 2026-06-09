package com.example.friday_helper

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.friday_helper.security.SecurityRepository
import com.example.friday_helper.security.SecuritySession
import com.example.friday_helper.settings.VoiceWakeService
import com.example.friday_helper.security.LockActivity

/**
 * Точка входа. Решает, показывать ли экран блокировки.
 * Пробрасывает extras от голосового открытия (VoiceWakeService) в MainActivity или LockActivity.
 */
class EntryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sec = SecurityRepository(this)
        val voiceExtras = intent?.extras?.takeIf { it?.getBoolean(VoiceWakeService.EXTRA_OPENED_BY_VOICE, false) == true }
            ?.let { b -> Intent().apply { putExtras(b) } }

        fun addVoiceExtras(i: Intent): Intent {
            voiceExtras?.extras?.let { i.putExtras(it) }
            return i
        }

        if (SecuritySession.isUnlocked || SecuritySession.isGuest) {
            startActivity(addVoiceExtras(Intent(this, MainActivity::class.java)))
            finish()
            return
        }

        if (sec.isPinEnabled()) {
            startActivity(addVoiceExtras(Intent(this, LockActivity::class.java)))
            finish()
            return
        }

        startActivity(addVoiceExtras(Intent(this, MainActivity::class.java)))
        finish()
    }
}

