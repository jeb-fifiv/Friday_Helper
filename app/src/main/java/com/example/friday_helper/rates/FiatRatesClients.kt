package com.example.friday_helper.rates

import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL

data class SourceInfo(val name: String, val url: String)

/**
 * Валюты через российские источники (ЦБ РФ).
 *
 * Возвращает карту code -> rate, где rate это "1 base = rate code".
 */
object FiatRatesClients {

    fun fetch(base: String, providerId: String): RatesResult<Map<String, Double>>? {
        val b = base.uppercase()
        val (rubPerUnit, src) = when (providerId) {
            "cbr_ru" -> fetchRubPerUnitFromCbrXmlDailyRu()
            else -> fetchRubPerUnitFromCbrOfficialXml() // default: cbr
        } ?: return null

        val map = convertRubPerUnitToBaseRates(rubPerUnit, b)
        return RatesResult(map, sourceName = src.name, sourceUrl = src.url)
    }

    private fun convertRubPerUnitToBaseRates(rubPerUnit: Map<String, Double>, base: String): Map<String, Double> {
        val rub = "RUB"
        val normalized = rubPerUnit.toMutableMap()
        normalized[rub] = 1.0
        val rubPerBase = normalized[base] ?: 1.0
        val out = mutableMapOf<String, Double>()
        for ((code, rubPer) in normalized) {
            if (rubPer <= 0.0) continue
            // 1 base = (rubPerBase / rubPerTarget) target
            out[code] = rubPerBase / rubPer
        }
        // ensure base itself is present
        out[base] = 1.0
        return out
    }

    /**
     * Официальный сайт ЦБ РФ: XML_daily.asp
     */
    private fun fetchRubPerUnitFromCbrOfficialXml(): Pair<Map<String, Double>, SourceInfo>? {
        val url = URL("https://www.cbr.ru/scripts/XML_daily.asp")
        val xml = httpGetText(url) ?: return null
        val map = parseCbrXmlDaily(xml)
        return map to SourceInfo(name = "ЦБ РФ", url = "https://www.cbr.ru/")
    }

    /**
     * Российское зеркало (удобное, быстрое). Формально тоже про ЦБ РФ.
     */
    private fun fetchRubPerUnitFromCbrXmlDailyRu(): Pair<Map<String, Double>, SourceInfo>? {
        val url = URL("https://www.cbr-xml-daily.ru/daily_json.js")
        val jsonText = httpGetText(url) ?: return null
        val map = parseCbrXmlDailyRuJson(jsonText)
        return map to SourceInfo(name = "cbr-xml-daily.ru (данные ЦБ РФ)", url = "https://www.cbr-xml-daily.ru/")
    }

    private fun parseCbrXmlDailyRuJson(text: String): Map<String, Double> {
        val json = JSONObject(text)
        val valute = json.optJSONObject("Valute") ?: return emptyMap()
        val out = mutableMapOf<String, Double>()
        val keys = valute.keys()
        while (keys.hasNext()) {
            val code = keys.next().uppercase()
            val obj = valute.optJSONObject(code) ?: continue
            val nominal = obj.optDouble("Nominal", 1.0).takeIf { it > 0.0 } ?: 1.0
            val value = obj.optDouble("Value", Double.NaN)
            if (!value.isNaN()) {
                out[code] = value / nominal
            }
        }
        out["RUB"] = 1.0
        return out
    }

    private fun parseCbrXmlDaily(xml: String): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())

        var event = parser.eventType
        var currentCharCode: String? = null
        var currentNominal: Int? = null
        var currentValue: Double? = null

        fun flush() {
            val code = currentCharCode
            val nominal = currentNominal
            val value = currentValue
            if (!code.isNullOrBlank() && nominal != null && nominal > 0 && value != null && value > 0) {
                out[code.uppercase()] = value / nominal.toDouble()
            }
            currentCharCode = null
            currentNominal = null
            currentValue = null
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "Valute" -> {
                            // new block
                            currentCharCode = null
                            currentNominal = null
                            currentValue = null
                        }
                        "CharCode" -> currentCharCode = parser.nextText()
                        "Nominal" -> currentNominal = parser.nextText().trim().toIntOrNull()
                        "Value" -> {
                            val raw = parser.nextText().trim().replace(',', '.')
                            currentValue = raw.toDoubleOrNull()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "Valute") {
                        flush()
                    }
                }
            }
            event = parser.next()
        }

        out["RUB"] = 1.0
        return out
    }

    private fun httpGetText(url: URL): String? {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            requestMethod = "GET"
            doInput = true
            setRequestProperty("User-Agent", "Hoctopus/1.0 (Android)")
        }
        return try {
            conn.connect()
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}

