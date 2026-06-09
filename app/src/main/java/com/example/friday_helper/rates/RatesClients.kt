package com.example.friday_helper.rates

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RatesResult<T>(
    val data: T,
    val sourceName: String,
    val sourceUrl: String
)

object RatesClients {

    /**
     * Returns map of currencyCode -> rate, where rate is "1 base = rate code".
     */
    fun fetchFiatRates(base: String, providerId: String): RatesResult<Map<String, Double>>? {
        val baseUp = base.uppercase()
        return when (providerId) {
            "erapi" -> fetchFiatFromErApi(baseUp)
            else -> fetchFiatFromFrankfurter(baseUp) // default
        }
    }

    /**
     * Returns map of cryptoId -> price in [vsFiat] for 1 coin.
     * cryptoId is CoinGecko-style id (e.g., "bitcoin"), but we try to support multiple providers.
     */
    fun fetchCryptoPrices(cryptoIds: Set<String>, vsFiat: String, providerId: String): RatesResult<Map<String, Double>>? {
        val ids = cryptoIds.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        if (ids.isEmpty()) return RatesResult(emptyMap(), sourceName = providerId, sourceUrl = "")
        val vs = vsFiat.uppercase()
        return when (providerId) {
            "coinbase" -> fetchCryptoFromCoinbase(ids, vs)
            else -> fetchCryptoFromCoinGecko(ids, vs) // default
        }
    }

    private fun fetchFiatFromFrankfurter(base: String): RatesResult<Map<String, Double>>? {
        val url = URL("https://api.frankfurter.app/latest?from=$base")
        val json = httpGetJson(url) ?: return null
        val rates = json.optJSONObject("rates") ?: return null
        val map = mutableMapOf<String, Double>()
        map[base] = 1.0
        val keys = rates.keys()
        while (keys.hasNext()) {
            val k = keys.next().uppercase()
            val v = rates.optDouble(k, Double.NaN)
            if (!v.isNaN()) map[k] = v
        }
        return RatesResult(map, sourceName = "Frankfurter", sourceUrl = "https://www.frankfurter.app/")
    }

    private fun fetchFiatFromErApi(base: String): RatesResult<Map<String, Double>>? {
        val url = URL("https://open.er-api.com/v6/latest/$base")
        val json = httpGetJson(url) ?: return null
        if (json.optString("result") != "success") return null
        val conv = json.optJSONObject("conversion_rates") ?: return null
        val map = mutableMapOf<String, Double>()
        map[base] = 1.0
        val keys = conv.keys()
        while (keys.hasNext()) {
            val k = keys.next().uppercase()
            val v = conv.optDouble(k, Double.NaN)
            if (!v.isNaN()) map[k] = v
        }
        return RatesResult(map, sourceName = "ER-API", sourceUrl = "https://www.exchangerate-api.com/")
    }

    private fun fetchCryptoFromCoinGecko(cryptoIds: Set<String>, vsFiat: String): RatesResult<Map<String, Double>>? {
        val ids = cryptoIds.joinToString(",")
        val vs = vsFiat.lowercase()
        val url = URL("https://api.coingecko.com/api/v3/simple/price?ids=$ids&vs_currencies=$vs")
        val json = httpGetJson(url) ?: return null
        val map = mutableMapOf<String, Double>()
        for (id in cryptoIds) {
            val obj = json.optJSONObject(id) ?: continue
            val price = obj.optDouble(vs, Double.NaN)
            if (!price.isNaN()) map[id] = price
        }
        return RatesResult(map, sourceName = "CoinGecko", sourceUrl = "https://www.coingecko.com/")
    }

    private fun fetchCryptoFromCoinbase(cryptoIds: Set<String>, vsFiat: String): RatesResult<Map<String, Double>>? {
        val map = mutableMapOf<String, Double>()
        for (id in cryptoIds) {
            val coinbase = CryptoCatalog.coinbaseCodeForId(id) ?: continue
            val url = URL("https://api.coinbase.com/v2/exchange-rates?currency=$coinbase")
            val json = httpGetJson(url) ?: continue
            val data = json.optJSONObject("data") ?: continue
            val rates = data.optJSONObject("rates") ?: continue
            val raw = rates.optString(vsFiat.uppercase(), "")
            val price = raw.toDoubleOrNull()
            if (price != null) map[id] = price
        }
        return RatesResult(map, sourceName = "Coinbase", sourceUrl = "https://www.coinbase.com/")
    }

    private fun httpGetJson(url: URL): JSONObject? {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            requestMethod = "GET"
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Hoctopus/1.0 (Android)")
        }
        return try {
            conn.connect()
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}

object CryptoCatalog {
    data class Coin(val id: String, val symbol: String, val name: String, val coinbaseCode: String?)

    val coins: List<Coin> = listOf(
        Coin(id = "bitcoin", symbol = "BTC", name = "Bitcoin", coinbaseCode = "BTC"),
        Coin(id = "ethereum", symbol = "ETH", name = "Ethereum", coinbaseCode = "ETH"),
        Coin(id = "toncoin", symbol = "TON", name = "Toncoin", coinbaseCode = "TON"),
        Coin(id = "solana", symbol = "SOL", name = "Solana", coinbaseCode = "SOL"),
        Coin(id = "tether", symbol = "USDT", name = "Tether", coinbaseCode = "USDT"),
    )

    fun byId(id: String): Coin? = coins.find { it.id == id }
    fun labelForId(id: String): String {
        val c = byId(id)
        return if (c != null) "${c.symbol} (${c.name})" else id
    }

    fun coinbaseCodeForId(id: String): String? = byId(id)?.coinbaseCode
}

