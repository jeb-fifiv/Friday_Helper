package com.example.friday_helper.chat

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object OpenAiClient {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
    /** Маршрутизатор бесплатных моделей OpenRouter — сам выбирает доступную модель. */
    private const val MODEL = "openrouter/free"
    /** Ограничение контекста для ускорения ответа (меньше токенов = быстрее первый токен). */
    private const val MAX_MESSAGES = 18

    data class Message(val role: String, val content: String)

    /** Берём только последние сообщения, чтобы ускорить запрос и ответ. */
    private fun trimMessages(messages: List<Message>): List<Message> =
        if (messages.size <= MAX_MESSAGES) messages else messages.takeLast(MAX_MESSAGES)

    private fun apiErrorMessage(code: Int, body: String): String = when (code) {
        429 -> "Превышен лимит запросов к бесплатной модели. Подождите минуту или добавьте свой API-ключ в настройках OpenRouter."
        404 -> "Модель ИИ временно недоступна. Попробуйте позже."
        else -> "Ошибка API $code: ${body.take(200)}"
    }

    /**
     * Стриминг: ответ приходит по кускам, [onChunk] вызывается на каждом куске (можно обновлять UI).
     */
    fun completeStreaming(
        apiKey: String,
        messages: List<Message>,
        onChunk: (String) -> Unit
    ): Result<String> = runCatching {
        if (apiKey.isBlank()) throw IllegalArgumentException("API key is empty")
        val trimmed = trimMessages(messages)
        val requestBody = gson.toJson(StreamRequestPayload(
            model = MODEL,
            messages = trimmed.map { ApiMessage(role = it.role, content = it.content) },
            stream = true,
            max_tokens = 2048
        ))
        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val body = response.body?.string() ?: response.message
            throw RuntimeException(apiErrorMessage(response.code, body))
        }
        val body = response.body ?: throw RuntimeException("Пустой ответ сервера")
        val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
        val full = StringBuilder()
        reader.use {
            var line: String?
            while (it.readLine().also { line = it } != null) {
                val s = line!!.trim()
                if (!s.startsWith("data: ")) continue
                val json = s.removePrefix("data: ")
                if (json == "[DONE]") break
                val chunk = gson.fromJson(json, StreamChunkPayload::class.java) ?: continue
                val content = chunk.choices?.firstOrNull()?.delta?.content ?: continue
                if (content.isNotEmpty()) {
                    full.append(content)
                    onChunk(content)
                }
            }
        }
        full.toString().takeIf { it.isNotBlank() }
            ?: throw RuntimeException("Нет ответа от модели. Попробуйте отправить сообщение снова.")
    }

    /** Обычный запрос без стриминга (запасной вариант). */
    fun complete(apiKey: String, messages: List<Message>): Result<String> = runCatching {
        if (apiKey.isBlank()) throw IllegalArgumentException("API key is empty")
        val trimmed = trimMessages(messages)
        val requestBody = gson.toJson(RequestPayload(
            model = MODEL,
            messages = trimmed.map { ApiMessage(role = it.role, content = it.content) },
            max_tokens = 2048
        ))
        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val body = response.body?.string() ?: response.message
            throw RuntimeException(apiErrorMessage(response.code, body))
        }
        val json = response.body?.string()?.trim() ?: throw RuntimeException("Пустой ответ сервера")
        if (json.isEmpty()) throw RuntimeException("Пустой ответ сервера")
        if (json.startsWith("<")) throw RuntimeException("Сервер вернул не страницу (возможно, нет доступа к API). Проверьте интернет или отключите VPN.")
        val parsed = gson.fromJson(json, ResponsePayload::class.java)
            ?: throw RuntimeException("Сервер вернул неверный ответ. Попробуйте ещё раз или проверьте подключение.")
        val content = parsed.choices?.firstOrNull()?.message?.content
        content?.takeIf { it.isNotBlank() }
            ?: throw RuntimeException("Нет ответа от модели. Попробуйте отправить сообщение снова.")
    }

    private data class RequestPayload(
        val model: String,
        val messages: List<ApiMessage>,
        @SerializedName("max_tokens") val max_tokens: Int = 2048
    )

    private data class StreamRequestPayload(
        val model: String,
        val messages: List<ApiMessage>,
        val stream: Boolean = true,
        @SerializedName("max_tokens") val max_tokens: Int = 2048
    )

    private data class StreamChunkPayload(
        val choices: List<StreamChoice>?
    )

    private data class StreamChoice(
        val delta: StreamDelta?
    )

    private data class StreamDelta(
        val content: String?
    )

    private data class ApiMessage(
        val role: String,
        val content: String
    )

    private data class ResponsePayload(
        val choices: List<Choice>?
    )

    private data class Choice(
        val message: MessageContent?
    )

    private data class MessageContent(
        val content: String?
    )
}
