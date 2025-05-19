package com.example.tars

import android.content.Context
import android.content.SharedPreferences
import java.util.Properties

object Config {
    // Constants
    private const val PREFS_NAME = "TarsConfig"
    
    // OpenRouter configuration
    private const val API_KEY = "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"
    const val MODEL_NAME = "meta-llama/llama-3-8b-instruct"
    const val MAX_TOKENS = 1000
    const val TEMPERATURE = 0.7
    const val TOP_P = 0.9
    const val FREQUENCY_PENALTY = 0.1
    const val PRESENCE_PENALTY = 0.1
    const val API_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    
    // Preferences
    private lateinit var prefs: SharedPreferences
    private var properties: Properties? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            properties = Properties().apply {
                context.assets.open("config.properties").use { load(it) }
            }
        } catch (e: Exception) {
            // Ignore if config.properties doesn't exist
        }
    }
    
    fun getModelConfig(): Map<String, Any> {
        return mapOf(
            "model" to MODEL_NAME,
            "max_tokens" to MAX_TOKENS,
            "temperature" to TEMPERATURE,
            "top_p" to TOP_P,
            "frequency_penalty" to FREQUENCY_PENALTY,
            "presence_penalty" to PRESENCE_PENALTY
        )
    }
    
    fun getApiHeaders(): Map<String, String> {
        return mapOf(
            "Authorization" to "Bearer $API_KEY",
            "HTTP-Referer" to "https://github.com/tars-assistant",
            "X-Title" to "TARS Android Assistant",
            "Content-Type" to "application/json"
        )
    }
    
    fun getApiEndpoint(): String = API_BASE_URL
} 