package com.example.tars

import android.content.Context
import android.content.SharedPreferences
import java.util.Properties

object Config {
    // Default API key
    private const val GROK_API_KEY = "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"
    
    private const val PREFS_NAME = "TarsConfig"
    private const val KEY_GROK = "grok_api_key"
    
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
        if (!prefs.contains(KEY_GROK)) {
            setGrokKey(GROK_API_KEY)
        }
    }
    
    fun setGrokKey(key: String) {
        prefs.edit().putString(KEY_GROK, key).apply()
    }
    
    fun getGrokKey(): String {
        return prefs.getString(KEY_GROK, GROK_API_KEY) ?: GROK_API_KEY
    }
} 