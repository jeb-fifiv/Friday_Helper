package com.example.friday_helper

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.res.ColorStateList
import com.google.android.material.appbar.MaterialToolbar
import com.example.friday_helper.settings.SettingsActivity
import com.example.friday_helper.settings.SettingsRepository
import com.example.friday_helper.settings.ThemeApplier
import com.example.friday_helper.settings.ThemeManager
import com.example.friday_helper.settings.VoiceWakeService
import com.example.friday_helper.ui.TentaclesView
import com.example.friday_helper.ui.VoiceWavesView
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.location.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.widget.TextView
import com.example.friday_helper.weather.WeatherClient
import com.example.friday_helper.rates.FiatRatesActivity
import com.example.friday_helper.security.SecuritySession
import com.example.friday_helper.chat.AppDatabase
import com.example.friday_helper.chat.ChatRepository
import com.example.friday_helper.chat.MessageAdapter
import com.example.friday_helper.chat.MessageEntity
import com.example.friday_helper.chat.MESSAGE_STATUS_LOCAL
import com.example.friday_helper.chat.MESSAGE_STATUS_PROCESSING
import com.example.friday_helper.chat.OpenAiClient
import com.ramotion.circlemenu.CircleMenuView
import android.widget.FrameLayout
import android.util.TypedValue
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.util.Log
import android.provider.ContactsContract
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import com.example.friday_helper.tasks.TasksDatabase
import com.example.friday_helper.tasks.TasksRepository
import com.example.friday_helper.tasks.TaskEntity
import com.example.friday_helper.search.GlobalSearchActivity
import com.example.friday_helper.tasks.TaskNotificationScheduler
import com.example.friday_helper.tasks.REMINDER_NONE
import com.example.friday_helper.tasks.REMINDER_ONCE
import com.example.friday_helper.tasks.REMINDER_DAILY
import com.example.friday_helper.alarm.AlarmScheduler
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPENED_BY_VOICE = "opened_by_voice"
        const val EXTRA_GREETING_NAME = "greeting_name"
        const val EXTRA_OPEN_CHAT_ID = "open_chat_id"

        private const val FRIDAY_ACTION_PREFIX = "FRIDAY_ACTION:"
        private const val TAG = "MainActivity"
        private val SYSTEM_PROMPT = """
Ты — голосовой ассистент. Отвечай кратко по-русски.
Если пользователь просит создать задачу/напоминание — в конце ответа с новой строки выведи ровно одну строку:
FRIDAY_ACTION: {"action":"create_task","title":"название","date":"today"|"tomorrow"|"YYYY-MM-DD","time":"HH:mm"|null,"reminder":"none"|"once"|"daily"}
Если просит будильник/разбудить в время — выведи: FRIDAY_ACTION: {"action":"set_alarm","time":"HH:mm","label":"подпись"}
Если просит позвонить человеку из контактов — выведи: FRIDAY_ACTION: {"action":"call_contact","name":"имя контакта"}
time всегда в формате HH:mm. В остальных случаях FRIDAY_ACTION не выводи.
""".trimIndent()
    }

    private var originalStatusBarColor: Int? = null
    private var originalNavigationBarColor: Int? = null
    private var originalLightBars: Boolean? = null
    private lateinit var weatherContainer: View
    private lateinit var weatherText: TextView
    private lateinit var settingsRepo: SettingsRepository
    private var currentChatId: Long? = null
    private val messageList = mutableListOf<MessageEntity>()
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageAdapter: MessageAdapter
    private var centerButtonWaves: ImageButton? = null
    private var useCircleMenuCurrent: Boolean? = null
    private var voiceRecording = false
    private var voiceResetTriggered = false
    private var voiceSpeechRecognizer: SpeechRecognizer? = null
    private val voiceHandler = Handler(Looper.getMainLooper())
    private var pendingContactToCall: String? = null
    private var pendingDirectCallPhone: String? = null
    private val voiceResetSlideThresholdPx: Float by lazy {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 72f, resources.displayMetrics)
    }
    private val locationPermissionLauncher by lazy {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                fetchAndShowWeather()
            } else {
                weatherContainer.visibility = View.GONE
            }
        }
    }
    private val contactsPermissionLauncher by lazy {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val pendingName = pendingContactToCall
                pendingContactToCall = null
                if (!pendingName.isNullOrBlank()) {
                    val nameToCall = pendingName
                    lifecycleScope.launch(Dispatchers.Main) {
                        val ok = callContactByName(nameToCall)
                        if (!ok) {
                            Toast.makeText(this@MainActivity, R.string.toast_contact_not_found, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                pendingContactToCall = null
                Toast.makeText(this@MainActivity, R.string.toast_contacts_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }
    private val callPhonePermissionLauncher by lazy {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val phone = pendingDirectCallPhone
            pendingDirectCallPhone = null
            if (granted && !phone.isNullOrBlank()) {
                runOnUiThread { dialPhoneNumber(phone, direct = true) }
            } else if (!granted) {
                Toast.makeText(this@MainActivity, R.string.toast_call_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        // Важно: инициализируем launchers в onCreate (до состояния STARTED/RESUMED),
        // иначе registerForActivityResult может упасть на поздней регистрации.
        locationPermissionLauncher
        contactsPermissionLauncher
        callPhonePermissionLauncher

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val btnBurger: ImageButton = findViewById(R.id.btnBurger)
        val voiceWaves: VoiceWavesView = findViewById(R.id.voiceWaves)
        val btnSend: ImageButton = findViewById(R.id.btnSend)
        val btnMic: ImageButton = findViewById(R.id.btnMic)
        val root: View = findViewById(R.id.root)
        val chatInputLayout: com.google.android.material.textfield.TextInputLayout = findViewById(R.id.chatInputLayout)
        val chatEditText: com.google.android.material.textfield.TextInputEditText = findViewById(R.id.chatEditText)
        val centerContainer: View = findViewById(R.id.centerButtonContainer)
        val greetingText: TextView = findViewById(R.id.greetingText)
        val chatPanel: View = findViewById(R.id.chatPanel)
        val chatRecycler: androidx.recyclerview.widget.RecyclerView = findViewById(R.id.chatRecycler)
        val btnNewChat: View = findViewById(R.id.btnNewChat)
        val btnNewChatInMenu: Button = findViewById(R.id.btnNewChatInMenu)
        chatRepository = ChatRepository(AppDatabase.getInstance(this))
        messageAdapter = MessageAdapter(messageList, Color.BLACK, resources.getColor(com.example.friday_helper.R.color.warm_gray, null))
        chatRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this).apply { stackFromEnd = true }
        chatRecycler.adapter = messageAdapter

        fun copyMessage(message: MessageEntity) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
            Toast.makeText(this, "Сообщение скопировано", Toast.LENGTH_SHORT).show()
        }

        fun deleteMessage(message: MessageEntity) {
            val msgId = message.id
            if (msgId > 0) {
                lifecycleScope.launch {
                    chatRepository.deleteMessage(msgId)
                }
            }
            val index = messageList.indexOfFirst { it.id == message.id }
            if (index >= 0) {
                messageList.removeAt(index)
                messageAdapter.submitList(messageList.toList())
            }
            messageAdapter.clearSelection()
        }

        fun promptForRestart(message: MessageEntity, position: Int): String? {
            if (message.role == "user") return message.content
            for (i in (position - 1) downTo 0) {
                val candidate = messageList.getOrNull(i)
                if (candidate?.role == "user") return candidate.content
            }
            return null
        }

        fun restartByMessage(message: MessageEntity, position: Int) {
            val prompt = promptForRestart(message, position)
            if (prompt.isNullOrBlank()) {
                Toast.makeText(this, "Не найден исходный промпт", Toast.LENGTH_SHORT).show()
                return
            }
            messageAdapter.clearSelection()
            lifecycleScope.launch {
                sendMessage(prompt, centerContainer, voiceWaves, chatPanel, chatRecycler, btnNewChat, greetingText)
            }
        }

        messageAdapter.onMessageActionClick = { msg, pos, action ->
            when (action) {
                MessageAdapter.MessageAction.RESTART -> restartByMessage(msg, pos)
                MessageAdapter.MessageAction.COPY -> {
                    copyMessage(msg)
                    messageAdapter.clearSelection()
                }
                MessageAdapter.MessageAction.DELETE -> deleteMessage(msg)
            }
        }
        val openChatId = intent.getLongExtra(EXTRA_OPEN_CHAT_ID, -1L)
        if (openChatId > 0) currentChatId = openChatId
        lifecycleScope.launch {
            currentChatId?.let { id ->
                val list = withContext(Dispatchers.IO) { chatRepository.getMessages(id) }
                messageList.clear()
                messageList.addAll(list)
                messageAdapter.submitList(messageList.toList())
            }
            updateChatUi(centerContainer, voiceWaves, chatPanel, btnNewChat, greetingText)
        }
        settingsRepo = SettingsRepository(this)
        setupCenterButtonByPreference(centerContainer, voiceWaves)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_global_search) {
                startActivity(Intent(this, GlobalSearchActivity::class.java))
                true
            } else {
                false
            }
        }

        // Обеспечим нужный Z-порядок: волны над контентом, но под кнопкой
        voiceWaves.bringToFront()
        centerContainer.bringToFront()

        checkShowGreetingFromVoice(intent, greetingText)

        val menuPanel: View = findViewById(R.id.menuPanel)
        val menuIcon: android.widget.ImageView = findViewById(R.id.menuIcon)
        val menuTitle: android.widget.TextView = findViewById(R.id.menuTitle)
        val menuOverlay: View = findViewById(R.id.menuOverlay)
        weatherContainer = findViewById(R.id.weatherContainer)
        weatherText = findViewById(R.id.weatherText)
        // set header icon after views are available
        runCatching {
            val octopusId = resources.getIdentifier("octopus_icon", "drawable", packageName)
            if (octopusId != 0) {
                menuIcon.setImageResource(octopusId)
                menuIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }
        }
        menuPanel.isClickable = true
        menuPanel.setOnClickListener { /* consume */ }
        menuOverlay.setOnClickListener {
            if (menuOverlay.visibility == View.VISIBLE) {
                hideMenuOverlay(menuOverlay, menuPanel)
            }
        }
        val btnSettings: Button = findViewById(R.id.btnSettings)
        val btnChats: Button = findViewById(R.id.btnChats)
        val btnTasks: Button = findViewById(R.id.btnTasks)
        val btnAlarm: Button = findViewById(R.id.btnAlarm)
        val btnCalls: Button = findViewById(R.id.btnCalls)
        val btnNotes: Button = findViewById(R.id.btnNotes)
        val btnRates: Button = findViewById(R.id.btnRates)
        val btnGlobalSearch: Button = findViewById(R.id.btnGlobalSearch)

        // Burger toggles compact menu panel
        btnBurger.setOnClickListener {
            if (menuOverlay.visibility == View.VISIBLE) {
                hideMenuOverlay(menuOverlay, menuPanel)
            } else {
                // ensure panel width (на 5% шире прежнего 50%)
                menuOverlay.post {
                    val half = (root.width * 0.55f).toInt()
                    val lp = menuPanel.layoutParams
                    if (lp.width != half) {
                        lp.width = half
                        menuPanel.layoutParams = lp
                    }
                    // compute panel color a bit lighter than background
                    val cfg = ThemeManager.theme.value
                    val panelColor = ThemeApplier.surfaceColorFor(cfg?.backgroundColor ?: Color.parseColor("#202020"))
                    showMenuOverlay(menuOverlay, menuPanel, panelColor)
                    // Try update weather when opening menu
                    updateWeatherVisibilityAndMaybeRequest()
                }
            }
        }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            hideMenuOverlay(menuOverlay, menuPanel)
        }
        btnChats.setOnClickListener {
            startActivity(Intent(this, com.example.friday_helper.chat.ChatsActivity::class.java))
                hideMenuOverlay(menuOverlay, menuPanel)
            }
        btnNotes.setOnClickListener {
            if (SecuritySession.isGuest) {
                Toast.makeText(this, "Гостевой режим: заметки недоступны", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, com.example.friday_helper.notes.NotesActivity::class.java))
            }
            hideMenuOverlay(menuOverlay, menuPanel)
        }
        btnRates.setOnClickListener {
            if (!settingsRepo.isRatesEnabled()) {
                Toast.makeText(this, R.string.toast_rates_disabled, Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, FiatRatesActivity::class.java))
            }
            hideMenuOverlay(menuOverlay, menuPanel)
        }
        btnAlarm.setOnClickListener {
            if (!settingsRepo.isClockEnabled()) {
                Toast.makeText(this, R.string.toast_clock_disabled, Toast.LENGTH_SHORT).show()
            } else {
                openClock()
            }
            hideMenuOverlay(menuOverlay, menuPanel)
        }
        btnGlobalSearch.setOnClickListener {
            startActivity(Intent(this, GlobalSearchActivity::class.java))
            hideMenuOverlay(menuOverlay, menuPanel)
        }
        btnTasks.setOnClickListener {
            startActivity(Intent(this, com.example.friday_helper.tasks.TasksActivity::class.java))
            hideMenuOverlay(menuOverlay, menuPanel)
        }
        btnCalls.setOnClickListener {
            startActivity(Intent(this, com.example.friday_helper.contacts.ContactsActivity::class.java))
            hideMenuOverlay(menuOverlay, menuPanel)
        }
        // Поведение подсказки: скрываем при фокусе/вводе, возвращаем при потере фокуса если поле пустое
        val originalHint = getString(R.string.chat_hint)
        chatEditText.setOnFocusChangeListener { v, hasFocus ->
            val et = v as com.google.android.material.textfield.TextInputEditText
            if (hasFocus) {
                et.hint = ""
            } else {
                if (et.text?.isEmpty() != false) et.hint = originalHint
            }
        }
        chatEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) {
                    if (chatEditText.hint?.isNotEmpty() == true) chatEditText.hint = ""
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s.isNullOrEmpty() && !chatEditText.hasFocus()) {
                    chatEditText.hint = originalHint
                }
            }
        })
        // Клик по пустому месту экрана (вне поля) возвращает подсказку, если текст пустой
        root.setOnTouchListener { _, _ ->
            if (chatEditText.hasFocus()) {
                chatEditText.clearFocus()
            }
            false
        }

        btnSend.setOnClickListener {
            val text = chatEditText.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return@setOnClickListener
            chatEditText.setText("")
            if (text == "/F1") {
                startActivity(Intent(this, com.example.friday_helper.settings.SettingsGuideActivity::class.java))
                return@setOnClickListener
            }
            lifecycleScope.launch {
                sendMessage(text, centerContainer, voiceWaves, chatPanel, chatRecycler, btnNewChat, greetingText)
            }
        }

        var micLongPressRunnable: Runnable? = null
        var micDownX = 0f
        btnMic.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    micDownX = event.rawX
                    micLongPressRunnable?.let { voiceHandler.removeCallbacks(it) }
                    micLongPressRunnable = Runnable {
                        startVoiceInput(voiceWaves)
                        micLongPressRunnable = null
                    }
                    voiceHandler.postDelayed(micLongPressRunnable!!, 550)
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (voiceRecording && !voiceResetTriggered && micDownX - event.rawX >= voiceResetSlideThresholdPx) {
                        showVoiceResetAndCancel()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL,
                android.view.MotionEvent.ACTION_OUTSIDE -> {
                    micLongPressRunnable?.let { voiceHandler.removeCallbacks(it) }
                    micLongPressRunnable = null
                    if (voiceRecording) {
                        runCatching { voiceSpeechRecognizer?.stopListening() }
                    }
                    true
                }
                else -> false
            }
        }

        val startNewChat: () -> Unit = {
            lifecycleScope.launch {
                currentChatId?.let { id -> chatRepository.setChatTitleFromFirstMessage(id) }
                currentChatId = null
                messageList.clear()
                messageAdapter.submitList(emptyList())
                updateChatUi(centerContainer, voiceWaves, chatPanel, btnNewChat, greetingText)
            }
        }
        btnNewChat.setOnClickListener { startNewChat() }
        btnNewChatInMenu.setOnClickListener {
            startNewChat()
            hideMenuOverlay(menuOverlay, menuPanel)
        }

        // Initial theming and observe changes
        ThemeManager.init(this)
        val coordinatorMain: View = findViewById(R.id.coordinatorMain)
        ThemeManager.theme.observe(this) { config ->
            ThemeApplier.applyToToolbar(toolbar, config)
            ThemeApplier.applyBackground(coordinatorMain, config)
            // Верхний и нижний тулбары — «срез» модификатора по их положению на экране
            toolbar.background = android.graphics.drawable.ColorDrawable(
                ThemeApplier.topBarColorFor(config.backgroundColor, config.backgroundModifier)
            )
            findViewById<View>(R.id.chatContainer).setBackgroundColor(
                ThemeApplier.bottomBarColorFor(config.backgroundColor, config.backgroundModifier)
            )
            ThemeApplier.applyFont(root, this, config.fontResId)
            // Ярко-белые волны
            voiceWaves.setWaveColor(Color.WHITE)
            // Buttons and inputs get surface fill derived from theme background
            val surface = ThemeApplier.surfaceColorFor(config.backgroundColor)
            val textOnBg = config.accentColor
            // Send button: белый фон, обводка по теме
            ThemeApplier.styleRoundedBackground(btnSend.background, Color.WHITE, config.accentColor)
            // Mic button: всегда белый фон, обводка под акцент темы
            ThemeApplier.styleRoundedBackground(btnMic.background, Color.WHITE, config.accentColor)
            // Цвет шторки: немного светлее фона основного окна
            val panelColor = ThemeApplier.surfaceColorFor(config.backgroundColor)
            menuPanel.setBackgroundColor(panelColor)

            // Панель чата — мягкий оттенок фона темы
            ThemeApplier.styleRoundedBackground(chatPanel.background, surface, surface)
            // Style compact menu buttons (без обводки)
            listOf(btnNewChatInMenu, btnGlobalSearch, btnTasks, btnAlarm, btnCalls, btnNotes, btnRates, btnChats, btnSettings).forEach {
                it.setTextColor(textOnBg)
                it.backgroundTintList = null
            }
            menuTitle.setTextColor(textOnBg)
            ThemeApplier.tintDrawableBackgroundStroke(menuIcon.background, config.accentColor)
            // also ensure image buttons have no tint
            btnSend.backgroundTintList = null
            chatEditText.setTextColor(textOnBg)
            messageAdapter.setTheme(config.accentColor, config.backgroundColor)
            ThemeApplier.styleRoundedBackground(btnBurger.background, surface, config.accentColor)
            // Кнопка «+»: фон — мягкий оттенок, иконка поверх
            val btnNewChatBg: View = findViewById(R.id.btnNewChatBackground)
            val btnNewChatIcon: android.widget.ImageView = findViewById(R.id.btnNewChatIcon)
            ThemeApplier.styleRoundedBackground(btnNewChatBg.background, surface, surface)
            btnNewChatIcon.imageTintList = ColorStateList.valueOf(config.accentColor)
            findViewById<View>(R.id.menuDivider).setBackgroundColor(
                Color.argb(80, Color.red(textOnBg), Color.green(textOnBg), Color.blue(textOnBg))
            )
            ThemeApplier.styleTextInput(chatInputLayout, config)
            ThemeApplier.tintImageButtonIcon(btnBurger, config.accentColor)
            toolbar.menu.findItem(R.id.action_global_search)?.let { searchItem ->
                searchItem.icon?.mutate()?.let { drawable ->
                    val wrapped = DrawableCompat.wrap(drawable)
                    DrawableCompat.setTint(wrapped, config.accentColor)
                    searchItem.icon = wrapped
                }
            }
            centerButtonWaves?.background?.let { ThemeApplier.styleRoundedBackground(it, Color.WHITE, Color.BLACK) }

            // Системные полосы в тон верхнего и нижнего «среза» модификатора
            window.statusBarColor = ThemeApplier.topBarColorFor(config.backgroundColor, config.backgroundModifier)
            window.navigationBarColor = ThemeApplier.bottomBarColorFor(config.backgroundColor, config.backgroundModifier)
            val controller = WindowInsetsControllerCompat(window, root)
            // Use luminance heuristic from ThemeApplier
            val darkBg = ThemeApplier.run {
                // replicate isDark check
                val c = config.backgroundColor
                val r = Color.red(c) / 255.0
                val g = Color.green(c) / 255.0
                val b = Color.blue(c) / 255.0
                val luminance = 0.299 * r + 0.587 * g + 0.114 * b
                luminance < 0.5
            }
            controller.isAppearanceLightStatusBars = !darkBg
            controller.isAppearanceLightNavigationBars = !darkBg
            findViewById<TextView>(R.id.greetingText)?.setTextColor(config.accentColor)
            findViewById<TextView>(R.id.voiceTranscriptText)?.setTextColor(config.accentColor)
        }

        fun updateRatesButtonVisibility() {
            val enabled = settingsRepo.isRatesEnabled()
            btnRates.visibility = if (enabled) View.VISIBLE else View.GONE
        }
        updateRatesButtonVisibility()

        if (settingsRepo.getWakeWord().isNotEmpty() &&
            settingsRepo.isMicrophoneEnabled() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            VoiceWakeService.updateRunningState(this, true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val openChatId = intent.getLongExtra(EXTRA_OPEN_CHAT_ID, -1L)
        if (openChatId > 0L) {
            intent.removeExtra(EXTRA_OPEN_CHAT_ID)
            currentChatId = openChatId
            lifecycleScope.launch {
                val list = withContext(Dispatchers.IO) { chatRepository.getMessages(openChatId) }
                messageList.clear()
                messageList.addAll(list)
                messageAdapter.submitList(messageList.toList())
                val centerContainer = findViewById<View>(R.id.centerButtonContainer)
                val voiceWaves = findViewById<View>(R.id.voiceWaves)
                val chatPanel = findViewById<View>(R.id.chatPanel)
                val btnNewChat = findViewById<View>(R.id.btnNewChat)
                val greetingText = findViewById<TextView>(R.id.greetingText)
                val chatRecycler = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.chatRecycler)
                updateChatUi(centerContainer, voiceWaves, chatPanel, btnNewChat, greetingText)
                if (messageList.isNotEmpty()) {
                    chatRecycler.post { chatRecycler.scrollToPosition(messageList.size - 1) }
                }
            }
        }
        findViewById<TextView>(R.id.greetingText)?.let { checkShowGreetingFromVoice(intent, it) }
    }

    override fun onResume() {
        super.onResume()
        useCircleMenuCurrent?.let { current ->
            if (settingsRepo.isUseCenterCircleMenu() != current) {
                val centerContainer = findViewById<View>(R.id.centerButtonContainer)
                val voiceWaves = findViewById<VoiceWavesView>(R.id.voiceWaves)
                setupCenterButtonByPreference(centerContainer, voiceWaves)
            }
        }
        runCatching {
            val btnRates: Button = findViewById(R.id.btnRates)
            val enabled = settingsRepo.isRatesEnabled()
            btnRates.visibility = if (enabled) View.VISIBLE else View.GONE
        }
    }

    private fun checkShowGreetingFromVoice(intent: Intent?, greetingText: TextView) {
        if (intent?.getBooleanExtra(EXTRA_OPENED_BY_VOICE, false) != true) return
        val name = intent.getStringExtra(EXTRA_GREETING_NAME)?.takeIf { it.isNotBlank() }
            ?: settingsRepo.getGreetingName().takeIf { it.isNotBlank() } ?: return
        val full = getString(R.string.greeting_format, name)
        greetingText.visibility = View.VISIBLE
        greetingText.text = ""
        runTypewriterAnimation(greetingText, full)
    }

    private fun runTypewriterAnimation(textView: TextView, fullText: String) {
        val delayMs = 80L
        val hideAfterMs = 5000L
        var index = 0
        val run = object : Runnable {
            override fun run() {
                if (index <= fullText.length) {
                    textView.text = fullText.substring(0, index)
                    index++
                    textView.postDelayed(this, delayMs)
                } else {
                    textView.postDelayed({ textView.visibility = View.GONE }, hideAfterMs)
                }
            }
        }
        textView.postDelayed(run, 100L)
    }

    private fun openClock() {
        // Try multiple safe options; guard against SecurityException
        val attempts = listOfNotNull(
            Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS),
            packageManager.getLaunchIntentForPackage("com.google.android.deskclock"),
            packageManager.getLaunchIntentForPackage("com.android.deskclock")
        )
        for (intent in attempts) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return
                }
            } catch (_: SecurityException) {
                // try next option
            }
        }
        Toast.makeText(this, R.string.toast_clock_not_found, Toast.LENGTH_SHORT).show()
    }

    /** Пытается поставить системный будильник через приложение "Часы". */
    private fun setSystemAlarm(hour: Int, minute: Int, label: String): Boolean {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val safeLabel = label.ifBlank { getString(R.string.menu_alarm) }

        val intents = listOf(
            Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, safeHour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, safeMinute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, safeLabel)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, safeHour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, safeMinute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, safeLabel)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in intents) {
            try {
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return true
                }
            } catch (_: SecurityException) {
                // пробуем следующий вариант
            } catch (_: Exception) {
                // пробуем следующий вариант
            }
        }
        return false
    }

    private fun findContactPhoneByName(nameQuery: String): Pair<String, String>? {
        if (nameQuery.isBlank()) return null
        if (!settingsRepo.isContactsEnabled()) return null
        val aliasesMap = settingsRepo.getContactAliasesMap()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val normalizedQuery = normalizeContactText(nameQuery)
        val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        var best: Pair<String, String>? = null
        var bestScore = Int.MIN_VALUE

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIdx >= 0 && numIdx >= 0) {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx).orEmpty()
                    val number = cursor.getString(numIdx).orEmpty()
                    if (name.isBlank() || number.isBlank()) continue

                    val normalizedName = normalizeContactText(name)
                    val nameTokens = normalizedName.split(" ").filter { it.isNotBlank() }
                    val aliasKey = SettingsRepository.contactAliasKey(name, number)
                    val aliases = aliasesMap[aliasKey].orEmpty()
                    var score = 0

                    if (normalizedName == normalizedQuery) score += 100
                    if (normalizedName.contains(normalizedQuery)) score += 60
                    if (tokens.all { t -> normalizedName.contains(t) }) score += 40
                    if (tokens.any { t -> nameTokens.any { nt -> nt.startsWith(t.take(3)) } }) score += 20
                    if (tokens.any { t -> normalizedName.contains(stemToken(t)) }) score += 10
                    val aliasHit = aliases.any { alias ->
                        val normalizedAlias = normalizeContactText(alias)
                        val aliasTokens = normalizedAlias.split(" ").filter { it.isNotBlank() }
                        normalizedAlias.contains(normalizedQuery) ||
                            normalizedQuery.contains(normalizedAlias) ||
                            tokens.all { t -> normalizedAlias.contains(t) } ||
                            tokens.any { t -> aliasTokens.any { at -> at.startsWith(t.take(3)) } } ||
                            tokens.any { t -> normalizedAlias.contains(stemToken(t)) } ||
                            aliasTokens.any { at -> tokens.any { t -> stemToken(at) == stemToken(t) } }
                    }
                    if (aliasHit) score += 120

                    if (score > bestScore) {
                        bestScore = score
                        best = name to number
                    }
                }
            }
        }
        return if (bestScore <= 0) null else best
    }

    private fun dialPhoneNumber(phone: String, direct: Boolean = false): Boolean {
        val telUri = Uri.parse("tel:${Uri.encode(phone)}")
        if (direct) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                pendingDirectCallPhone = phone
                callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                return true
            }
            val directIntents = listOf(
                Intent(Intent.ACTION_CALL, telUri),
                Intent(Intent.ACTION_CALL, telUri).setPackage("com.miui.contacts"),
                Intent(Intent.ACTION_CALL, telUri).setPackage("com.android.contacts"),
                Intent(Intent.ACTION_CALL, telUri).setPackage("com.google.android.dialer"),
                Intent(Intent.ACTION_CALL, telUri).setPackage("com.android.dialer")
            )
            directIntents.forEach { intent ->
                try {
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return true
                } catch (_: android.content.ActivityNotFoundException) {
                    // попробуем следующий вариант
                } catch (e: Exception) {
                    Log.w(TAG, "direct call intent failed pkg=${intent.`package`}", e)
                }
            }
            return false
        }
        val intents = mutableListOf<Intent>()
        intents += Intent(Intent.ACTION_DIAL, telUri)
        intents += Intent(Intent.ACTION_VIEW, telUri)
        intents += Intent(Intent.ACTION_DIAL, telUri).setPackage("com.google.android.dialer")
        intents += Intent(Intent.ACTION_DIAL, telUri).setPackage("com.android.dialer")
        intents += Intent(Intent.ACTION_DIAL, telUri).setPackage("com.miui.contacts")

        intents.forEach { intent ->
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return true
            } catch (_: android.content.ActivityNotFoundException) {
                // пробуем следующий вариант
            } catch (e: Exception) {
                Log.w(TAG, "dial intent failed: ${intent.action} pkg=${intent.`package`}", e)
            }
        }
        return false
    }

    private fun normalizeContactText(text: String): String {
        val stopWords = setOf(
            "позвони", "набери", "пожалуйста", "контакту", "контакт", "человеку", "на", "по", "имени", "к", "для", "мне"
        )
        return text
            .lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it !in stopWords }
            .joinToString(" ")
    }

    private fun stemToken(token: String): String {
        if (token.length <= 3) return token
        return token.removeSuffix("е")
            .removeSuffix("у")
            .removeSuffix("ой")
            .removeSuffix("ю")
            .removeSuffix("а")
            .removeSuffix("я")
    }

    private suspend fun callContactByName(name: String): Boolean {
        if (!settingsRepo.isContactsEnabled()) return false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingContactToCall = name
            runOnUiThread {
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
            return true
        }
        val contact = withContext(Dispatchers.IO) { findContactPhoneByName(name) } ?: return false
        withContext(Dispatchers.Main) {
            val opened = dialPhoneNumber(contact.second, direct = settingsRepo.isDirectCallMode())
            if (opened) {
                Toast.makeText(this@MainActivity, "Звонок: ${contact.first}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, R.string.toast_dialer_not_found, Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    private fun setupCenterButtonByPreference(centerContainer: View, voiceWaves: VoiceWavesView) {
        val container = centerContainer as? FrameLayout ?: return
        val tentaclesView = findViewById<TentaclesView>(R.id.tentaclesView)
        container.removeAllViews()
        centerButtonWaves = null
        if (settingsRepo.isUseCenterCircleMenu()) {
            useCircleMenuCurrent = true
            tentaclesView?.visibility = View.GONE
            voiceWaves.visibility = if (messageList.isEmpty()) View.VISIBLE else View.GONE
            setupCircleMenu(centerContainer)
        } else {
            useCircleMenuCurrent = false
            tentaclesView?.visibility = View.GONE
            voiceWaves.visibility = if (messageList.isEmpty()) View.VISIBLE else View.GONE
            setupCenterButtonWaves(centerContainer, voiceWaves)
        }
    }

    /** Круговое меню по центру с иконками icons8: задачи, будильник, звонки, заметки, чаты, настройки. */
    private fun setupCircleMenu(centerContainer: View) {
        val container = centerContainer as? FrameLayout ?: return
        val iconNames = listOf(
            "icons8_tasks_24",      // 0 — Задачи
            "icons8_alarm_24",      // 1 — Будильник
            "icons8_calls_24",      // 2 — Звонки
            "icons8_notes_50",      // 3 — Заметки (досье)
            "icons8_chat_50",       // 4 — Чаты
            "icons8_settings_50"    // 5 — Настройки
        )
        val icons = iconNames.mapNotNull { name ->
            val id = resources.getIdentifier(name, "drawable", packageName)
            if (id != 0) id else null
        }
        if (icons.size != iconNames.size) return // не все иконки найдены
        val colors = listOf(
            R.color.circle_btn_tasks,
            R.color.circle_btn_alarm,
            R.color.circle_btn_calls,
            R.color.circle_btn_notes,
            R.color.circle_btn_chats,
            R.color.circle_btn_settings
        ).map { ContextCompat.getColor(this, it) }
        val circleMenu = CircleMenuView(this, icons, colors)
        val sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120f, resources.displayMetrics).toInt()
        circleMenu.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = android.view.Gravity.CENTER
        }
        runCatching {
            val octopusId = resources.getIdentifier("octopus_icon", "drawable", packageName)
            if (octopusId != 0) circleMenu.setIconMenu(octopusId)
        }
        circleMenu.setEventListener(object : CircleMenuView.EventListener() {
            override fun onMenuOpenAnimationStart(view: CircleMenuView) {}
            override fun onMenuOpenAnimationEnd(view: CircleMenuView) {}
            override fun onMenuCloseAnimationStart(view: CircleMenuView) {}
            override fun onMenuCloseAnimationEnd(view: CircleMenuView) {}
            override fun onButtonClickAnimationStart(view: CircleMenuView, index: Int) {}
            override fun onButtonClickAnimationEnd(view: CircleMenuView, index: Int) {
                val overlay = findViewById<View>(R.id.menuOverlay)
                val panel = findViewById<View>(R.id.menuPanel)
                when (index) {
                    0 -> { Toast.makeText(this@MainActivity, R.string.toast_soon, Toast.LENGTH_SHORT).show(); hideMenuOverlay(overlay, panel) }
                    1 -> {
                        if (!settingsRepo.isClockEnabled()) Toast.makeText(this@MainActivity, R.string.toast_clock_disabled, Toast.LENGTH_SHORT).show()
                        else openClock()
                        hideMenuOverlay(overlay, panel)
                    }
                    2 -> { startActivity(Intent(this@MainActivity, com.example.friday_helper.contacts.ContactsActivity::class.java)); hideMenuOverlay(overlay, panel) }
                    3 -> {
                        if (SecuritySession.isGuest) Toast.makeText(this@MainActivity, "Гостевой режим: заметки недоступны", Toast.LENGTH_SHORT).show()
                        else startActivity(Intent(this@MainActivity, com.example.friday_helper.notes.NotesActivity::class.java))
                        hideMenuOverlay(overlay, panel)
                    }
                    4 -> { startActivity(Intent(this@MainActivity, com.example.friday_helper.chat.ChatsActivity::class.java)); hideMenuOverlay(overlay, panel) }
                    5 -> { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)); hideMenuOverlay(overlay, panel) }
                }
            }
        })
        container.addView(circleMenu)
    }

    /** Запуск голосового ввода: затемнение экрана, транскрипт, распознавание. voiceWaves — только для центральной кнопки (остановка волн). */
    private fun startVoiceInput(voiceWaves: VoiceWavesView?) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.toast_voice_permission, Toast.LENGTH_SHORT).show()
            return
        }
        voiceRecording = true
        voiceResetTriggered = false
        voiceWaves?.stopContinuous()
        findViewById<View>(R.id.voiceDimOverlay)?.apply {
            visibility = View.VISIBLE
            alpha = 0f
            animate().alpha(0.45f).setDuration(200).start()
        }
        // Затемняем системные полосы (верх с иконками, низ с кнопками назад/домой)
        ThemeManager.theme.value?.let { cfg ->
            val top = ThemeApplier.topBarColorFor(cfg.backgroundColor, cfg.backgroundModifier)
            val bottom = ThemeApplier.bottomBarColorFor(cfg.backgroundColor, cfg.backgroundModifier)
            window.statusBarColor = ThemeApplier.darkenColor(top, 0.5f)
            window.navigationBarColor = ThemeApplier.darkenColor(bottom, 0.5f)
        }
        val transcriptView = findViewById<TextView>(R.id.voiceTranscriptText)
        val chatEditText = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.chatEditText)
        transcriptView.visibility = View.VISIBLE
        transcriptView.text = getString(R.string.voice_input_listening)

        voiceSpeechRecognizer?.destroy()
        voiceSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    runOnUiThread {
                        val partial = transcriptView.text?.toString()?.trim()
                        val hasPartial = !partial.isNullOrEmpty() && partial != getString(R.string.voice_input_listening)
                        stopVoiceRecording()
                        if (hasPartial) applyVoiceResult(partial, chatEditText)
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    runOnUiThread {
                        val fromList = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                        val fromTranscript = transcriptView.text?.toString()?.trim()
                        val listeningHint = getString(R.string.voice_input_listening)
                        val text = when {
                            !fromList.isNullOrEmpty() -> fromList
                            !fromTranscript.isNullOrEmpty() && fromTranscript != listeningHint -> fromTranscript
                            else -> null
                        }
                        stopVoiceRecording()
                        if (!text.isNullOrEmpty()) applyVoiceResult(text, chatEditText)
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    runOnUiThread {
                        val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val part = list?.firstOrNull()?.trim()
                        if (part.isNullOrEmpty()) return@runOnUiThread
                        val current = transcriptView.text?.toString()?.trim() ?: ""
                        val hint = getString(R.string.voice_input_listening)
                        // Не заменять более длинный текст коротким (при паузе приходят короткие partial — не сбрасываем)
                        if (part.length >= current.length || current == hint) transcriptView.text = part
                    }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Не завершать сессию по тишине — только по stopListening() при отпускании кнопки
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3600000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3600000)
        }
        runCatching {
            voiceSpeechRecognizer?.startListening(intent)
        }.onFailure {
            stopVoiceRecording()
        }
    }

    private fun stopVoiceRecording() {
        voiceRecording = false
        runCatching { voiceSpeechRecognizer?.stopListening() }
        voiceSpeechRecognizer?.destroy()
        voiceSpeechRecognizer = null
        findViewById<TextView>(R.id.voiceTranscriptText)?.apply {
            visibility = View.GONE
            text = ""
        }
        findViewById<View>(R.id.voiceDimOverlay)?.apply {
            animate().alpha(0f).setDuration(200).withEndAction { visibility = View.GONE }.start()
        }
        // Восстанавливаем цвет системных полос
        ThemeManager.theme.value?.let { cfg ->
            window.statusBarColor = ThemeApplier.topBarColorFor(cfg.backgroundColor, cfg.backgroundModifier)
            window.navigationBarColor = ThemeApplier.bottomBarColorFor(cfg.backgroundColor, cfg.backgroundModifier)
        }
    }

    /** Применить результат голосового ввода: в строку для редактирования или сразу отправить в чат. */
    private fun applyVoiceResult(text: String, chatEditText: com.google.android.material.textfield.TextInputEditText) {
        if (settingsRepo.isVoiceSendImmediately()) {
            val centerContainer = findViewById<View>(R.id.centerButtonContainer)
            val voiceWaves = findViewById<View>(R.id.voiceWaves)
            val chatPanel = findViewById<View>(R.id.chatPanel)
            val chatRecycler = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.chatRecycler)
            val btnNewChat = findViewById<View>(R.id.btnNewChat)
            val greetingText = findViewById<TextView>(R.id.greetingText)
            lifecycleScope.launch {
                sendMessage(text, centerContainer, voiceWaves, chatPanel, chatRecycler, btnNewChat, greetingText)
                withContext(Dispatchers.Main) { chatEditText.setText("") }
            }
        } else {
            chatEditText.setText(text)
        }
    }

    /** Сброс голосового ввода без подстановки текста в поле. */
    private fun cancelVoiceInput() {
        voiceRecording = false
        voiceResetTriggered = false
        runCatching { voiceSpeechRecognizer?.stopListening() }
        voiceSpeechRecognizer?.destroy()
        voiceSpeechRecognizer = null
        findViewById<TextView>(R.id.voiceTranscriptText)?.apply {
            visibility = View.GONE
            text = ""
        }
        findViewById<View>(R.id.voiceDimOverlay)?.apply {
            animate().alpha(0f).setDuration(200).withEndAction { visibility = View.GONE }.start()
        }
        // Восстанавливаем цвет системных полос
        ThemeManager.theme.value?.let { cfg ->
            window.statusBarColor = ThemeApplier.topBarColorFor(cfg.backgroundColor, cfg.backgroundModifier)
            window.navigationBarColor = ThemeApplier.bottomBarColorFor(cfg.backgroundColor, cfg.backgroundModifier)
        }
    }

    /** Показать круг с иконкой удаления, по завершении анимации — сбросить голосовой ввод. */
    private fun showVoiceResetAndCancel() {
        if (voiceResetTriggered) return
        voiceResetTriggered = true
        val circle = findViewById<View>(R.id.voiceResetCircle)
        val iconView = findViewById<android.widget.ImageView>(R.id.voiceResetIcon)
        circle.visibility = View.VISIBLE
        circle.alpha = 0f
        circle.animate().alpha(1f).setDuration(150).start()
        val deleteResId = resources.getIdentifier("icons8_delete", "drawable", packageName)
        if (deleteResId != 0) {
            Glide.with(this)
                .asGif()
                .load(deleteResId)
                .into(object : CustomTarget<GifDrawable>() {
                    override fun onResourceReady(resource: GifDrawable, transition: Transition<in GifDrawable>?) {
                        iconView.setImageDrawable(resource)
                        resource.setLoopCount(1)
                        resource.start()
                    }
                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                })
        } else {
            iconView.setImageResource(android.R.drawable.ic_menu_delete)
        }
        voiceHandler.postDelayed({
            cancelVoiceInput()
            circle.visibility = View.GONE
            circle.alpha = 1f
        }, 1200)
    }

    /** Классическая центральная кнопка: короткое нажатие — волны; длинное — запись голоса с выводом текста. */
    private fun setupCenterButtonWaves(centerContainer: View, voiceWaves: VoiceWavesView) {
        val container = centerContainer as? FrameLayout ?: return
        var longPressRunnable: Runnable? = null

        val btn = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 84f, resources.displayMetrics).toInt(),
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 84f, resources.displayMetrics).toInt()
            ).apply { gravity = android.view.Gravity.CENTER }
            setBackgroundResource(R.drawable.bg_center_circle)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics).toInt(),
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics).toInt(),
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics).toInt(),
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics).toInt()
            )
            contentDescription = getString(R.string.app_name)
        }
        runCatching {
            val octopusId = resources.getIdentifier("octopus_icon", "drawable", packageName)
            if (octopusId != 0) btn.setImageResource(octopusId)
        }

        var downAt = 0L
        var downY = 0f
        btn.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downAt = android.os.SystemClock.elapsedRealtime()
                    downY = event.rawY
                    longPressRunnable?.let { voiceHandler.removeCallbacks(it) }
                    longPressRunnable = Runnable {
                        startVoiceInput(voiceWaves)
                        longPressRunnable = null
                    }
                    voiceHandler.postDelayed(longPressRunnable!!, 550)
                    voiceWaves.setOriginFrom(btn)
                    voiceWaves.startContinuous()
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (voiceRecording && !voiceResetTriggered && event.rawY - downY >= voiceResetSlideThresholdPx) {
                        showVoiceResetAndCancel()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL,
                android.view.MotionEvent.ACTION_OUTSIDE -> {
                    longPressRunnable?.let { voiceHandler.removeCallbacks(it) }
                    longPressRunnable = null
                    val elapsed = android.os.SystemClock.elapsedRealtime() - downAt
                    voiceWaves.stopContinuous()
                    if (voiceRecording) {
                        runCatching { voiceSpeechRecognizer?.stopListening() }
                    } else if (elapsed < 200) {
                        voiceWaves.setOriginFrom(btn)
                        voiceWaves.playBurst()
                    }
                    true
                }
                else -> false
            }
        }
        centerButtonWaves = btn
        container.addView(btn)
    }

    private fun updateWeatherVisibilityAndMaybeRequest() {
        if (!settingsRepo.isLocationEnabled()) {
            weatherContainer.visibility = View.GONE
            return
        }
        // has setting enabled
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            weatherContainer.visibility = View.GONE
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            fetchAndShowWeather()
        }
    }

    private fun fetchAndShowWeather() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
        if (loc == null) {
            weatherContainer.visibility = View.VISIBLE
            weatherText.text = "Погода: геолокация недоступна"
            return
        }
        val lat = loc.latitude
        val lon = loc.longitude
        weatherContainer.visibility = View.VISIBLE
        weatherText.text = "Погода: загрузка..."
        lifecycleScope.launch(Dispatchers.IO) {
            val data = WeatherClient.fetch(lat, lon)
            withContext(Dispatchers.Main) {
                if (data != null) {
                    val t = data.temperatureC
                    val d = data.description
                    weatherText.text = String.format("Погода: %.0f°C, %s", t, d)
                } else {
                    weatherText.text = "Погода: ошибка"
                }
            }
        }
    }

    private fun showMenuOverlay(overlay: View, panel: View, barColor: Int) {
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        panel.translationX = -panel.width.coerceAtLeast(1).toFloat()
        val a0 = ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f)
        val a1 = ObjectAnimator.ofFloat(panel, View.ALPHA, 0f, 1f)
        val a2 = ObjectAnimator.ofFloat(panel, View.TRANSLATION_X, panel.translationX, 0f)
        // shift center circle menu left
        val center = findViewById<View>(R.id.centerButtonContainer)
        val radius = (center.width / 2f).coerceAtLeast(0f)
        val a3 = ObjectAnimator.ofFloat(center, View.TRANSLATION_X, center.translationX, -radius)
        AnimatorSet().apply {
            duration = 200
            playTogether(a0, a1, a2, a3)
            start()
        }
        // color system bars same as drawer
        originalStatusBarColor = window.statusBarColor
        originalNavigationBarColor = window.navigationBarColor
        originalLightBars = WindowInsetsControllerCompat(window, findViewById(R.id.root)).isAppearanceLightStatusBars
        window.statusBarColor = barColor
        val darkBg = run {
            val r = Color.red(barColor) / 255.0
            val g = Color.green(barColor) / 255.0
            val b = Color.blue(barColor) / 255.0
            (0.299 * r + 0.587 * g + 0.114 * b) < 0.5
        }
        val controller = WindowInsetsControllerCompat(window, findViewById(R.id.root))
        controller.isAppearanceLightStatusBars = !darkBg
        controller.isAppearanceLightNavigationBars = !darkBg
    }

    private fun hideMenuOverlay(overlay: View, panel: View) {
        val wasOverlayVisible = overlay.visibility == View.VISIBLE
        val a0 = ObjectAnimator.ofFloat(overlay, View.ALPHA, overlay.alpha, 0f)
        val a1 = ObjectAnimator.ofFloat(panel, View.ALPHA, panel.alpha, 0f)
        val targetX = -panel.width.coerceAtLeast(1).toFloat()
        val a2 = ObjectAnimator.ofFloat(panel, View.TRANSLATION_X, 0f, targetX)
        // return center to original position (0)
        val center = findViewById<View>(R.id.centerButtonContainer)
        val a3 = ObjectAnimator.ofFloat(center, View.TRANSLATION_X, center.translationX, 0f)
        AnimatorSet().apply {
            duration = 180
            playTogether(a0, a1, a2, a3)
            start()
        }.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                overlay.visibility = View.GONE
                panel.translationX = targetX
                // восстанавливаем системные панели только если шторка бургера была открыта (не при клике по круговому меню)
                if (wasOverlayVisible) {
                originalStatusBarColor?.let { window.statusBarColor = it }
                originalNavigationBarColor?.let { window.navigationBarColor = it }
                originalLightBars?.let {
                    val controller = WindowInsetsControllerCompat(window, findViewById(R.id.root))
                    controller.isAppearanceLightStatusBars = it
                    controller.isAppearanceLightNavigationBars = it
                    }
                }
            }
        })
    }

    private fun updateChatUi(
        centerContainer: View,
        voiceWaves: View,
        chatPanel: View,
        btnNewChat: View,
        greetingText: View
    ) {
        val hasMessages = messageList.isNotEmpty()
        centerContainer.visibility = if (hasMessages) View.GONE else View.VISIBLE
        voiceWaves.visibility = if (hasMessages) View.GONE else View.VISIBLE
        chatPanel.visibility = if (hasMessages) View.VISIBLE else View.GONE
        btnNewChat.visibility = if (hasMessages) View.VISIBLE else View.GONE
        if (hasMessages) greetingText.visibility = View.GONE
    }

    private suspend fun sendMessage(
        userText: String,
        centerContainer: View,
        voiceWaves: View,
        chatPanel: View,
        chatRecycler: androidx.recyclerview.widget.RecyclerView,
        btnNewChat: View,
        greetingText: View
    ) {
        var chatId = currentChatId
        if (chatId == null) {
            chatId = chatRepository.createChat("Новый чат")
            currentChatId = chatId
        }
        chatRepository.addMessage(chatId, "user", userText)
        messageList.add(MessageEntity(chatId = chatId, role = "user", content = userText))
        messageAdapter.submitList(messageList.toList(), MESSAGE_STATUS_LOCAL)
        updateChatUi(centerContainer, voiceWaves, chatPanel, btnNewChat, greetingText)
        chatRecycler.post { chatRecycler.scrollToPosition(messageList.size - 1) }

        // Локальные команды (без API): "позвони/набери ...".
        val localCallMatch = Regex("(?:^|\\s)(позвони|набери)\\s+(.+)$", RegexOption.IGNORE_CASE).find(userText.trim())
        val localContactName = localCallMatch?.groupValues?.getOrNull(2)?.trim().orEmpty()
        if (localContactName.isNotBlank()) {
            val localResult = if (!settingsRepo.isContactsEnabled()) {
                getString(R.string.toast_contacts_disabled)
            } else {
                val ok = callContactByName(localContactName)
                if (ok) "Открываю звонок для контакта: $localContactName" else getString(R.string.toast_contact_not_found)
            }
            val localAssistant = MessageEntity(chatId = chatId, role = "assistant", content = localResult)
            chatRepository.addMessage(chatId, "assistant", localAssistant.content)
            messageList.add(localAssistant)
            if (messageList.size == 2) chatRepository.setChatTitleFromFirstMessage(chatId)
            withContext(Dispatchers.Main) {
                messageAdapter.submitList(messageList.toList())
                updateChatUi(centerContainer, voiceWaves, chatPanel, btnNewChat, greetingText)
                chatRecycler.post { chatRecycler.scrollToPosition(messageList.size - 1) }
            }
            return
        }

        val apiKey = settingsRepo.getOpenAiApiKey()
        if (apiKey.isBlank()) {
            val err = MessageEntity(chatId = chatId, role = "assistant", content = "Укажите API ключ OpenRouter в Настройки → Дополнительно.")
            chatRepository.addMessage(chatId, "assistant", err.content)
            messageList.add(err)
            withContext(Dispatchers.Main) {
                messageAdapter.submitList(messageList.toList())
                chatRecycler.post { chatRecycler.scrollToPosition(messageList.size - 1) }
            }
            return
        }
        val messagesForApi = listOf(OpenAiClient.Message(role = "system", content = SYSTEM_PROMPT)) +
            messageList.map { OpenAiClient.Message(role = it.role, content = it.content) }
        withContext(Dispatchers.Main) {
            messageAdapter.submitList(messageList.toList(), MESSAGE_STATUS_PROCESSING)
        }
        messageList.add(MessageEntity(chatId = chatId, role = "assistant", content = ""))
        withContext(Dispatchers.Main) {
            messageAdapter.submitList(messageList.toList())
            chatRecycler.post { chatRecycler.scrollToPosition(messageList.size - 1) }
        }
        val mainHandler = Handler(Looper.getMainLooper())
        val chunkThrottleMs = 80L
        val result = withContext(Dispatchers.IO) {
            val contentBuilder = StringBuilder()
            val lastChunkUpdateTime = longArrayOf(0L)
            OpenAiClient.completeStreaming(apiKey, messagesForApi, onChunk = { chunk ->
                contentBuilder.append(chunk)
                val fullSoFar = contentBuilder.toString()
                val now = android.os.SystemClock.uptimeMillis()
                if (lastChunkUpdateTime[0] == 0L || now - lastChunkUpdateTime[0] >= chunkThrottleMs) {
                    lastChunkUpdateTime[0] = now
                    mainHandler.post {
                        if (messageList.isNotEmpty() && messageList.last().role == "assistant") {
                            messageList[messageList.size - 1] = MessageEntity(chatId = chatId, role = "assistant", content = fullSoFar)
                            messageAdapter.updateLastMessageContent(fullSoFar)
                            chatRecycler.post { chatRecycler.scrollToPosition(messageList.size - 1) }
                        }
                    }
                }
            })
        }
        var assistantText = when {
            result.isSuccess -> (result.getOrNull() ?: "Нет ответа от модели")
            else -> {
                messageList.removeAt(messageList.size - 1)
                val e = result.exceptionOrNull()!!
                when (e) {
                    is CancellationException -> "Запрос отменён."
                    is IOException -> "Нет доступа в интернет. Проверьте подключение и запустите снова."
                    else -> "Ошибка: ${e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName}"
                }
            }
        }
        if (result.isSuccess) {
            val actionLine = assistantText.indexOf(FRIDAY_ACTION_PREFIX).let { idx ->
                if (idx < 0) null else assistantText.indexOf('\n', idx).let { end ->
                    if (end < 0) assistantText.substring(idx) else assistantText.substring(idx, end)
                }
            }
            actionLine?.let { line ->
                val afterPrefix = line.removePrefix(FRIDAY_ACTION_PREFIX).trim()
                val jsonStr = afterPrefix.run {
                    val start = indexOf('{')
                    if (start < 0) this else {
                        var depth = 0
                        var end = -1
                        for (i in start..this.lastIndex) {
                            when (this[i]) {
                                '{' -> depth++
                                '}' -> { depth--; if (depth == 0) { end = i; break } }
                            }
                        }
                        if (end >= 0) substring(start, end + 1) else substring(start)
                    }
                }
                try {
                    val json = JSONObject(jsonStr)
                    when (json.optString("action", "").lowercase()) {
                        "create_task" -> {
                            val assistantLower = assistantText.lowercase()
                            val userLower = userText.lowercase()
                            val userLooksLikeTaskIntent =
                                userLower.contains("задач") ||
                                userLower.contains("напомин") ||
                                userLower.contains("добавь") ||
                                userLower.contains("создай") ||
                                userLower.contains("поставь")
                            val assistantLooksLikeTaskIntent =
                                assistantLower.contains("задач") || assistantLower.contains("напомин")
                            // Если модель вернула явный create_task, выполняем его.
                            // Ограничение оставляем только для полностью нерелевантных запросов.
                            if (!userLooksLikeTaskIntent && !assistantLooksLikeTaskIntent) {
                                return@let
                            }
                            val title = json.optString("title", "").takeIf { it.isNotBlank() } ?: "Задача"
                            val dateStr = json.optString("date", "today")
                            val timeStr = json.optString("time").takeIf { it != "null" && it.isNotBlank() }
                            val reminderStr = json.optString("reminder", "none")
                            val cal = java.util.Calendar.getInstance(java.util.Locale.getDefault())
                            when (dateStr.lowercase()) {
                                "tomorrow" -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                "today", "" -> { }
                                else -> {
                                    val parts = dateStr.split("-")
                                    if (parts.size == 3) {
                                        cal.set(parts[0].toIntOrNull() ?: cal.get(java.util.Calendar.YEAR),
                                            (parts[1].toIntOrNull() ?: 1) - 1,
                                            parts[2].toIntOrNull() ?: 1)
                                    }
                                }
                            }
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            cal.set(java.util.Calendar.MINUTE, 0)
                            cal.set(java.util.Calendar.SECOND, 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            val dayStartMillis = cal.timeInMillis
                            var dueTimeMinutes = 0
                            var reminderType = REMINDER_NONE
                            var reminderParam = 0L
                            if (timeStr != null) {
                                val t = timeStr.split(":")
                                val h = t.getOrNull(0)?.toIntOrNull() ?: 0
                                val m = t.getOrNull(1)?.toIntOrNull() ?: 0
                                dueTimeMinutes = (h * 60 + m).coerceIn(0, 1439)
                                when (reminderStr.lowercase()) {
                                    "once" -> {
                                        reminderType = REMINDER_ONCE
                                        reminderParam = dayStartMillis + dueTimeMinutes * 60_000L
                                    }
                                    "daily" -> reminderType = REMINDER_DAILY
                                }
                            }
                            val tasksRepo = TasksRepository(TasksDatabase.getInstance(applicationContext))
                            val task = TaskEntity(
                                title = title,
                                subtitle = "",
                                dueDateMillis = dayStartMillis,
                                dueTimeMinutes = dueTimeMinutes,
                                tags = "",
                                done = false,
                                cardColorIndex = 0,
                                sortOrder = 0,
                                reminderType = reminderType,
                                reminderParam = reminderParam
                            )
                            lifecycleScope.launch(Dispatchers.IO) {
                                val id = tasksRepo.insertTask(task, emptyList())
                                TaskNotificationScheduler.schedule(this@MainActivity, task.copy(id = id))
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "Задача создана", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        "set_alarm" -> {
                            val timeStr = (json.optString("time", "") ?: "").trim()
                            val label = (json.optString("label", "") ?: "").trim()
                            val t = timeStr.split(":")
                            val h = t.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
                            val m = t.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                            if (timeStr.isNotBlank()) {
                                val setInClockApp = withContext(Dispatchers.Main) {
                                    if (!settingsRepo.isClockEnabled()) {
                                        Toast.makeText(this@MainActivity, R.string.toast_clock_disabled, Toast.LENGTH_SHORT).show()
                                        false
                                    } else {
                                        setSystemAlarm(h, m, label)
                                    }
                                }
                                if (setInClockApp) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@MainActivity, "Будильник создан в приложении Часы на $timeStr", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val triggerMillis = AlarmScheduler.triggerTimeAt(h, m, useTomorrowIfPassed = true)
                                    AlarmScheduler.schedule(this@MainActivity, triggerMillis, label)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@MainActivity, "Поставлен внутренний будильник на $timeStr", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        "call_contact", "call" -> {
                            val contactName = (json.optString("name", "") ?: "")
                                .ifBlank { json.optString("contact", "") ?: "" }
                                .ifBlank { json.optString("person", "") ?: "" }
                                .trim()
                            if (!settingsRepo.isContactsEnabled()) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, R.string.toast_contacts_disabled, Toast.LENGTH_SHORT).show()
                                }
                            } else if (contactName.isNotBlank()) {
                                val ok = callContactByName(contactName)
                                if (!ok) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@MainActivity, R.string.toast_contact_not_found, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) { }
                assistantText = assistantText.replace(line, "").trim()
                if (messageList.isNotEmpty() && messageList.last().role == "assistant") {
                    messageList[messageList.size - 1] = messageList.last().copy(content = assistantText)
                }
            }
            if (actionLine == null && settingsRepo.isContactsEnabled()) {
                val m = Regex("(?:^|\\s)(позвони|набери)\\s+(.+)$", RegexOption.IGNORE_CASE).find(userText.trim())
                val contactName = m?.groupValues?.getOrNull(2)?.trim().orEmpty()
                if (contactName.isNotBlank()) {
                    val ok = callContactByName(contactName)
                    if (!ok) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, R.string.toast_contact_not_found, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        if (!result.isSuccess) {
            messageList.add(MessageEntity(chatId = chatId, role = "assistant", content = assistantText))
        }
        chatRepository.addMessage(chatId, "assistant", assistantText)
        if (messageList.size == 2) chatRepository.setChatTitleFromFirstMessage(chatId)
        withContext(Dispatchers.Main) {
            messageAdapter.submitList(messageList.toList())
            updateChatUi(centerContainer, voiceWaves, chatPanel, btnNewChat, greetingText)
            chatRecycler.post { chatRecycler.scrollToPosition(messageList.size - 1) }
        }
    }
}