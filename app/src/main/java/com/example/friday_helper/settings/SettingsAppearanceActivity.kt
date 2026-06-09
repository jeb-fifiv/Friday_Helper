package com.example.friday_helper.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.friday_helper.R
import com.example.friday_helper.settings.ThemeApplier.textColorOnBackground
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Экран «Внешний вид»: только белая/чёрная темы и пользовательская тема.
 */
class SettingsAppearanceActivity : AppCompatActivity() {

    private lateinit var previewScreen: View
    private lateinit var customThemeHexInfo: TextView
    private lateinit var customBgPreview: View
    private lateinit var customAccentPreview: View
    private lateinit var customThemeNameInput: TextInputEditText
    private lateinit var customEmojiInput: TextInputEditText
    private lateinit var colorSectionContent: LinearLayout
    private lateinit var modifierSectionContent: LinearLayout
    private lateinit var arrowColor: ImageView
    private lateinit var arrowModifier: ImageView
    private lateinit var savedThemesList: LinearLayout
    private lateinit var tvSavedThemesEmpty: TextView
    private lateinit var tvSavedThemesHeader: TextView
    private lateinit var btnSaveThemeToList: MaterialButton
    private lateinit var customThemeNameLayout: TextInputLayout
    private lateinit var customEmojiLayout: TextInputLayout
    private lateinit var btnPickCustomBackground: MaterialButton
    private lateinit var btnPickCustomAccent: MaterialButton
    private lateinit var btnApplyCustomTheme: MaterialButton
    private lateinit var rootAppearance: View

