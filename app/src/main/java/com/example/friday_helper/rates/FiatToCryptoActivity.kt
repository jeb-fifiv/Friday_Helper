package com.example.friday_helper.rates

import android.text.InputType
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.friday_helper.databinding.ActivityFiatToCryptoBinding
import com.example.friday_helper.settings.SettingsRepository
import com.example.friday_helper.settings.ThemeApplier
import com.example.friday_helper.settings.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FiatToCryptoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFiatToCryptoBinding
    private lateinit var settings: SettingsRepository

    private var lastSource: RatesResult<*>? = null
    private var lastUpdatedAt: Long? = null

    private data class ProviderOption(val id: String, val title: String)
    private val providers = listOf(
        ProviderOption(id = "coingecko", title = "CoinGecko"),
        ProviderOption(id = "coinbase", title = "Coinbase"),
    )

    private val fiats = listOf("RUB", "USD", "EUR")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFiatToCryptoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsRepository(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        binding.btnBack.setOnClickListener { finish() }

        setupDropdowns()
        setupButtons()
        applyTheme()
    }

    private fun setupDropdowns() {
        fun setupNonEditable(dd: AutoCompleteTextView) {
            dd.inputType = InputType.TYPE_NULL
            dd.keyListener = null
            dd.isCursorVisible = false
            dd.setOnClickListener { dd.showDropDown() }
            dd.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) dd.showDropDown() }
        }

        setupNonEditable(binding.providerDropdown)
        setupNonEditable(binding.fiatDropdown)
        setupNonEditable(binding.cryptoDropdown)

        val providerTitles = providers.map { it.title }
        binding.providerDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, providerTitles))
        val savedProvider = settings.getCryptoProvider()
        binding.providerDropdown.setText(providers.find { it.id == savedProvider }?.title ?: providers.first().title, false)
        binding.providerDropdown.setOnItemClickListener { _, _, pos, _ ->
            val opt = providers.getOrNull(pos) ?: return@setOnItemClickListener
            settings.setCryptoProvider(opt.id)
        }

        binding.fiatDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, fiats))
        val vs = settings.getCryptoVsFiat().uppercase().takeIf { it in fiats } ?: "RUB"
        binding.fiatDropdown.setText(vs, false)
        binding.fiatDropdown.setOnItemClickListener { _, _, pos, _ ->
            val v = fiats.getOrNull(pos) ?: return@setOnItemClickListener
            settings.setCryptoVsFiat(v)
        }

        val cryptos = settings.getSelectedCryptos().toList().sorted()
        val labels = cryptos.map { CryptoCatalog.labelForId(it) }
        binding.cryptoDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
        binding.cryptoDropdown.setText(labels.firstOrNull() ?: "BTC (Bitcoin)", false)
    }

    private fun setupButtons() {
        binding.btnConvert.setOnClickListener { convert() }
    }

    override fun onResume() {
        super.onResume()
        // если пользователь поменял выбранные монеты в настройках крипты — обновим список
        val cryptos = settings.getSelectedCryptos().toList().sorted()
        val labels = cryptos.map { CryptoCatalog.labelForId(it) }
        binding.cryptoDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
        if (binding.cryptoDropdown.text.isNullOrBlank() && labels.isNotEmpty()) {
            binding.cryptoDropdown.setText(labels.first(), false)
        }
    }

    private fun applyTheme() {
        ThemeManager.init(this)
        ThemeManager.theme.observe(this) { cfg ->
            ThemeApplier.applyToToolbar(binding.toolbar, cfg)
            ThemeApplier.applyBackground(binding.rootFiatToCrypto, cfg)
            ThemeApplier.applyFont(binding.rootFiatToCrypto, this, cfg.fontResId)
            val txt = cfg.accentColor
            val surface = ThemeApplier.surfaceColorFor(cfg.backgroundColor)

            binding.tvTitle.setTextColor(txt)
            binding.tvProviderLabel.setTextColor(txt)
            binding.tvFiatLabel.setTextColor(txt)
            binding.tvCryptoLabel.setTextColor(txt)
            binding.tvResult.setTextColor(txt)
            binding.tvAttribution.setTextColor(txt)

            binding.box.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(surface)
                val px = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
                setStroke(px, cfg.accentColor)
            }

            val btnBg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(surface)
                val px = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
                setStroke(px, cfg.accentColor)
            }
            binding.btnConvert.background = btnBg
            binding.btnConvert.setTextColor(txt)
            binding.btnConvert.backgroundTintList = null

            listOf(binding.providerDropdown, binding.fiatDropdown, binding.cryptoDropdown).forEach {
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

            // text input
            ThemeApplier.styleTextInput(binding.etFiatAmount.parent.parent as com.google.android.material.textfield.TextInputLayout, cfg)
            binding.etFiatAmount.setTextColor(txt)
        }
    }

    private fun convert() {
        if (!settings.isCryptoEnabled()) {
            Toast.makeText(this, "Включите «Криптовалюты» в Настройках", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = binding.etFiatAmount.text?.toString()?.trim()?.replace(",", ".")?.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            binding.tvResult.text = "Введите сумму"
            return
        }

        val vs = binding.fiatDropdown.text?.toString()?.trim()?.uppercase().orEmpty()
        val providerId = providers.find { it.title == binding.providerDropdown.text?.toString() }?.id ?: settings.getCryptoProvider()

        val cryptoLabel = binding.cryptoDropdown.text?.toString().orEmpty()
        val coin = CryptoCatalog.coins.firstOrNull { CryptoCatalog.labelForId(it.id) == cryptoLabel }
        val cryptoId = coin?.id ?: "bitcoin"

        binding.btnConvert.isEnabled = false
        binding.tvAttribution.text = "Загрузка..."

        lifecycleScope.launch(Dispatchers.IO) {
            val res = RatesClients.fetchCryptoPrices(setOf(cryptoId), vs, providerId)
            withContext(Dispatchers.Main) {
                binding.btnConvert.isEnabled = true
                if (res == null) {
                    binding.tvResult.text = "Не удалось получить цену"
                    binding.tvAttribution.text = "Источник: —"
                    return@withContext
                }
                lastSource = res
                lastUpdatedAt = System.currentTimeMillis()
                val price = res.data[cryptoId]
                if (price == null || price <= 0.0) {
                    binding.tvResult.text = "Нет данных по цене"
                } else {
                    val cryptoAmount = amount / price
                    val symbol = CryptoCatalog.byId(cryptoId)?.symbol ?: cryptoId
                    binding.tvResult.text = "${formatMoney(amount)} $vs ≈ ${formatMoney(cryptoAmount)} $symbol"
                }
                updateAttribution()
            }
        }
    }

    private fun updateAttribution() {
        val s = lastSource as? RatesResult<*>
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

    private fun formatMoney(v: Double): String {
        return String.format(Locale.US, "%.8f", v).trimEnd('0').trimEnd('.')
    }
}

