package com.example.tars

import android.content.Context
import android.content.SharedPreferences
import java.util.Properties

object Config {
    // Default API key
    private const val OPENROUTER_API_KEY = "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"  // Replace with your OpenRouter API key
    
    private const val PREFS_NAME = "TarsConfig"
    private const val KEY_OPENROUTER = "openrouter_api_key"
    
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
        
        // If we have a stored API key, use it, otherwise default
        if (!prefs.contains(KEY_OPENROUTER)) {
            setOpenRouterKey(OPENROUTER_API_KEY)
        }
    }
    
    fun setOpenRouterKey(key: String) {
        prefs.edit().putString(KEY_OPENROUTER, key).apply()
    }
    
    fun getOpenRouterKey(): String {
        return prefs.getString(KEY_OPENROUTER, OPENROUTER_API_KEY) ?: OPENROUTER_API_KEY
    }
} 