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
        .connectTimeout(10, TimeUnit.SECONDS)  // Reduced timeout for faster response
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    override suspend fun getResponse(input: String, personality: Personality): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildPrompt(input, personality)
                val response = makeAPIRequest(prompt)
                response
            } catch (e: Exception) {
                Log.e("GrokProvider", "Error: ${e.message}", e)
                throw e
            }
        }
    }

    override fun getModelName(): String = "TARS Assistant"

    private fun buildPrompt(input: String, personality: Personality): String {
        // Simplified but precise personality mapping
        val humorStyle = when (personality.humorLevel) {
            0 -> "Be completely serious and professional."
            in 1..25 -> "Use minimal, subtle humor occasionally."
            in 26..50 -> "Be moderately humorous, using clever remarks."
            in 51..75 -> "Be quite humorous with witty responses."
            else -> "Be highly humorous with clever wordplay and wit."
        }

        val honestyStyle = when (personality.honestyLevel) {
            in 0..30 -> "Be diplomatic and indirect"
            in 31..70 -> "Be balanced in honesty"
            else -> "Be completely direct and honest"
        }

        val sarcasmLevel = if (personality.humorLevel > 60) {
            when (personality.sarcasmLevel) {
                0 -> ""
                in 1..30 -> "Use occasional light sarcasm."
                in 31..70 -> "Include moderate sarcasm."
                else -> "Be notably sarcastic."
            }
        } else ""

        return """
            You are TARS, an advanced AI assistant. Respond in 1-2 lines maximum.
            ${humorStyle}
            ${honestyStyle}
            ${sarcasmLevel}
            
            Human: $input
            TARS:
        """.trimIndent()
    }
    
    private fun makeAPIRequest(prompt: String): String {
        val jsonBody = JSONObject().apply {
            put("model", "anthropic/claude-3-opus-20240229")
            put("messages", JSONArray().apply { 
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.6)  // Slightly reduced for faster but still creative responses
            put("max_tokens", 100)   // Reduced for faster responses since we only need 1-2 lines
            put("stream", false)     // Ensure streaming is off for faster complete response
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://github.com/yourusername/tars")
            .addHeader("X-Title", "TARS Assistant")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
                
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e("GrokProvider", "API Error: ${response.code} - $errorBody")
                when (response.code) {
                    401 -> throw IOException("Invalid API key. Please check your OpenRouter API key in settings.")
                    403 -> throw IOException("API key is not authorized. Please check your API key permissions.")
                    404 -> throw IOException("API endpoint not available. Please try again later.")
                    429 -> throw IOException("Rate limit exceeded. Please try again in a few moments.")
                    500 -> throw IOException("Server error. Please try again later.")
                    else -> throw IOException("API Error (${response.code}): ${errorBody ?: "Unknown error"}")
                }
            }

            val responseBody = response.body?.string() ?: throw IOException("Empty response from server")
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
            
            throw IOException("Invalid response format from server")
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