package com.example.friday_helper.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.example.friday_helper.EntryActivity
import com.example.friday_helper.R
import java.util.Locale

/**
 * Служба в фоне: слушает голос и при произнесении слова для вызова открывает приложение.
 */
class VoiceWakeService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    /** Слово, которое пользователь говорит, чтобы открыть приложение. */
    private var wakeWord: String = ""
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        updateWakeWord()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateWakeWord()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
        // Небольшая задержка, чтобы сервис точно был в foreground — снижает риск блокировки на части устройств
        handler.postDelayed({ startListening() }, 300)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.voice_wake_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, EntryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.voice_wake_notification_title))
            .setContentText(getString(R.string.voice_wake_notification_text, wakeWord))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:voice_wake").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L) // до 10 часов, снимается в onDestroy
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }
    }

    private fun updateWakeWord() {
        wakeWord = SettingsRepository(this).getWakeWord().trim().ifEmpty { DEFAULT_WAKE_WORD }
    }

    private fun startListening() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    handler.postDelayed({ startListening() }, RESTART_DELAY_MS)
                }
                override fun onResults(results: android.os.Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (list != null && list.any { matchesWakeWord(it) }) {
                        launchMainWithGreeting()
                        return
                    }
                    handler.postDelayed({ startListening() }, RESTART_DELAY_MS)
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (_: SecurityException) {
            handler.postDelayed({ startListening() }, RESTART_DELAY_MS)
        } catch (e: Exception) {
            handler.postDelayed({ startListening() }, RESTART_DELAY_MS)
        }
    }

    private fun matchesWakeWord(phrase: String): Boolean {
        val normalized = phrase.trim().lowercase()
        val word = wakeWord.lowercase()
        if (word.isEmpty()) return false
        return normalized.contains(word) || word.contains(normalized)
    }

    private fun launchMainWithGreeting() {
        val greetingName = SettingsRepository(this).getGreetingName().trim()
        val launch = Intent(this, EntryActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_OPENED_BY_VOICE, true)
            putExtra(EXTRA_GREETING_NAME, greetingName)
        }
        startActivity(launch)
        handler.postDelayed({ startListening() }, RESTART_DELAY_MS)
    }

    override fun onDestroy() {
        releaseWakeLock()
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPENED_BY_VOICE = "opened_by_voice"
        const val EXTRA_GREETING_NAME = "greeting_name"
        private const val CHANNEL_ID = "voice_wake"
        private const val NOTIFICATION_ID = 9001
        private const val RESTART_DELAY_MS = 1500L
        private const val DEFAULT_WAKE_WORD = "октопус"

        fun updateRunningState(context: android.content.Context, shouldRun: Boolean) {
            val intent = Intent(context, VoiceWakeService::class.java)
            if (shouldRun) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
