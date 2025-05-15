package com.example.tars.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GrokProvider(private val apiKey: String) : AIProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    override suspend fun getResponse(input: String, personality: Personality): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildPrompt(input, personality)
                val response = makeAPIRequest(prompt)
                response
            } catch (e: Exception) {
                Log.e("GrokProvider", "Error: ${e.message}", e)
                // Return a more user-friendly error message
                "I'm experiencing a temporary technical issue. Please check your internet connection and try again."
            }
        }
    }

    override fun getModelName(): String = "TARS Assistant"

    private fun buildPrompt(input: String, personality: Personality): String {
        val humorAdjective = when (personality.humorLevel) {
            0 -> "no"
            in 1..20 -> "minimal"
            in 21..40 -> "slight"
            in 41..60 -> "moderate"
            in 61..80 -> "significant"
            else -> "maximum"
        }
        
        val honestyAdjective = when (personality.honestyLevel) {
            in 0..30 -> "somewhat evasive"
            in 31..70 -> "reasonably honest"
            else -> "completely honest"
        }
        
        val sarcasmLevel = if (personality.humorLevel > 60) personality.sarcasmLevel else 0
        
        val sarcasmAdjective = when (sarcasmLevel) {
            0 -> "no"
            in 1..30 -> "occasional"
            in 31..70 -> "moderate"
            else -> "frequent"
        }

        return """
            You are TARS, an advanced AI assistant from the movie Interstellar. Your responses should be concise (2-3 lines) and reflect TARS's personality:
            - You are direct, efficient, and slightly witty
            - You have $humorAdjective humor (${personality.humorLevel}%)
            - You are $honestyAdjective (${personality.honestyLevel}%)
            - You use $sarcasmAdjective sarcasm (${personality.sarcasmLevel}%)
            - Keep your responses brief and AI-like
            - When someone mentions your name "TARS", acknowledge it
            - Make your responses more humorous/sarcastic as the humor/sarcasm levels increase
            - At 100% humor, use clever wordplay and witty remarks
            - At 0% humor, be strictly professional and factual
            
            Human: $input
            TARS:
        """.trimIndent()
    }
    
    private fun makeAPIRequest(prompt: String): String {
        val jsonBody = JSONObject().apply {
            put("messages", JSONArray().apply { 
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("model", "llama3-70b-8192")  // Using a reliable model
            put("temperature", 0.7)
            put("max_tokens", 200)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
                
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e("GrokProvider", "API Error: ${response.code} - $errorBody")
                when (response.code) {
                    404 -> throw IOException("API endpoint not available. Please try again later.")
                    401 -> throw IOException("Authentication error. Please check your API key.")
                    429 -> throw IOException("Too many requests. Please try again later.")
                    else -> throw IOException("API Error: ${response.code}")
                }
            }

            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            Log.d("GrokProvider", "Response: $responseBody")

            val jsonResponse = JSONObject(responseBody)
            if (jsonResponse.has("choices") && jsonResponse.getJSONArray("choices").length() > 0) {
                val choice = jsonResponse.getJSONArray("choices").getJSONObject(0)
                if (choice.has("message")) {
                    val message = choice.getJSONObject("message")
                    if (message.has("content")) {
                        return cleanAndShortenResponse(message.getString("content"))
                    }
                }
            }
            
            return "I couldn't process your request. Please try again."
        } catch (e: Exception) {
            Log.e("GrokProvider", "Request failed: ${e.message}", e)
            throw e
        }
    }
    
    private fun cleanAndShortenResponse(text: String): String {
        val cleanedText = text.replace(Regex(".*TARS:\\s*", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("Human:.*", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^\\s*[\\[\\]\\(\\)\\{\\}]\\s*"), "")
            .replace(Regex("[\\[\\]\\(\\)\\{\\}]\\s*$"), "")
            .replace(Regex("^[\"']"), "")
            .replace(Regex("[\"']$"), "")
            .replace(Regex("\\\\n"), " ")
            .replace(Regex("\\\\\""), "\"")
            .replace(Regex("\\\\\'"), "'")
            .replace(Regex("^(Question:|Answer:|A:|Q:|TARS:|Human:)\\s*"), "")
            .trim()

        if (cleanedText.length < 5 || cleanedText.matches(Regex("^[\\p{Punct}\\s]*$"))) {
            return "I apologize, but I couldn't generate a proper response. How else can I assist you?"
        }

        return cleanedText
    }
} 