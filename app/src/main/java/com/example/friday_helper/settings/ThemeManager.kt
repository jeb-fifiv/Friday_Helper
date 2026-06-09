package com.example.friday_helper.settings

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.friday_helper.R

object ThemeManager {

    data class ThemeConfig(
        val backgroundColor: Int,
        val accentColor: Int,
        val fontResId: Int,
        val backgroundModifier: Int = 0,
        val backgroundEmoji: String? = null
    )

    enum class ThemeType { LIGHT, DARK, CUSTOM;
        companion object {
            fun fromInt(value: Int): ThemeType = when (value) {
                1 -> DARK
                2 -> CUSTOM
                else -> LIGHT
            }
            fun toInt(type: ThemeType): Int = when (type) {
                LIGHT -> 0
                DARK -> 1
                CUSTOM -> 2
            }
        }
    }

    private val internalTheme = MutableLiveData<ThemeConfig>()
    val theme: LiveData<ThemeConfig> = internalTheme

    private lateinit var repository: SettingsRepository

    fun init(context: Context) {
        if (!::repository.isInitialized) {
            repository = SettingsRepository(context.applicationContext)
        }
        internalTheme.value = repository.load()
    }

    fun updateTheme(type: ThemeType, fontResId: Int) {
        val mod = internalTheme.value?.backgroundModifier ?: repository.getBackgroundModifier()
        val config = repository.toConfig(type, fontResId).copy(backgroundModifier = mod)
        internalTheme.value = config
        repository.saveTheme(type, fontResId)
    }

    fun updateThemeCustom(backgroundColor: Int, accentColor: Int, themeName: String, backgroundEmoji: String?, fontResId: Int) {
        repository.saveThemeCustom(backgroundColor, accentColor, themeName, backgroundEmoji, fontResId)
        val mod = internalTheme.value?.backgroundModifier ?: repository.getBackgroundModifier()
        val config = repository.toConfig(ThemeType.CUSTOM, fontResId).copy(backgroundModifier = mod)
        internalTheme.value = config
    }

    fun updateBackgroundModifier(modifier: Int) {
        repository.setBackgroundModifier(modifier)
        val current = internalTheme.value ?: return
        internalTheme.value = current.copy(backgroundModifier = modifier)
    }
}

