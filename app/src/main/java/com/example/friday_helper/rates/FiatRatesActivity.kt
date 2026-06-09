package com.example.friday_helper.rates

import android.text.InputType
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.friday_helper.databinding.ActivityFiatRatesBinding
import com.example.friday_helper.settings.SettingsRepository
import com.example.friday_helper.settings.ThemeApplier
import com.example.friday_helper.settings.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FiatRatesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFiatRatesBinding
    private lateinit var settings: SettingsRepository
    private val adapter = RateRowAdapter()

    private var rates: Map<String, Double> = emptyMap()
    private var source: RatesResult<*>? = null
    private var lastUpdatedAt: Long? = null

    private data class ProviderOption(val id: String, val title: String)
    private val providers = listOf(
        ProviderOption("cbr", "ЦБ РФ (официальный сайт)"),
        ProviderOption("cbr_ru", "cbr-xml-daily.ru (данные ЦБ РФ)"),
    )

    private val commonFiats = listOf("RUB", "USD", "EUR", "GBP", "CNY", "JPY", "CHF", "KZT", "UAH")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFiatRatesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsRepository(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        binding.btnBack.setOnClickListener { finish() }

        setupList()
        setupDropdowns()
        setupButtons()
        applyTheme()

        refresh()
    }

    override fun onResume() {
        super.onResume()
        updateLinksVisibility()
    }

    private fun setupList() {
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
    }

    private fun setupDropdowns() {
        fun setupNonEditable(dd: AutoCompleteTextView) {
            dd.inputType = InputType.TYPE_NULL
            dd.keyListener = null
            dd.isCursorVisible = false
            dd.setOnClickListener { dd.showDropDown() }
            dd.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) dd.showDropDown() }
        }

        setupNonEditable(binding.baseDropdown)
        setupNonEditable(binding.providerDropdown)

        binding.baseDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, commonFiats))
        binding.baseDropdown.setText(settings.getRatesBaseFiat().uppercase(), false)
        binding.baseDropdown.setOnItemClickListener { _, _, pos, _ ->
            val base = commonFiats.getOrNull(pos) ?: return@setOnItemClickListener
            settings.setRatesBaseFiat(base)
            // ensure base exists in selected set
            val set = settings.getSelectedFiats().toMutableSet()
            set.add(base)
            settings.setSelectedFiats(set)
            refresh()
        }

        val titles = providers.map { it.title }
        binding.providerDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, titles))
        val saved = settings.getFiatProvider()
        binding.providerDropdown.setText(providers.find { it.id == saved }?.title ?: providers.first().title, false)
        binding.providerDropdown.setOnItemClickListener { _, _, pos, _ ->
            val opt = providers.getOrNull(pos) ?: return@setOnItemClickListener
            settings.setFiatProvider(opt.id)
            refresh()
        }
    }

    private fun setupButtons() {
        binding.btnChooseFiats.setOnClickListener { openFiatsDialog() }
        binding.btnRefresh.setOnClickListener { refresh() }

        binding.btnOpenCrypto.setOnClickListener {
            startActivity(android.content.Intent(this, CryptoRatesActivity::class.java))
        }
        binding.btnOpenFiatToCrypto.setOnClickListener {
            startActivity(android.content.Intent(this, FiatToCryptoActivity::class.java))
        }

        updateLinksVisibility()
    }

    private fun updateLinksVisibility() {
        val enabled = settings.isRatesEnabled()
        val cryptoEnabled = enabled && settings.isCryptoEnabled()
        binding.boxLinks.visibility = if (cryptoEnabled) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun applyTheme() {
        ThemeManager.init(this)
        ThemeManager.theme.observe(this) { cfg ->
            ThemeApplier.applyToToolbar(binding.toolbar, cfg)
            ThemeApplier.applyBackground(binding.rootFiatRates, cfg)
            ThemeApplier.applyFont(binding.rootFiatRates, this, cfg.fontResId)
            val txt = cfg.accentColor
            val surface = ThemeApplier.surfaceColorFor(cfg.backgroundColor)

            binding.tvTitle.setTextColor(txt)
            binding.tvSettingsHeader.setTextColor(txt)
            binding.tvBaseLabel.setTextColor(txt)
            binding.tvProviderLabel.setTextColor(txt)
            binding.tvAttribution.setTextColor(txt)

            // settings box background
            binding.boxSettings.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(surface)
                val px = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
                setStroke(px, cfg.accentColor)
            }

            // buttons (same logic as other screens)
            listOf(binding.btnChooseFiats, binding.btnRefresh, binding.btnOpenCrypto, binding.btnOpenFiatToCrypto).forEach { v ->
                val d = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 12f * resources.displayMetrics.density
                    setColor(surface)
                    val px = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
                    setStroke(px, cfg.accentColor)
                }
                v.background = d
                v.setTextColor(txt)
                v.backgroundTintList = null
            }

            // dropdowns
            listOf(binding.baseDropdown, binding.providerDropdown).forEach {
                val d = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 12f * resources.displayMetrics.density
                    setColor(surface)
                    val px = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
                    setStroke(px, cfg.accentColor)
                }
                it.background = d
                it.setTextColor(txt)
                it.setHintTextColor(android.content.res.ColorStateList.valueOf(txt))
            }

            adapter.setStyle(text = txt, fill = surface, stroke = cfg.accentColor)
        }
    }

    private fun refresh() {
        binding.btnRefresh.isEnabled = false
        binding.tvAttribution.text = "Загрузка..."

        val base = settings.getRatesBaseFiat()
        val provider = settings.getFiatProvider()
        val selected = settings.getSelectedFiats().toMutableSet().apply { add(base.uppercase()) }

        lifecycleScope.launch(Dispatchers.IO) {
            val res = FiatRatesClients.fetch(base, provider)
            withContext(Dispatchers.Main) {
                binding.btnRefresh.isEnabled = true
                if (res == null) {
                    rates = emptyMap()
                    source = null
                    lastUpdatedAt = null
                    adapter.submit(emptyList())
                    binding.tvAttribution.text = "Источник: —"
                    return@withContext
                }
                rates = res.data
                source = res
                lastUpdatedAt = System.currentTimeMillis()

                val baseUp = base.uppercase()
                val rows = selected.map { it.uppercase() }.distinct().sorted()
                    .filter { it != baseUp }
                    .mapNotNull { code ->
                        val r = rates[code] ?: return@mapNotNull null
                        RateRow(
                            title = code,
                            subtitle = "1 $baseUp = ${formatRate(r)} $code",
                            value = formatRate(r)
                        )
                    }
                adapter.submit(rows)
                updateAttribution()
            }
        }
    }

    private fun openFiatsDialog() {
        val all = commonFiats
        val current = settings.getSelectedFiats().map { it.uppercase() }.toSet()
        val checked = all.map { current.contains(it) }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Выберите валюты")
            .setMultiChoiceItems(all.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Сохранить") { _, _ ->
                val set = mutableSetOf<String>()
                for (i in all.indices) if (checked[i]) set.add(all[i])
                set.add(settings.getRatesBaseFiat().uppercase())
                if (set.isEmpty()) set.add("RUB")
                settings.setSelectedFiats(set)
                refresh()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateAttribution() {
        val s = source as? RatesResult<*>
        val whenText = lastUpdatedAt?.let {
            val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            "обновлено: ${df.format(Date(it))}"
        }
        if (s == null) {
            binding.tvAttribution.text = "Источник: —"
            return
        }
        val url = s.sourceUrl.takeIf { it.isNotBlank() } ?: ""
        val srcText = "Источник: ${s.sourceName}${if (url.isNotEmpty()) " ($url)" else ""}"
        binding.tvAttribution.text = if (whenText != null) "$srcText • $whenText" else srcText
    }

    private fun formatRate(v: Double): String {
        return when {
            v == 0.0 -> "0"
            kotlin.math.abs(v) >= 1000 -> String.format(Locale.US, "%.2f", v)
            kotlin.math.abs(v) >= 1 -> String.format(Locale.US, "%.4f", v)
            else -> String.format(Locale.US, "%.6f", v)
        }
    }
}

