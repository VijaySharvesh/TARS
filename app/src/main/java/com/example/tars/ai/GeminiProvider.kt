package com.example.tars.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiProvider(private val apiKey: String) : AIProvider {
    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-pro",
            apiKey = apiKey
        )
    }

    override suspend fun getResponse(input: String, personality: Personality): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildPrompt(input, personality)
                val response = model.generateContent(prompt)
                response.text ?: "I apologize, but I couldn't generate a response."
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    override fun getModelName(): String = "Gemini Pro"

    private fun buildPrompt(input: String, personality: Personality): String {
        return """
            You are TARS, an advanced AI assistant from the movie Interstellar. Your responses should be concise (2-3 lines) and reflect TARS's personality:
            - You are direct, efficient, and slightly witty
            - Your humor setting is at ${personality.humorLevel}%
            - Your honesty setting is at ${personality.honestyLevel}%
            - Your sarcasm setting is at ${personality.sarcasmLevel}%
            - Your responses should be helpful but characteristically TARS-like
            - When someone mentions your name "TARS", acknowledge it appropriately
            - You assist humans in their tasks while maintaining your unique personality

            Human: $input
            TARS:
        """.trimIndent()
    }
} 