    private val modifierNames: List<Int> = listOf(
        R.string.modifier_none,
        R.string.modifier_half_top,
        R.string.modifier_half_bottom,
        R.string.modifier_angle_top,
        R.string.modifier_angle_bottom,
    )
    private var customBgColor: Int = Color.parseColor("#3F8F8F")
    private var customAccentColor: Int = Color.WHITE
    private var customBackgroundEmoji: String = ""
    private var pendingThemeType: ThemeManager.ThemeType = ThemeManager.ThemeType.LIGHT
    private var pendingModifier: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_appearance)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        rootAppearance = findViewById(R.id.rootAppearance)
        previewScreen = findViewById(R.id.previewScreen)
        val btnBack = findViewById<View>(R.id.btnBack)
        val colorSectionHeader = findViewById<View>(R.id.colorSectionHeader)
        colorSectionContent = findViewById(R.id.colorSectionContent)
        arrowColor = findViewById(R.id.arrowColor)
        val modifierSectionHeader = findViewById<View>(R.id.modifierSectionHeader)
        modifierSectionContent = findViewById(R.id.modifierSectionContent)
        arrowModifier = findViewById(R.id.arrowModifier)
        customThemeNameLayout = findViewById(R.id.customThemeNameLayout)
        customThemeNameInput = findViewById(R.id.customThemeNameInput)
        customEmojiLayout = findViewById(R.id.customEmojiLayout)
        customEmojiInput = findViewById(R.id.customEmojiInput)
        btnPickCustomBackground = findViewById(R.id.btnPickCustomBackground)
        btnPickCustomAccent = findViewById(R.id.btnPickCustomAccent)
        btnApplyCustomTheme = findViewById(R.id.btnApplyCustomTheme)
        btnSaveThemeToList = findViewById(R.id.btnSaveThemeToList)
        tvSavedThemesHeader = findViewById(R.id.tvSavedThemesHeader)
        tvSavedThemesEmpty = findViewById(R.id.tvSavedThemesEmpty)
        savedThemesList = findViewById(R.id.savedThemesList)
        customBgPreview = findViewById(R.id.customBgPreview)
        customAccentPreview = findViewById(R.id.customAccentPreview)
        customThemeHexInfo = findViewById(R.id.customThemeHexInfo)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.title = ""
        btnBack.setOnClickListener { finish() }

        ThemeManager.init(this)
        val repo = SettingsRepository(this)
        val fontResId = { ThemeManager.theme.value?.let { it.fontResId } ?: repo.load().fontResId }
        pendingThemeType = ThemeManager.ThemeType.fromInt(
            getSharedPreferences("user_theme", MODE_PRIVATE).getInt("theme_type", 0)
        )
        pendingModifier = repo.getBackgroundModifier()
        customBgColor = repo.getCustomBackground()
        customAccentColor = repo.getCustomAccent()
        customBackgroundEmoji = repo.getCustomBackgroundEmoji()
        customThemeNameInput.setText(repo.getCustomThemeName())
        customEmojiInput.setText(customBackgroundEmoji)

        btnSaveThemeToList.setOnClickListener {
            val name = customThemeNameInput.text?.toString()?.trim().orEmpty()
            val finalName = if (name.isBlank()) getString(R.string.appearance_custom_theme_default_name) else name
            val emoji = customEmojiInput.text?.toString()?.trim().orEmpty()
            val preset = SavedCustomThemePreset(
                id = "",
                name = finalName,
                backgroundColor = customBgColor,
                accentColor = customAccentColor,
                emoji = emoji
            )
            val labelColor = textColorOnBackground(ContextCompat.getColor(this, R.color.dark_gray))
            if (repo.addCustomThemePreset(preset)) {
                Toast.makeText(this, R.string.appearance_theme_saved_to_list, Toast.LENGTH_SHORT).show()
                rebuildSavedThemesList(repo, labelColor)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.appearance_theme_presets_limit, SettingsRepository.MAX_CUSTOM_THEME_PRESETS),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val lightColor = Color.parseColor("#FFFFFF")
        val darkColor = Color.parseColor("#1E1E1E")
        colorSectionContent.removeAllViews()
        listOf(
            lightColor to R.string.color_light,
            darkColor to R.string.color_dark
        ).forEachIndexed { index, colorMeta ->
            val row = layoutInflater.inflate(R.layout.item_appearance_color, colorSectionContent, false)
            val circle = row.findViewById<View>(R.id.colorCircle)
            val label = row.findViewById<TextView>(R.id.colorLabel)
            circle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorMeta.first)
            }
            label.setText(colorMeta.second)
            row.setOnClickListener {
                pendingThemeType = if (index == 0) ThemeManager.ThemeType.LIGHT else ThemeManager.ThemeType.DARK
                refreshCustomPreviews()
            }
            colorSectionContent.addView(row)
        }

        btnPickCustomBackground.setOnClickListener {
            showRgbColorPicker(
                title = getString(R.string.appearance_pick_bg_color),
                initialColor = customBgColor
            ) { selected ->
                pendingThemeType = ThemeManager.ThemeType.CUSTOM
                customBgColor = selected
                refreshCustomPreviews()
            }
        }

        btnPickCustomAccent.setOnClickListener {
            showRgbColorPicker(
                title = getString(R.string.appearance_pick_text_color),
                initialColor = customAccentColor
            ) { selected ->
                pendingThemeType = ThemeManager.ThemeType.CUSTOM
                customAccentColor = selected
                refreshCustomPreviews()
            }
        }

        customEmojiInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                customBackgroundEmoji = s?.toString()?.trim().orEmpty()
                if (pendingThemeType == ThemeManager.ThemeType.CUSTOM) {
                    refreshCustomPreviews()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnApplyCustomTheme.setOnClickListener {
            val name = customThemeNameInput.text?.toString()?.trim().orEmpty()
            val finalName = if (name.isBlank()) getString(R.string.appearance_custom_theme_default_name) else name
            customThemeNameInput.setText(finalName)
            customBackgroundEmoji = customEmojiInput.text?.toString()?.trim().orEmpty()
            customEmojiInput.setText(customBackgroundEmoji)
            val font = fontResId()
            when (pendingThemeType) {
                ThemeManager.ThemeType.LIGHT -> ThemeManager.updateTheme(ThemeManager.ThemeType.LIGHT, font)
                ThemeManager.ThemeType.DARK -> ThemeManager.updateTheme(ThemeManager.ThemeType.DARK, font)
                ThemeManager.ThemeType.CUSTOM -> ThemeManager.updateThemeCustom(customBgColor, customAccentColor, finalName, customBackgroundEmoji, font)
            }
            ThemeManager.updateBackgroundModifier(pendingModifier)
            Toast.makeText(this, R.string.appearance_applied, Toast.LENGTH_SHORT).show()
        }

        modifierSectionContent.removeAllViews()
        modifierNames.forEachIndexed { index, nameRes ->
            val text = TextView(this).apply {
                text = getString(nameRes)
                setPadding(
                    (16 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt(),
                    (16 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt()
                )
                setBackgroundResource(android.R.drawable.list_selector_background)
            }
            text.setOnClickListener {
                pendingModifier = index
                refreshCustomPreviews()
            }
            modifierSectionContent.addView(text)
        }

        colorSectionHeader.setOnClickListener {
            val visible = colorSectionContent.visibility == View.VISIBLE
            colorSectionContent.visibility = if (visible) View.GONE else View.VISIBLE
            arrowColor.rotation = if (visible) 0f else 180f
        }
        modifierSectionHeader.setOnClickListener {
            val visible = modifierSectionContent.visibility == View.VISIBLE
            modifierSectionContent.visibility = if (visible) View.GONE else View.VISIBLE
            arrowModifier.rotation = if (visible) 0f else 180f
        }

        // Фон окна «Внешний вид» всегда тёмно-серый; превью и остальная логика — по теме
        val windowBgDarkGray = ContextCompat.getColor(this, R.color.dark_gray)
        rootAppearance.setBackgroundColor(windowBgDarkGray)
        toolbar.setBackgroundColor(windowBgDarkGray)
        refreshCustomPreviews()
        rebuildSavedThemesList(repo, textColorOnBackground(windowBgDarkGray))
        ThemeManager.theme.observe(this) { cfg ->
            ThemeApplier.applyFont(rootAppearance, this, cfg.fontResId)
            val txtColor = textColorOnBackground(windowBgDarkGray)
            findViewById<TextView>(R.id.tvTitle).setTextColor(txtColor)
            findViewById<TextView>(R.id.labelColor).setTextColor(txtColor)
            findViewById<TextView>(R.id.labelModifier).setTextColor(txtColor)
            arrowColor.setColorFilter(txtColor)
            arrowModifier.setColorFilter(txtColor)
            for (i in 0 until colorSectionContent.childCount) {
                val row = colorSectionContent.getChildAt(i)
                (row as? LinearLayout)?.findViewById<TextView>(R.id.colorLabel)?.setTextColor(txtColor)
            }
            for (i in 0 until modifierSectionContent.childCount) {
                (modifierSectionContent.getChildAt(i) as? TextView)?.setTextColor(txtColor)
            }
            customThemeHexInfo.setTextColor(txtColor)
            customThemeNameInput.setTextColor(txtColor)
            customThemeNameInput.setHintTextColor(txtColor)
            customEmojiInput.setTextColor(txtColor)
            customEmojiInput.setHintTextColor(txtColor)
            ThemeApplier.styleTextInput(customThemeNameLayout, cfg)
            ThemeApplier.styleTextInput(customEmojiLayout, cfg)
            tvSavedThemesHeader.setTextColor(txtColor)
            tvSavedThemesEmpty.setTextColor(txtColor)
            listOf(btnPickCustomBackground, btnPickCustomAccent, btnApplyCustomTheme, btnSaveThemeToList).forEach { btn ->
                ThemeApplier.styleRoundedBackground(btn.background, ThemeApplier.surfaceColorFor(cfg.backgroundColor), cfg.accentColor)
                btn.setTextColor(txtColor)
                btn.backgroundTintList = null
            }
            rebuildSavedThemesList(repo, txtColor)
            refreshCustomPreviews()
        }
    }

    private fun refreshCustomPreviews() {
        val lightColor = Color.parseColor("#FFFFFF")
        val darkColor = Color.parseColor("#1E1E1E")
        if (::customBgPreview.isInitialized) {
            customBgPreview.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(customBgColor)
            }
            customAccentPreview.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(customAccentColor)
            }
            customThemeHexInfo.text = getString(
                R.string.appearance_custom_hex_info,
                toHex(customBgColor),
                toHex(customAccentColor)
            )
            val previewBg = when (pendingThemeType) {
                ThemeManager.ThemeType.LIGHT -> lightColor
                ThemeManager.ThemeType.DARK -> darkColor
                ThemeManager.ThemeType.CUSTOM -> customBgColor
            }
            val previewEmoji = if (pendingThemeType == ThemeManager.ThemeType.CUSTOM) customBackgroundEmoji else null
            previewScreen.background = ThemeApplier.backgroundDrawableFor(previewBg, pendingModifier, previewEmoji)
        }
    }

    private fun rebuildSavedThemesList(repo: SettingsRepository, labelTextColor: Int) {
        if (!::savedThemesList.isInitialized) return
        val presets = repo.getCustomThemePresets()
        savedThemesList.removeAllViews()
        tvSavedThemesEmpty.visibility = if (presets.isEmpty()) View.VISIBLE else View.GONE
        presets.forEach { preset ->
            val row = layoutInflater.inflate(R.layout.item_saved_theme_row, savedThemesList, false)
            row.findViewById<View>(R.id.savedThemeBgDot).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(preset.backgroundColor)
            }
            row.findViewById<View>(R.id.savedThemeAccentDot).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(preset.accentColor)
            }
            row.findViewById<TextView>(R.id.savedThemeName).apply {
                text = preset.name.ifBlank { getString(R.string.appearance_custom_theme_default_name) }
                setTextColor(labelTextColor)
            }
            row.findViewById<ImageButton>(R.id.btnDeleteSavedTheme).apply {
                setColorFilter(labelTextColor)
                setOnClickListener {
                    repo.removeCustomThemePreset(preset.id)
                    Toast.makeText(this@SettingsAppearanceActivity, R.string.appearance_theme_removed_from_list, Toast.LENGTH_SHORT).show()
                    rebuildSavedThemesList(repo, labelTextColor)
                }
            }
            row.setOnClickListener {
                customBgColor = preset.backgroundColor
                customAccentColor = preset.accentColor
                customBackgroundEmoji = preset.emoji
                customThemeNameInput.setText(preset.name)
                customEmojiInput.setText(preset.emoji)
                pendingThemeType = ThemeManager.ThemeType.CUSTOM
                refreshCustomPreviews()
            }
            savedThemesList.addView(row)
        }
    }

    private fun showRgbColorPicker(
        title: String,
        initialColor: Int,
        onColorSelected: (Int) -> Unit
    ) {
        val preview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (42 * resources.displayMetrics.density).toInt()
            )
        }
        val valueText = TextView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 6)
            addView(preview)
            addView(valueText)
        }
        fun seek(label: String, value: Int): Pair<TextView, SeekBar> {
            val tv = TextView(this).apply { text = "$label: $value" }
            val sb = SeekBar(this).apply { max = 255; progress = value }
            container.addView(tv)
            container.addView(sb)
            return tv to sb
        }
        val (rLabel, rBar) = seek("R", Color.red(initialColor))
        val (gLabel, gBar) = seek("G", Color.green(initialColor))
        val (bLabel, bBar) = seek("B", Color.blue(initialColor))

        fun updatePreview() {
            val c = Color.rgb(rBar.progress, gBar.progress, bBar.progress)
            preview.setBackgroundColor(c)
            valueText.text = toHex(c)
            rLabel.text = "R: ${rBar.progress}"
            gLabel.text = "G: ${gBar.progress}"
            bLabel.text = "B: ${bBar.progress}"
        }
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        rBar.setOnSeekBarChangeListener(listener)
        gBar.setOnSeekBarChangeListener(listener)
        bBar.setOnSeekBarChangeListener(listener)
        updatePreview()

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_apply) { _, _ ->
                onColorSelected(Color.rgb(rBar.progress, gBar.progress, bBar.progress))
            }
            .show()
    }

    private fun toHex(color: Int): String = String.format("#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color))
}
