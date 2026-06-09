package com.example.friday_helper.settings

import android.content.Context
import android.graphics.Color
import androidx.core.content.edit
import com.example.friday_helper.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedCustomThemePreset(
    val id: String,
    val name: String,
    val backgroundColor: Int,
    val accentColor: Int,
    val emoji: String
)

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("user_theme", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    fun load(): ThemeManager.ThemeConfig {
        val type = ThemeManager.ThemeType.fromInt(prefs.getInt("theme_type", 0))
        val fontRes = prefs.getInt("font", R.font.opensans)
        return toConfig(type, fontRes).copy(backgroundModifier = getBackgroundModifier())
    }

    fun saveTheme(type: ThemeManager.ThemeType, fontResId: Int) {
        prefs.edit {
            putInt("theme_type", ThemeManager.ThemeType.toInt(type))
            putInt("font", fontResId)
        }
    }

    fun saveThemeCustom(backgroundColor: Int, accentColor: Int, themeName: String, backgroundEmoji: String?, fontResId: Int) {
        prefs.edit {
            putInt("theme_type", ThemeManager.ThemeType.toInt(ThemeManager.ThemeType.CUSTOM))
            putInt("font", fontResId)
            putInt(KEY_THEME_CUSTOM_BG, backgroundColor)
            putInt(KEY_THEME_CUSTOM_ACCENT, accentColor)
            putString(KEY_THEME_CUSTOM_NAME, themeName.trim())
            putString(KEY_THEME_CUSTOM_BG_EMOJI, backgroundEmoji?.trim().orEmpty())
        }
    }

    fun getCustomBackground(): Int = prefs.getInt(KEY_THEME_CUSTOM_BG, android.graphics.Color.parseColor("#3F8F8F"))
    fun getCustomAccent(): Int {
        if (prefs.contains(KEY_THEME_CUSTOM_ACCENT)) {
            return prefs.getInt(KEY_THEME_CUSTOM_ACCENT, Color.WHITE)
        }
        val bg = getCustomBackground()
        return if (luminance(bg) > 0.65) Color.parseColor("#212121") else Color.WHITE
    }
    fun getCustomThemeName(): String = prefs.getString(KEY_THEME_CUSTOM_NAME, "") ?: ""
    fun getCustomBackgroundEmoji(): String = prefs.getString(KEY_THEME_CUSTOM_BG_EMOJI, "") ?: ""

    fun getCustomThemePresets(): List<SavedCustomThemePreset> {
        ensureCustomPresetsStorageInitialized()
        return parseCustomPresetsJson(prefs.getString(KEY_CUSTOM_THEMES_JSON, "[]"))
    }

    /** @return true если тема добавлена; false если достигнут лимит сохранённых тем. */
    fun addCustomThemePreset(preset: SavedCustomThemePreset): Boolean {
        ensureCustomPresetsStorageInitialized()
        val list = parseCustomPresetsJson(prefs.getString(KEY_CUSTOM_THEMES_JSON, "[]")).toMutableList()
        if (list.size >= MAX_CUSTOM_THEME_PRESETS) return false
        val id = preset.id.ifBlank { UUID.randomUUID().toString() }
        list.add(preset.copy(id = id))
        prefs.edit { putString(KEY_CUSTOM_THEMES_JSON, customPresetsToJson(list)) }
        return true
    }

    fun removeCustomThemePreset(id: String) {
        if (!prefs.contains(KEY_CUSTOM_THEMES_JSON)) return
        val list = parseCustomPresetsJson(prefs.getString(KEY_CUSTOM_THEMES_JSON, "[]"))
            .filterNot { it.id == id }
        prefs.edit { putString(KEY_CUSTOM_THEMES_JSON, customPresetsToJson(list)) }
    }

    private fun ensureCustomPresetsStorageInitialized() {
        if (prefs.contains(KEY_CUSTOM_THEMES_JSON)) return
        val arr = JSONArray()
        val type = ThemeManager.ThemeType.fromInt(prefs.getInt("theme_type", 0))
        if (type == ThemeManager.ThemeType.CUSTOM) {
            arr.put(
                JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put(
                        "name",
                        getCustomThemeName().ifBlank {
                            appContext.getString(R.string.appearance_custom_theme_default_name)
                        }
                    )
                    put("bg", getCustomBackground())
                    put("accent", getCustomAccent())
                    put("emoji", getCustomBackgroundEmoji())
                }
            )
        }
        prefs.edit { putString(KEY_CUSTOM_THEMES_JSON, arr.toString()) }
    }

    private fun parseCustomPresetsJson(raw: String?): List<SavedCustomThemePreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { "$i" }
                SavedCustomThemePreset(
                    id = id,
                    name = o.optString("name"),
                    backgroundColor = o.optInt("bg"),
                    accentColor = o.optInt("accent"),
                    emoji = o.optString("emoji", "")
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun customPresetsToJson(list: List<SavedCustomThemePreset>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("bg", p.backgroundColor)
                    put("accent", p.accentColor)
                    put("emoji", p.emoji)
                }
            )
        }
        return arr.toString()
    }

    /** Модификатор фона: 0=без разделения, 1=горизонт верх/низ, 2=горизонт низ/верх, 3=диагональ верх/низ, 4=диагональ низ/верх */
    fun getBackgroundModifier(): Int = prefs.getInt(KEY_BACKGROUND_MODIFIER, 0).coerceIn(0, 4)
    fun setBackgroundModifier(value: Int) {
        prefs.edit { putInt(KEY_BACKGROUND_MODIFIER, value.coerceIn(0, 4)) }
    }

    /** Имя, которое показывается в приветствии над кнопкой: «Приветствую [имя]». */
    fun getGreetingName(): String = prefs.getString(KEY_GREETING_NAME, "") ?: ""
    fun setGreetingName(name: String) {
        prefs.edit { putString(KEY_GREETING_NAME, name.trim()) }
    }

    /** Слово, которое пользователь говорит, чтобы открыть приложение (голосовой вызов). */
    fun getWakeWord(): String = prefs.getString(KEY_WAKE_WORD, "") ?: ""
    fun setWakeWord(word: String) {
        prefs.edit { putString(KEY_WAKE_WORD, word.trim()) }
    }

    fun toConfig(type: ThemeManager.ThemeType, fontResId: Int): ThemeManager.ThemeConfig {
        return when (type) {
            ThemeManager.ThemeType.LIGHT -> {
                val background = Color.parseColor("#FFFFFF")
                val accent = Color.parseColor("#212121")
                ThemeManager.ThemeConfig(background, accent, fontResId, backgroundEmoji = null)
            }
            ThemeManager.ThemeType.DARK -> {
                val background = Color.parseColor("#1E1E1E")
                val accent = Color.parseColor("#FFFFFF")
                ThemeManager.ThemeConfig(background, accent, fontResId, backgroundEmoji = null)
            }
            ThemeManager.ThemeType.CUSTOM -> {
                val background = prefs.getInt(KEY_THEME_CUSTOM_BG, Color.parseColor("#3F8F8F"))
                val accent = getCustomAccent()
                val emoji = getCustomBackgroundEmoji().ifBlank { null }
                ThemeManager.ThemeConfig(background, accent, fontResId, backgroundEmoji = emoji)
            }
        }
    }

    // Location setting
    fun isLocationEnabled(): Boolean = prefs.getBoolean(KEY_LOCATION_ENABLED, false)
    fun setLocationEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LOCATION_ENABLED, enabled) }
    }

    // Clock integration
    fun isClockEnabled(): Boolean = prefs.getBoolean(KEY_CLOCK_ENABLED, false)
    fun setClockEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CLOCK_ENABLED, enabled) }
    }

    // Microphone (for voice wake)
    fun isMicrophoneEnabled(): Boolean = prefs.getBoolean(KEY_MICROPHONE_ENABLED, false)
    fun setMicrophoneEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_MICROPHONE_ENABLED, enabled) }
    }

    // Contacts (for AI "call contact by name")
    fun isContactsEnabled(): Boolean = prefs.getBoolean(KEY_CONTACTS_ENABLED, false)
    fun setContactsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CONTACTS_ENABLED, enabled) }
    }

    // Rates / crypto integrations
    fun isRatesEnabled(): Boolean = prefs.getBoolean(KEY_RATES_ENABLED, false)
    fun setRatesEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_RATES_ENABLED, enabled) }
    }

    fun isCryptoEnabled(): Boolean = prefs.getBoolean(KEY_CRYPTO_ENABLED, false)
    fun setCryptoEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CRYPTO_ENABLED, enabled) }
    }

    fun getRatesBaseFiat(): String = prefs.getString(KEY_RATES_BASE_FIAT, DEFAULT_BASE_FIAT) ?: DEFAULT_BASE_FIAT
    fun setRatesBaseFiat(code: String) {
        prefs.edit { putString(KEY_RATES_BASE_FIAT, code.uppercase()) }
    }

    fun getSelectedFiats(): Set<String> =
        prefs.getString(KEY_RATES_SELECTED_FIATS, DEFAULT_SELECTED_FIATS)?.split(",")
            ?.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() }?.uppercase() }
            ?.toSet()
            ?.ifEmpty { defaultFiatsSet() }
            ?: defaultFiatsSet()

    fun setSelectedFiats(codes: Set<String>) {
        val normalized = codes.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
        prefs.edit { putString(KEY_RATES_SELECTED_FIATS, normalized.joinToString(",")) }
    }

    fun getSelectedCryptos(): Set<String> =
        prefs.getString(KEY_RATES_SELECTED_CRYPTOS, DEFAULT_SELECTED_CRYPTOS)?.split(",")
            ?.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() }?.lowercase() }
            ?.toSet()
            ?.ifEmpty { defaultCryptosSet() }
            ?: defaultCryptosSet()

    fun setSelectedCryptos(ids: Set<String>) {
        val normalized = ids.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        prefs.edit { putString(KEY_RATES_SELECTED_CRYPTOS, normalized.joinToString(",")) }
    }

    fun getFiatProvider(): String = prefs.getString(KEY_RATES_FIAT_PROVIDER, DEFAULT_FIAT_PROVIDER) ?: DEFAULT_FIAT_PROVIDER
    fun setFiatProvider(value: String) {
        prefs.edit { putString(KEY_RATES_FIAT_PROVIDER, value) }
    }

    fun getCryptoProvider(): String = prefs.getString(KEY_RATES_CRYPTO_PROVIDER, DEFAULT_CRYPTO_PROVIDER) ?: DEFAULT_CRYPTO_PROVIDER
    fun setCryptoProvider(value: String) {
        prefs.edit { putString(KEY_RATES_CRYPTO_PROVIDER, value) }
    }

    fun getCryptoVsFiat(): String = prefs.getString(KEY_CRYPTO_VS_FIAT, DEFAULT_CRYPTO_VS_FIAT) ?: DEFAULT_CRYPTO_VS_FIAT
    fun setCryptoVsFiat(code: String) {
        prefs.edit { putString(KEY_CRYPTO_VS_FIAT, code.uppercase()) }
    }

    fun getOpenAiApiKey(): String = prefs.getString(KEY_OPENAI_API_KEY, "")?.takeIf { it.isNotBlank() }
        ?: DEFAULT_OPENROUTER_API_KEY
    fun setOpenAiApiKey(key: String) {
        prefs.edit { putString(KEY_OPENAI_API_KEY, key.trim()) }
    }

    /** true = круговое меню по центру, false = старая центральная кнопка с анимацией волн */
    fun isUseCenterCircleMenu(): Boolean = prefs.getBoolean(KEY_USE_CENTER_CIRCLE_MENU, true)
    fun setUseCenterCircleMenu(use: Boolean) {
        prefs.edit { putBoolean(KEY_USE_CENTER_CIRCLE_MENU, use) }
    }

    /** true = показывать анимации приветствия при запуске (по умолчанию включены) */
    fun isWelcomeAnimationsEnabled(): Boolean = prefs.getBoolean(KEY_WELCOME_ANIMATIONS_ENABLED, true)
    fun setWelcomeAnimationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_WELCOME_ANIMATIONS_ENABLED, enabled) }
    }

    /** true = голосовое сообщение сразу отправлять в чат; false = текст в строку для редактирования (по умолчанию) */
    fun isVoiceSendImmediately(): Boolean = prefs.getBoolean(KEY_VOICE_SEND_IMMEDIATELY, false)
    fun setVoiceSendImmediately(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_VOICE_SEND_IMMEDIATELY, enabled) }
    }

    /** true = звонить напрямую (ACTION_CALL), false = открыть звонилку/контакты с номером (по умолчанию). */
    fun isDirectCallMode(): Boolean = prefs.getBoolean(KEY_DIRECT_CALL_MODE, false)
    fun setDirectCallMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DIRECT_CALL_MODE, enabled) }
    }

    // Contact aliases for voice calling: key = normalized "name|phone"
    fun getContactAliasesMap(): Map<String, List<String>> {
        val raw = prefs.getString(KEY_CONTACT_ALIASES_JSON, "{}") ?: "{}"
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { key ->
                val arr = json.optJSONArray(key) ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optString(i)?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            }
        }.getOrElse { emptyMap() }
    }

    fun getAliasesForContact(key: String): List<String> = getContactAliasesMap()[key].orEmpty()

    fun setAliasesForContact(key: String, aliases: List<String>) {
        val normalized = aliases.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val map = getContactAliasesMap().toMutableMap()
        if (normalized.isEmpty()) map.remove(key) else map[key] = normalized
        val out = JSONObject()
        map.forEach { (k, list) ->
            out.put(k, JSONArray().apply { list.forEach { put(it) } })
        }
        prefs.edit { putString(KEY_CONTACT_ALIASES_JSON, out.toString()) }
    }

    companion object {
        private const val KEY_THEME_CUSTOM_BG = "theme_custom_bg"
        private const val KEY_THEME_CUSTOM_ACCENT = "theme_custom_accent"
        private const val KEY_THEME_CUSTOM_NAME = "theme_custom_name"
        private const val KEY_THEME_CUSTOM_BG_EMOJI = "theme_custom_bg_emoji"
        private const val KEY_CUSTOM_THEMES_JSON = "custom_themes_json"
        const val MAX_CUSTOM_THEME_PRESETS = 24
        private const val KEY_BACKGROUND_MODIFIER = "background_modifier"

        private fun luminance(color: Int): Double {
            val r = Color.red(color) / 255.0
            val g = Color.green(color) / 255.0
            val b = Color.blue(color) / 255.0
            return 0.299 * r + 0.587 * g + 0.114 * b
        }
        const val KEY_GREETING_NAME = "greeting_name"
        private const val KEY_WAKE_WORD = "wake_word"
        private const val KEY_LOCATION_ENABLED = "location_enabled"
        private const val KEY_CLOCK_ENABLED = "clock_enabled"
        private const val KEY_MICROPHONE_ENABLED = "microphone_enabled"
        private const val KEY_CONTACTS_ENABLED = "contacts_enabled"

        private const val KEY_RATES_ENABLED = "rates_enabled"
        private const val KEY_CRYPTO_ENABLED = "crypto_enabled"
        private const val KEY_RATES_BASE_FIAT = "rates_base_fiat"
        private const val KEY_RATES_SELECTED_FIATS = "rates_selected_fiats"
        private const val KEY_RATES_SELECTED_CRYPTOS = "rates_selected_cryptos"
        private const val KEY_RATES_FIAT_PROVIDER = "rates_fiat_provider"
        private const val KEY_RATES_CRYPTO_PROVIDER = "rates_crypto_provider"
        private const val KEY_CRYPTO_VS_FIAT = "crypto_vs_fiat"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        /** Ключ OpenRouter по умолчанию (DeepSeek R1 0528), если пользователь не задал свой. */
        private const val DEFAULT_OPENROUTER_API_KEY = "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"
        private const val KEY_USE_CENTER_CIRCLE_MENU = "use_center_circle_menu"
        private const val KEY_WELCOME_ANIMATIONS_ENABLED = "welcome_animations_enabled"
        private const val KEY_VOICE_SEND_IMMEDIATELY = "voice_send_immediately"
        private const val KEY_DIRECT_CALL_MODE = "direct_call_mode"
        private const val KEY_CONTACT_ALIASES_JSON = "contact_aliases_json"

        fun contactAliasKey(name: String, phone: String): String {
            val normalizedName = name.trim().lowercase()
            val normalizedPhone = phone.filter { it.isDigit() }
            return "$normalizedName|$normalizedPhone"
        }

        private const val DEFAULT_BASE_FIAT = "RUB"
        private const val DEFAULT_SELECTED_FIATS = "RUB,USD,EUR"
        private const val DEFAULT_SELECTED_CRYPTOS = "bitcoin,ethereum,toncoin"

        // Providers are just string ids stored in prefs; real logic lives in rates package.
        private const val DEFAULT_FIAT_PROVIDER = "cbr"
        private const val DEFAULT_CRYPTO_PROVIDER = "coingecko"
        private const val DEFAULT_CRYPTO_VS_FIAT = "RUB"

        private fun defaultFiatsSet(): Set<String> = DEFAULT_SELECTED_FIATS.split(",").map { it.trim().uppercase() }.toSet()
        private fun defaultCryptosSet(): Set<String> = DEFAULT_SELECTED_CRYPTOS.split(",").map { it.trim().lowercase() }.toSet()
    }
}

