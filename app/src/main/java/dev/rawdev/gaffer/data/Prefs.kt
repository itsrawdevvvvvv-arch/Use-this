package dev.rawdev.gaffer.data

import android.content.Context

/**
 * App-private preference store. The API key lives in this app's private
 * SharedPreferences file, which no other app on the device can read.
 */
class Prefs private constructor(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("gaffer_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString(KEY_API, "") ?: ""
        set(v) = sp.edit().putString(KEY_API, v.trim()).apply()

    var baseUrl: String
        get() = sp.getString(KEY_BASE, DEFAULT_BASE) ?: DEFAULT_BASE
        set(v) = sp.edit().putString(KEY_BASE, v.trim().ifBlank { DEFAULT_BASE }).apply()

    var model: String
        get() = sp.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = sp.edit().putString(KEY_MODEL, v.trim().ifBlank { DEFAULT_MODEL }).apply()

    var requireConfirmation: Boolean
        get() = sp.getBoolean(KEY_CONFIRM, true)
        set(v) = sp.edit().putBoolean(KEY_CONFIRM, v).apply()

    var maxSteps: Int
        get() = sp.getInt(KEY_STEPS, 24)
        set(v) = sp.edit().putInt(KEY_STEPS, v.coerceIn(4, 60)).apply()

    var temperature: Float
        get() = sp.getFloat(KEY_TEMP, 0.2f)
        set(v) = sp.edit().putFloat(KEY_TEMP, v.coerceIn(0f, 1.5f)).apply()

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        private const val KEY_API = "api_key"
        private const val KEY_BASE = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_CONFIRM = "confirm"
        private const val KEY_STEPS = "max_steps"
        private const val KEY_TEMP = "temperature"

        const val DEFAULT_BASE = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"

        @Volatile
        private var inst: Prefs? = null

        fun of(context: Context): Prefs =
            inst ?: synchronized(this) {
                inst ?: Prefs(context).also { inst = it }
            }
    }
}
