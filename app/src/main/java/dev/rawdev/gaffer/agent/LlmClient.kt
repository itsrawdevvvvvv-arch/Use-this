package dev.rawdev.gaffer.agent

import dev.rawdev.gaffer.data.Prefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to any OpenAI-compatible /chat/completions endpoint:
 * OpenAI, Groq, OpenRouter, Together, Fireworks, Sarvam, vLLM, Ollama,
 * LM Studio, and so on. Only the base URL and model name change.
 */
class LlmClient(private val prefs: Prefs) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun complete(history: JSONArray, tools: JSONArray?): JSONObject {
        if (prefs.apiKey.isBlank()) {
            throw IOException("No API key set. Open Settings and paste one in.")
        }
        val body = JSONObject().apply {
            put("model", prefs.model)
            put("messages", history)
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
            put("temperature", prefs.temperature.toDouble())
            put("stream", false)
        }
        val url = prefs.baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + prefs.apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try {
                    JSONObject(raw).optJSONObject("error")?.optString("message")
                } catch (e: Exception) {
                    null
                }
                throw IOException("HTTP ${'$'}{response.code}: ${'$'}{detail ?: raw.take(400)}")
            }
            val json = try {
                JSONObject(raw)
            } catch (e: Exception) {
                throw IOException("The endpoint did not return JSON. Check the base URL.")
            }
            if (json.has("error") && !json.has("choices")) {
                throw IOException(json.optJSONObject("error")?.optString("message") ?: "Endpoint error")
            }
            return json
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
