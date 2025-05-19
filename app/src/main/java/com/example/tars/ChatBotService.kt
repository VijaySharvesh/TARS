package com.example.tars

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.Locale

class ChatBotService(private val context: Context) {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private val phoneControlService = PhoneControlService(context)

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

    @RequiresApi(Build.VERSION_CODES.O)
    fun processCommand(command: String) {
        val lowerCommand = command.lowercase()
        val response = when {
            // Navigation commands
            lowerCommand.contains("navigate") || lowerCommand.contains("directions") -> {
                handleNavigation(lowerCommand)
            }
            
            // Phone call commands
            lowerCommand.contains("call") -> {
                handlePhoneCall(lowerCommand)
            }
            
            // Volume control commands
            lowerCommand.contains("volume up") || lowerCommand.contains("increase volume") -> {
                phoneControlService.adjustVolume(true)
            }
            lowerCommand.contains("volume down") || lowerCommand.contains("decrease volume") -> {
                phoneControlService.adjustVolume(false)
            }
            lowerCommand.contains("max volume") || lowerCommand.contains("volume max") || 
            lowerCommand.contains("full volume") || lowerCommand.contains("volume 100") -> {
                phoneControlService.maxVolume()
            }
            lowerCommand.contains("min volume") || lowerCommand.contains("volume min") || 
            lowerCommand.contains("mute volume") || lowerCommand.contains("volume 0") -> {
                phoneControlService.minVolume()
            }
            
            // Brightness control commands
            lowerCommand.contains("brightness up") || lowerCommand.contains("increase brightness") -> {
                phoneControlService.increaseBrightness()
            }
            lowerCommand.contains("brightness down") || lowerCommand.contains("decrease brightness") -> {
                phoneControlService.decreaseBrightness()
            }
            
            // WiFi commands
            lowerCommand.contains("turn on wifi") || lowerCommand.contains("enable wifi") -> {
                phoneControlService.toggleWifi(true)
            }
            lowerCommand.contains("turn off wifi") || lowerCommand.contains("disable wifi") -> {
                phoneControlService.toggleWifi(false)
            }
            
            // Bluetooth commands
            lowerCommand.contains("turn on bluetooth") || lowerCommand.contains("enable bluetooth") -> {
                phoneControlService.toggleBluetooth(true)
            }
            lowerCommand.contains("turn off bluetooth") || lowerCommand.contains("disable bluetooth") -> {
                phoneControlService.toggleBluetooth(false)
            }
            
            // App launch commands
            lowerCommand.contains("open") && (!lowerCommand.contains("setting")) -> {
                val appName = extractAppName(lowerCommand)
                if (appName.isNotEmpty()) {
                    phoneControlService.launchApp(appName)
                } else {
                    "Please specify which app to open"
                }
            }
            
            // Settings commands
            lowerCommand.contains("open settings") || lowerCommand.contains("go to setting") -> {
                val settingType = extractSettingType(lowerCommand)
                phoneControlService.openSettings(settingType)
            }
            
            // General AI responses
            else -> {
                generateAIResponse(lowerCommand)
            }
        }
        speakResponse(response)
    }

    private fun extractAppName(command: String): String {
        val keywords = listOf("open", "launch", "start", "run")
        var appName = command
        
        for (keyword in keywords) {
            appName = appName.replace(keyword, "").trim()
        }
        
        // Additional cleaning
        val stopWords = listOf("the", "app", "application")
        for (word in stopWords) {
            appName = appName.replace(" $word", "").trim()
        }
        
        return appName
    }
    
    private fun extractSettingType(command: String): String {
        val keywords = listOf("open settings", "go to settings", "settings for", "open setting", "go to setting")
        var settingType = command
        
        for (keyword in keywords) {
            settingType = settingType.replace(keyword, "").trim()
        }
        
        return if (settingType.isEmpty() || settingType == command) "general" else settingType
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun generateAIResponse(command: String): String {
        // Basic AI responses based on keywords
        return when {
            command.contains("hello") || command.contains("hi") -> 
                "Hello! I'm TARS. How can I assist you today?"
            command.contains("help") -> 
                "I can help you with navigation, making phone calls, adjusting system settings like volume and brightness, controlling WiFi and Bluetooth, opening apps, and more. What would you like to do?"
            command.contains("what can you do") -> 
                "I can control your phone using voice commands. Try saying things like 'volume up', 'brightness down', 'turn on WiFi', 'open camera', 'go to settings', or 'call someone'."
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