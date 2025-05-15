package com.example.tars

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class ChatBotService(private val context: Context) {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    init {
        initializeTextToSpeech()
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("ChatBot", "Language not supported")
                }
                isInitialized = true
            } else {
                Log.e("ChatBot", "TextToSpeech initialization failed")
            }
        }
    }

    fun processCommand(command: String) {
        val response = when {
            command.contains("navigate") || command.contains("directions") -> {
                handleNavigation(command)
            }
            command.contains("call") -> {
                handlePhoneCall(command)
            }
            else -> {
                generateAIResponse(command)
            }
        }
        speakResponse(response)
    }

    private fun handleNavigation(command: String): String {
        // Extract location from command
        val location = command.replace(Regex("navigate|directions|to|in|at"), "").trim()
        if (location.isNotEmpty()) {
            val uri = "google.navigation:q=$location"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.setPackage("com.google.android.apps.maps")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            return "Starting navigation to $location"
        }
        return "Please specify a location to navigate to"
    }

    private fun handlePhoneCall(command: String): String {
        // Extract phone number or contact name from command
        val phoneNumber = command.replace(Regex("call|phone|dial"), "").trim()
        if (phoneNumber.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return "Initiating call to $phoneNumber"
        }
        return "Please specify a phone number to call"
    }

    private fun generateAIResponse(command: String): String {
        // Basic AI responses based on keywords
        return when {
            command.contains("hello") || command.contains("hi") -> 
                "Hello! I'm TARS. How can I assist you today?"
            command.contains("help") -> 
                "I can help you with navigation, making phone calls, and answering questions. What would you like to do?"
            command.contains("weather") -> 
                "I can check the weather for you. Would you like to know the current weather?"
            command.contains("time") -> 
                "The current time is ${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}"
            command.contains("date") -> 
                "Today's date is ${java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"))}"
            else -> 
                "I understand you're saying '$command'. How can I help you with that?"
        }
    }

    private fun speakResponse(response: String) {
        if (isInitialized) {
            textToSpeech?.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }
} 