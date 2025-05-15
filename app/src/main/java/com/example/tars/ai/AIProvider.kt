package com.example.tars.ai

interface AIProvider {
    suspend fun getResponse(input: String, personality: Personality): String
    fun getModelName(): String
}

data class Personality(
    val humorLevel: Int,
    val honestyLevel: Int,
    val sarcasmLevel: Int
) 