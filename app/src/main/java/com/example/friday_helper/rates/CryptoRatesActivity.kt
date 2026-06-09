package com.example.friday_helper.rates

import android.text.InputType
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.friday_helper.databinding.ActivityCryptoRatesBinding
import com.example.friday_helper.settings.SettingsRepository
import com.example.friday_helper.settings.ThemeApplier
import com.example.friday_helper.settings.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CryptoRatesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCryptoRatesBinding
    private lateinit var settings: SettingsRepository
    private val adapter = RateRowAdapter()

    private var prices: Map<String, Double> = emptyMap()
    private var source: RatesResult<*>? = null
    private var lastUpdatedAt: Long? = null

    private data class ProviderOption(val id: String, val title: String)
    private val providers = listOf(
        ProviderOption(id = "coingecko", title = "CoinGecko"),
        ProviderOption(id = "coinbase", title = "Coinbase"),
    )

    private val vsFiats = listOf("RUB", "USD", "EUR")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCryptoRatesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsRepository(this)

        if (!settings.isCryptoEnabled()) {
            Toast.makeText(this, "Включите «Криптовалюты» в Настройках", Toast.LENGTH_SHORT).show()
        }

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

        setupNonEditable(binding.vsDropdown)
        setupNonEditable(binding.providerDropdown)

        binding.vsDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, vsFiats))
        binding.vsDropdown.setText(settings.getCryptoVsFiat().uppercase(), false)
        binding.vsDropdown.setOnItemClickListener { _, _, pos, _ ->
            val vs = vsFiats.getOrNull(pos) ?: return@setOnItemClickListener
            settings.setCryptoVsFiat(vs)
            refresh()
        }

        val titles = providers.map { it.title }
        binding.providerDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, titles))
        val saved = settings.getCryptoProvider()
        binding.providerDropdown.setText(providers.find { it.id == saved }?.title ?: providers.first().title, false)
        binding.providerDropdown.setOnItemClickListener { _, _, pos, _ ->
            val opt = providers.getOrNull(pos) ?: return@setOnItemClickListener
            settings.setCryptoProvider(opt.id)
            refresh()
        }
    }

    private fun setupButtons() {
        binding.btnChooseCryptos.setOnClickListener { openCryptosDialog() }
        binding.btnRefresh.setOnClickListener { refresh() }
    }

    private fun applyTheme() {
        ThemeManager.init(this)
        ThemeManager.theme.observe(this) { cfg ->
            ThemeApplier.applyToToolbar(binding.toolbar, cfg)
            ThemeApplier.applyBackground(binding.rootCryptoRates, cfg)
            ThemeApplier.applyFont(binding.rootCryptoRates, this, cfg.fontResId)
            val txt = cfg.accentColor
            val surface = ThemeApplier.surfaceColorFor(cfg.backgroundColor)

            binding.tvTitle.setTextColor(txt)
            binding.tvSettingsHeader.setTextColor(txt)
            binding.tvVsLabel.setTextColor(txt)
            binding.tvProviderLabel.setTextColor(txt)
            binding.tvAttribution.setTextColor(txt)

            binding.boxSettings.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(surface)
                val px = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
                setStroke(px, cfg.accentColor)
            }

            listOf(binding.btnChooseCryptos, binding.btnRefresh).forEach { v ->
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

            listOf(binding.vsDropdown, binding.providerDropdown).forEach {
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
        if (!settings.isCryptoEnabled()) {
            adapter.submit(emptyList())
            binding.tvAttribution.text = "Источник: —"
            return
        }

        binding.btnRefresh.isEnabled = false
        binding.tvAttribution.text = "Загрузка..."

        val cryptos = settings.getSelectedCryptos()
        val vs = settings.getCryptoVsFiat()
        val provider = settings.getCryptoProvider()

        lifecycleScope.launch(Dispatchers.IO) {
            val res = RatesClients.fetchCryptoPrices(cryptos, vs, provider)
            withContext(Dispatchers.Main) {
                binding.btnRefresh.isEnabled = true
                if (res == null) {
                    prices = emptyMap()
                    source = null
                    lastUpdatedAt = null
                    adapter.submit(emptyList())
                    binding.tvAttribution.text = "Источник: —"
                    return@withContext
                }
                prices = res.data
                source = res
                lastUpdatedAt = System.currentTimeMillis()

                val vsUp = vs.uppercase()
                val rows = cryptos.toList().sorted().map { id ->
                    val label = CryptoCatalog.labelForId(id)
                    val p = prices[id]
                    if (p == null) {
                        RateRow(label, "нет данных", "—")
                    } else {
                        RateRow(label, "1 = ${formatRate(p)} $vsUp", "${formatRate(p)} $vsUp")
                    }
                }
                adapter.submit(rows)
                updateAttribution()
            }
        }
    }

    private fun openCryptosDialog() {
        val all = CryptoCatalog.coins
        val labels = all.map { CryptoCatalog.labelForId(it.id) }
        val current = settings.getSelectedCryptos().toSet()
        val checked = all.map { current.contains(it.id) }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Выберите криптовалюты")
            .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Сохранить") { _, _ ->
                val set = mutableSetOf<String>()
                for (i in all.indices) if (checked[i]) set.add(all[i].id)
                if (set.isEmpty()) set.add("bitcoin")
                settings.setSelectedCryptos(set)
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

