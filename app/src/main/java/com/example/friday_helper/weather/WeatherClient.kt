package com.example.friday_helper.weather

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherData(val temperatureC: Double, val description: String)

object WeatherClient {
    fun describe(code: Int): String = when (code) {
        0 -> "ясно"
        1, 2 -> "переменная облачность"
        3 -> "пасмурно"
        45, 48 -> "туман"
        51, 53, 55 -> "морось"
        61, 63, 65 -> "дождь"
        71, 73, 75 -> "снег"
        80, 81, 82 -> "ливень"
        95 -> "гроза"
        96, 99 -> "гроза с градом"
        else -> "погода"
    }

    fun fetch(lat: Double, lon: Double): WeatherData? {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "GET"
        conn.doInput = true
        return try {
            conn.connect()
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val cw = json.getJSONObject("current_weather")
                val temp = cw.getDouble("temperature")
                val code = cw.getInt("weathercode")
                WeatherData(temp, describe(code))
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}

