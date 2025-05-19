package com.example.tars
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.MotionEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tars.ai.AIProvider
import com.example.tars.ai.OpenRouterProvider
import com.example.tars.ai.Personality
import com.example.tars.databinding.ActivityChatBotBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class ChatBotActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBotBinding
    private lateinit var messageAdapter: MessageAdapter
    private var textToSpeech: TextToSpeech? = null
    private var isProcessing = false
    private var isListening = false
    private var humorSetting = 100
    private var honestySetting = 100
    private var sarcasmSetting = 0
    
    private var aiProvider: AIProvider? = null

    private var speechRecognizer: SpeechRecognizer? = null

    private lateinit var phoneControlService: PhoneControlService

    // Add variables to track if user is interacting with the UI
    private var isUserInteracting = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Config with API keys
        Config.init(this)

        // Initialize phone control service
        phoneControlService = PhoneControlService(this)

        setupRecyclerView()
        setupTextToSpeech()
        setupSpeechRecognizer()
        setupClickListeners()
        setupHumorSlider()
        checkPermissions()
        
        // Setup AI provider first to ensure welcome message appears
        setupAIProvider()
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.shutdown()
        
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e("ChatBotActivity", "Error destroying speech recognizer: ${e.message}")
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter()
        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatBotActivity)
            adapter = messageAdapter
        }
    }

    private fun setupTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.apply {
                    language = Locale.US
                    setPitch(1.0f)
                    setSpeechRate(1.0f)
                }
            } else {
                Toast.makeText(this, "Text to speech initialization failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer?.setRecognitionListener(createRecognitionListener())
                Log.d("ChatBotActivity", "Speech recognizer initialized successfully")
            } else {
                Log.e("ChatBotActivity", "Speech recognition not available on this device")
                Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("ChatBotActivity", "Error initializing speech recognizer: ${e.message}")
            Toast.makeText(this, "Error initializing speech: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            binding.statusText.text = "Listening..."
            binding.micButton.setBackgroundResource(R.drawable.mic_button_pressed)
        }

        override fun onBeginningOfSpeech() {
            Log.d("ChatBotActivity", "Speech started")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Could implement visual feedback based on volume
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Not needed for basic implementation
        }

        override fun onEndOfSpeech() {
            Log.d("ChatBotActivity", "Speech ended")
            // UI will be updated in onResults or onError
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error"
            }
            
            Log.e("ChatBotActivity", "Speech recognition error: $errorMessage ($error)")
            
            // Only show toast for critical errors
            if (error != SpeechRecognizer.ERROR_NO_MATCH && 
                error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Toast.makeText(this@ChatBotActivity, errorMessage, Toast.LENGTH_SHORT).show()
            }
            
            // Always reset UI state
            isListening = false
            binding.statusText.text = "Hold to speak"
            binding.micButton.setBackgroundResource(R.drawable.mic_button_background)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                Log.d("ChatBotActivity", "Speech recognized: $spokenText")
                processUserInput(spokenText)
            }
            
            // Always reset UI state
            isListening = false
            binding.statusText.text = "Hold to speak"
            binding.micButton.setBackgroundResource(R.drawable.mic_button_background)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Not needed for basic implementation
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Not needed for basic implementation
        }
    }

    private fun startListening() {
        try {
            if (checkPermissions() && !isListening) {
                // Always reset UI state first
                isListening = true
                binding.statusText.text = "Listening..."
                binding.micButton.setBackgroundResource(R.drawable.mic_button_pressed)
                
                // Check if speech recognizer needs recreation
                if (speechRecognizer == null) {
                    setupSpeechRecognizer()
                }
                
                if (speechRecognizer != null) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    }
                    try {
                        speechRecognizer?.startListening(intent)
                        Log.d("ChatBotActivity", "Started listening")
                    } catch (e: Exception) {
                        Log.e("ChatBotActivity", "Error with direct speech recognition: ${e.message}")
                        // Try using system activity as fallback
                        launchSystemSpeechRecognizer()
                    }
                } else {
                    Log.d("ChatBotActivity", "Speech recognizer not available, using system activity")
                    // Use the system's speech recognition activity as fallback
                    launchSystemSpeechRecognizer()
                }
            }
        } catch (e: Exception) {
            Log.e("ChatBotActivity", "Error starting listening: ${e.message}")
            isListening = false
            binding.statusText.text = "Hold to speak"
            binding.micButton.setBackgroundResource(R.drawable.mic_button_background)
            Toast.makeText(this, "Failed to start voice recognition", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchSystemSpeechRecognizer() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e("ChatBotActivity", "Could not start system speech recognition: ${e.message}")
            isListening = false
            binding.statusText.text = "Hold to speak"
            binding.micButton.setBackgroundResource(R.drawable.mic_button_background)
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            SPEECH_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (!results.isNullOrEmpty()) {
                        val spokenText = results[0]
                        processUserInput(spokenText)
                    }
                }
            }
            
            WRITE_SETTINGS_REQUEST_CODE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.System.canWrite(this)) {
                        Toast.makeText(this, "System settings permission granted", Toast.LENGTH_SHORT).show()
                        messageAdapter.addMessage(Message(
                            "System settings permission granted. You can now control brightness and other settings.",
                            false,
                            getCurrentTime()
                        ))
                        binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
                    } else {
                        Toast.makeText(this, 
                            "System settings permission denied. Some features like brightness control won't work.", 
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        
        // Always reset UI state after any activity result
        isListening = false
        binding.statusText.text = "Hold to speak"
        binding.micButton.setBackgroundResource(R.drawable.mic_button_background)
    }

    private fun stopListening() {
        try {
            // Always reset UI state first
            isListening = false
            binding.statusText.text = "Hold to speak"
            binding.micButton.setBackgroundResource(R.drawable.mic_button_background)
            
            // Then try to stop listening
            speechRecognizer?.stopListening()
            Log.d("ChatBotActivity", "Stopped listening")
        } catch (e: Exception) {
            Log.e("ChatBotActivity", "Error stopping listening: ${e.message}")
            // UI state already reset above
        }
    }

    private fun checkPermissions(): Boolean {
        val basicPermissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE
        )
        
        // Add Bluetooth permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            basicPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            basicPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        
        val missingPermissions = basicPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 123)
            return false
        }
        
        // Check for system write settings permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                Toast.makeText(this, 
                    "Please grant permission to control system settings like brightness", 
                    Toast.LENGTH_LONG).show()
                
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, WRITE_SETTINGS_REQUEST_CODE)
            }
        }
        
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            123 -> { // Basic permissions
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "All basic permissions granted", Toast.LENGTH_SHORT).show()
                    startListening()
                } else {
                    Toast.makeText(this, 
                        "Some permissions were denied. Some features may not work properly.", 
                        Toast.LENGTH_LONG).show()
                }
            }
            
            BLUETOOTH_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
                    messageAdapter.addMessage(Message(
                        "Bluetooth permissions granted. You can now control Bluetooth.",
                        false,
                        getCurrentTime()
                    ))
                    binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
                } else {
                    Toast.makeText(this, 
                        "Bluetooth permissions denied. Bluetooth control won't work.", 
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun processUserInput(input: String) {
        if (isProcessing) return
        isProcessing = true

        // Add user message to chat
        messageAdapter.addMessage(Message(input, true, getCurrentTime()))

        // Scroll to bottom
        binding.messagesRecyclerView.post {
            binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
        }

        // Check for phone control commands first
        val phoneControlResponse = handlePhoneControlCommand(input)
        if (phoneControlResponse != null) {
            messageAdapter.addMessage(Message(phoneControlResponse, false, getCurrentTime()))
            binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
            speakText(phoneControlResponse)
            isProcessing = false
            return
        }

        // Check for navigation command
        val navigationResponse = handleNavigationCommand(input)
        if (navigationResponse != null) {
            messageAdapter.addMessage(Message(navigationResponse, false, getCurrentTime()))
            binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
            speakText(navigationResponse)
            isProcessing = false
            return
        }

        // Check for TARS commands
        val response = handleTarsCommand(input)
        if (response != null) {
            messageAdapter.addMessage(Message(response, false, getCurrentTime()))
            binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
            speakText(response)
            isProcessing = false
            return
        }

        // If no command matched, get AI response
        // Show a "thinking" indicator
        val thinkingMessage = Message("Thinking...", false, getCurrentTime())
        messageAdapter.addMessage(thinkingMessage)
        binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val aiResponse = getAIResponse(input)
                withContext(Dispatchers.Main) {
                    // Remove thinking message
                    messageAdapter.removeMessage(thinkingMessage)
                    
                    if (aiResponse.isNotEmpty()) {
                        messageAdapter.addMessage(Message(aiResponse, false, getCurrentTime()))
                        binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
                        speakText(aiResponse)
                    } else {
                        Toast.makeText(this@ChatBotActivity, "No response received", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Remove thinking message
                    messageAdapter.removeMessage(thinkingMessage)
                    
                    val errorMessage = when {
                        !isNetworkAvailable() -> "No internet connection. Please check your network and try again."
                        e.message?.contains("Invalid API key", ignoreCase = true) == true -> "Invalid API key. Please update your Grok API key in settings."
                        e.message?.contains("API key is not authorized", ignoreCase = true) == true -> "API key is not authorized. Please check your Grok API key permissions."
                        e.message?.contains("Rate limit exceeded", ignoreCase = true) == true -> "Rate limit exceeded. Please try again in a few moments."
                        e.message?.contains("Server error", ignoreCase = true) == true -> "Server error. Please try again later."
                        e.message?.contains("Empty response", ignoreCase = true) == true -> "No response received from server. Please try again."
                        e.message?.contains("Invalid response format", ignoreCase = true) == true -> "Invalid response from server. Please try again."
                        else -> "I'm experiencing a technical issue: ${e.message}. Please try again."
                    }
                    
                    messageAdapter.addMessage(Message(errorMessage, false, getCurrentTime()))
                    binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
                    speakText(errorMessage)
                    
                    Log.e("ChatBot", "API Error: ${e.message}")
                }
            } finally {
                isProcessing = false
            }
        }

        // After processing the command, if this activity was opened through the wake word,
        // we should finish this activity after a short delay
        if (isProcessing) return
        isProcessing = true
        
        // At the end of your method, after command processing
        handler.postDelayed({
            // Finish activity only if it's not being actively used
            if (!isListening && !isUserInteracting) {
                finish()
            }
        }, 5000) // Wait 5 seconds after command execution before closing
    }

    private fun speakText(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun handleTarsCommand(input: String): String? {
        val lowerInput = input.lowercase().trim()
        
        return when {
            lowerInput == "show commands" || lowerInput == "what commands" || 
            lowerInput == "help" || lowerInput == "show help" || 
            lowerInput == "what can you do" -> {
                getAvailableCommands()
            }
            
            lowerInput == "show settings" || lowerInput == "show parameters" || 
            lowerInput == "what are your settings" || lowerInput == "display settings" -> {
                "Current settings:\n" +
                "Humor: $humorSetting%\n" +
                "Honesty: $honestySetting%\n" +
                "Sarcasm: $sarcasmSetting%"
            }
            
            lowerInput.matches(Regex(".*set\\s+humor\\s+to\\s+\\d+.*")) -> {
                val value = extractNumber(lowerInput)
                if (value in 0..100) {
                    humorSetting = value
                    "Humor setting adjusted to $value%"
                } else {
                    "Invalid humor value. Please specify a number between 0 and 100."
                }
            }
            
            lowerInput.matches(Regex(".*set\\s+honesty\\s+to\\s+\\d+.*")) -> {
                val value = extractNumber(lowerInput)
                if (value in 0..100) {
                    honestySetting = value
                    "Honesty setting adjusted to $value%"
                } else {
                    "Invalid honesty value. Please specify a number between 0 and 100."
                }
            }
            
            lowerInput.matches(Regex(".*set\\s+sarcasm\\s+to\\s+\\d+.*")) -> {
                val value = extractNumber(lowerInput)
                if (value in 0..100) {
                    sarcasmSetting = value
                    "Sarcasm setting adjusted to $value%"
                } else {
                    "Invalid sarcasm value. Please specify a number between 0 and 100."
                }
            }
            
            lowerInput == "reset settings" || lowerInput == "reset all settings" -> {
                humorSetting = 100
                honestySetting = 100
                sarcasmSetting = 0
                "All settings have been reset to default values."
            }
            
            lowerInput == "hello tars" || lowerInput == "hi tars" -> {
                "Hello! I am TARS, your personal assistant. How can I help you today?"
            }
            
            lowerInput == "who are you" || lowerInput == "who are you tars" -> {
                "I am TARS, a highly advanced AI assistant. I can help you with various tasks and maintain conversation with different personality settings."
            }
            
            lowerInput.matches(Regex("^thank(s| you).*tars.*$")) || 
            lowerInput.matches(Regex("^tars.*thank(s| you).*$")) -> {
                "You're welcome. Is there anything else I can help you with?"
            }
            
            lowerInput == "goodbye tars" || lowerInput == "bye tars" -> {
                "Goodbye! Feel free to return if you need any assistance."
            }
            
            lowerInput == "tell me a joke" || lowerInput == "tell a joke" || 
            lowerInput == "crack me a joke" || lowerInput == "tars tell me a joke" -> {
                if (humorSetting > 0) {
                    getJoke()
                } else {
                    "Sorry, my humor setting is currently set to 0%. Please adjust it if you'd like to hear jokes."
                }
            }
            
            else -> null
        }
    }

    private fun getAvailableCommands(): String {
        return """
            Available TARS Commands:
            
            Volume Controls:
            - "Volume up" or "Increase volume"
            - "Volume down" or "Decrease volume"
            - "Max volume" or "Volume 100"
            - "Min volume" or "Mute volume"
            
            Brightness Controls:
            - "Brightness up" or "Increase brightness" 
            - "Brightness down" or "Decrease brightness"
            
            WiFi Controls:
            - "Turn on WiFi" or "Enable WiFi"
            - "Turn off WiFi" or "Disable WiFi"
            
            Bluetooth Controls:
            - "Turn on Bluetooth" or "Enable Bluetooth"
            - "Turn off Bluetooth" or "Disable Bluetooth"
            
            App Controls:
            - "Open [app name]" (e.g., "Open Camera", "Open YouTube")
            - "Launch [app name]"
            
            System Navigation:
            - "Go home" or "Home screen"
            - "Go back" or "Back"
            - "Recent apps" or "Show recents"
            - "Lock screen" or "Lock phone"
            - "Open notifications" or "Show notifications"
            - "Open quick settings" or "Show quick settings"
            - "Split screen" or "Multi window"
            - "Take screenshot" or "Screen capture"
            - "Do not disturb" or "Silent mode"
            - "Power saving" or "Battery saver"
            - "Flashlight" or "Torch"
            
            Settings Navigation:
            - "Open settings" or "Go to settings"
            - "Open [setting type] settings" (e.g., "Open Bluetooth settings")
            
            Location Navigation:
            - "Navigate to [location]"
            - "Directions to [location]"
            
            AI Assistant:
            - "Set humor to [0-100]"
            - "Set honesty to [0-100]"
            - "Set sarcasm to [0-100]"
            - "Tell me a joke"
            - "Show commands" or "Help"
            - Ask any question for AI responses
        """.trimIndent()
    }

    private fun extractNumber(input: String): Int {
        val regex = Regex("\\d+")
        return regex.find(input)?.value?.toIntOrNull() ?: -1
    }

    private fun setupAIProvider() {
        try {
            aiProvider = OpenRouterProvider()
            
            // Show welcome message
            val humorDescription = when(humorSetting) {
                0 -> "I'm in strictly professional mode."
                in 1..20 -> "My humor is set to minimal."
                in 21..40 -> "I'm slightly humorous today."
                in 41..60 -> "I have a balanced sense of humor."
                in 61..80 -> "I'm feeling quite humorous."
                else -> "I'm at maximum wit and sarcasm."
            }
            
            messageAdapter.addMessage(Message(
                "Hello, I am TARS. Your personal assistant powered by Meta Llama-3. My humor setting is currently at $humorSetting%. $humorDescription Feel free to adjust it using the slider above.",
                false,
                getCurrentTime()
            ))
        } catch (e: Exception) {
            Log.e("ChatBotActivity", "Error setting up AI provider: ${e.message}")
            Toast.makeText(this, "Failed to initialize TARS. Please try again.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private suspend fun getAIResponse(input: String): String {
        if (!isNetworkAvailable()) {
            throw IOException("No internet connection")
        }
        
        aiProvider?.let {
            val personality = Personality(
                humorLevel = humorSetting,
                honestyLevel = honestySetting,
                sarcasmLevel = sarcasmSetting
            )
            return it.getResponse(input, personality)
        }
        
        throw IOException("AI provider not initialized")
    }

    private fun getJoke(): String {
        val jokes = listOf(
            "Why don't scientists trust atoms? Because they make up everything!",
            "What did the AI say to the coffee machine? 'You're brewing my mind!'",
            "Why did the scarecrow win an award? Because he was outstanding in his field!",
            "What do you call a bear with no teeth? A gummy bear!",
            "Why don't programmers like nature? It has too many bugs!",
            "What did one wall say to the other wall? I'll meet you at the corner!",
            "Why did the math book look sad? Because it had too many problems!",
            "What do you call a fake noodle? An impasta!",
            "Why did the cookie go to the doctor? Because it was feeling crumbly!",
            "What did the ocean say to the shore? Nothing, it just waved!"
        )
        return jokes.random()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
        return actNw.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getCurrentTime(): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        return String.format("%02d:%02d", hour, minute)
    }

    private fun setupHumorSlider() {
        binding.humorSlider.value = humorSetting.toFloat()
        binding.humorPercentage.text = "$humorSetting%"
        
        binding.humorSlider.addOnChangeListener { _, value, _ ->
            humorSetting = value.toInt()
            updateHumorUI(humorSetting)
        }
    }

    private fun updateHumorUI(value: Int) {
        binding.humorPercentage.text = "$value%"
        
        val description = when(value) {
            0 -> "Current: Strictly Professional"
            in 1..20 -> "Current: Minimal Humor"
            in 21..40 -> "Current: Slightly Humorous"
            in 41..60 -> "Current: Moderately Humorous"
            in 61..80 -> "Current: Very Humorous"
            else -> "Current: Sarcastic and Humorous"
        }
        
        binding.humorDescription.text = description
        
        // Add a message to indicate the change
        messageAdapter.addMessage(Message(
            "Humor setting adjusted to $value%. ${getHumorMessage(value)}",
            false,
            getCurrentTime()
        ))
        
        // Scroll to the bottom
        binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
    }

    private fun getHumorMessage(value: Int): String {
        return when(value) {
            0 -> "I will maintain strict professionalism."
            in 1..20 -> "I will keep humor to a minimum."
            in 21..40 -> "I will be occasionally humorous."
            in 41..60 -> "I will maintain a balanced sense of humor."
            in 61..80 -> "I will be quite humorous."
            else -> "Expect maximum wit and sarcasm."
        }
    }

    private fun handleNavigationCommand(input: String): String? {
        val navigationPatterns = listOf(
            "navigate to (.+)",
            "directions to (.+)",
            "take me to (.+)",
            "how do i get to (.+)",
            "show me the way to (.+)"
        )
        
        for (pattern in navigationPatterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val matchResult = regex.find(input)
            
            if (matchResult != null) {
                val location = matchResult.groupValues[1].trim()
                try {
                    // Create a more generic maps intent that works with various map apps
                    val gmmIntentUri = Uri.parse("geo:0,0?q=$location")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    
                    // Try to start the activity without checking resolveActivity first
                    startActivity(mapIntent)
                    return "Starting navigation to $location"
                } catch (e: Exception) {
                    Log.e("Navigation", "Error launching maps: ${e.message}")
                    
                    // Fallback to a web URL if app launch fails
                    try {
                        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(location)}")
                        val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                        startActivity(webIntent)
                        return "Opening map to $location in browser"
                    } catch (e2: Exception) {
                        Log.e("Navigation", "Error launching web maps: ${e2.message}")
                        return "Unable to start navigation. Please check if a maps app is installed."
                    }
                }
            }
        }
        return null
    }

    private fun startVoiceService() {
        try {
            val intent = Intent(this, VoiceRecognitionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d("ChatBotActivity", "Voice service started")
        } catch (e: Exception) {
            Log.e("ChatBotActivity", "Error starting voice service: ${e.message}")
            Toast.makeText(this, "Failed to start voice service", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        binding.micButton.setOnTouchListener { view: android.view.View, event: android.view.MotionEvent ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startListening()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopListening()
                    true
                }
                else -> false
            }
        }
    }

    private fun handlePhoneControlCommand(input: String): String? {
        val lowerInput = input.lowercase().trim()
        
        return when {
            // App launching - check this first
            lowerInput.startsWith("open ") || lowerInput.startsWith("launch ") || 
            lowerInput.startsWith("start ") || lowerInput.startsWith("run ") -> {
                val appName = extractAppName(lowerInput)
                if (appName.isNotEmpty()) {
                    phoneControlService.launchApp(appName)
                } else {
                    "Please specify which app to open"
                }
            }
            
            // Volume controls
            lowerInput.contains("volume up") || lowerInput.contains("increase volume") || 
            lowerInput.contains("turn up volume") || lowerInput.contains("louder") -> {
                phoneControlService.adjustVolume(true)
            }
            
            lowerInput.contains("volume down") || lowerInput.contains("decrease volume") || 
            lowerInput.contains("turn down volume") || lowerInput.contains("lower volume") || 
            lowerInput.contains("quieter") -> {
                phoneControlService.adjustVolume(false)
            }
            
            lowerInput.contains("max volume") || lowerInput.contains("volume max") || 
            lowerInput.contains("maximum volume") || lowerInput.contains("full volume") || 
            lowerInput.contains("volume 100") || lowerInput.contains("volume hundred") -> {
                phoneControlService.maxVolume()
            }
            
            lowerInput.contains("min volume") || lowerInput.contains("volume min") || 
            lowerInput.contains("minimum volume") || lowerInput.contains("mute volume") || 
            lowerInput.contains("volume 0") || lowerInput.contains("volume zero") -> {
                phoneControlService.minVolume()
            }
            
            // Brightness controls
            lowerInput.contains("brightness up") || lowerInput.contains("increase brightness") || 
            lowerInput.contains("turn up brightness") || lowerInput.contains("make screen brighter") -> {
                phoneControlService.increaseBrightness()
            }
            
            lowerInput.contains("brightness down") || lowerInput.contains("decrease brightness") || 
            lowerInput.contains("turn down brightness") || lowerInput.contains("make screen darker") || 
            lowerInput.contains("dim screen") -> {
                phoneControlService.decreaseBrightness()
            }
            
            // WiFi controls
            lowerInput.contains("turn on wifi") || lowerInput.contains("enable wifi") || 
            lowerInput.contains("activate wifi") || lowerInput.contains("connect to wifi") -> {
                requestWriteSettingsPermission()
                phoneControlService.toggleWifi(true)
            }
            
            lowerInput.contains("turn off wifi") || lowerInput.contains("disable wifi") || 
            lowerInput.contains("deactivate wifi") || lowerInput.contains("disconnect wifi") -> {
                requestWriteSettingsPermission()
                phoneControlService.toggleWifi(false)
            }
            
            // Bluetooth controls
            lowerInput.contains("turn on bluetooth") || lowerInput.contains("enable bluetooth") || 
            lowerInput.contains("activate bluetooth") -> {
                requestBluetoothPermission()
                phoneControlService.toggleBluetooth(true)
            }
            
            lowerInput.contains("turn off bluetooth") || lowerInput.contains("disable bluetooth") || 
            lowerInput.contains("deactivate bluetooth") -> {
                requestBluetoothPermission()
                phoneControlService.toggleBluetooth(false)
            }
            
            // Settings navigation
            lowerInput.contains("open settings") || lowerInput.contains("go to settings") -> {
                val settingType = extractSettingType(lowerInput)
                phoneControlService.openSettings(settingType)
            }
            
            // System navigation - Home screen
            lowerInput.contains("go home") || lowerInput.contains("home screen") || 
            lowerInput.contains("go to home") || lowerInput == "home" -> {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                "Going to home screen"
            }
            
            // System navigation - Back (not easily supported without accessibility services)
            lowerInput.contains("go back") || lowerInput == "back" -> {
                // We can't easily trigger back button without accessibility services
                // Just inform the user
                "Sorry, I can't go back without special permissions"
            }
            
            // System navigation - Recent apps (open app switcher if possible)
            lowerInput.contains("recent apps") || lowerInput.contains("show recent") || 
            lowerInput.contains("recents") || lowerInput.contains("recent tasks") -> {
                try {
                    val serviceIntent = Intent("com.android.systemui.TOGGLE_RECENTS")
                    serviceIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(serviceIntent)
                    "Showing recent apps"
                } catch (e: Exception) {
                    "Sorry, I can't access recent apps on this device"
                }
            }
            
            // System navigation - Lock screen (open settings instead)
            lowerInput.contains("lock screen") || lowerInput.contains("lock phone") || 
            lowerInput.contains("lock device") || lowerInput == "lock" || 
            lowerInput.contains("turn off screen") || lowerInput.contains("sleep device") -> {
                // We can't directly lock screen without device admin rights
                phoneControlService.openSettings("security")
                "Opening security settings. I cannot directly lock your screen without special permissions."
            }
            
            // System navigation - Notifications (open settings instead)
            lowerInput.contains("notifications") || lowerInput.contains("show notifications") || 
            lowerInput.contains("open notifications") || lowerInput.contains("pull down notifications") || 
            lowerInput.contains("check notifications") -> {
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                "Opening settings where you can find notifications"
            }
            
            // System navigation - Quick settings (open settings instead)
            lowerInput.contains("quick settings") || lowerInput.contains("show quick settings") || 
            lowerInput.contains("open quick settings") || lowerInput.contains("system settings") || 
            lowerInput.contains("toggles") || lowerInput.contains("quick panel") -> {
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                "Opening system settings"
            }
            
            // Split screen (not easily supported without accessibility services)
            lowerInput.contains("split screen") || lowerInput.contains("multi window") || 
            lowerInput.contains("dual screen") -> {
                "Sorry, I can't activate split screen without special permissions"
            }
            
            // Take screenshot (not easily supported without system permissions)
            lowerInput.contains("screenshot") || lowerInput.contains("take screenshot") || 
            lowerInput.contains("capture screen") || lowerInput.contains("screen capture") -> {
                "Sorry, I can't take screenshots without special permissions"
            }
            
            // Open camera
            lowerInput == "camera" || lowerInput.contains("open camera") || 
            lowerInput.contains("take photo") || lowerInput.contains("take picture") -> {
                phoneControlService.launchApp("camera")
            }
            
            // Do not disturb mode (open settings instead)
            lowerInput.contains("do not disturb") || lowerInput.contains("silent mode") || 
            lowerInput.contains("mute notifications") -> {
                val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                "Opening sound settings where you can enable Do Not Disturb"
            }
            
            // Power saving mode (open battery settings)
            lowerInput.contains("power saving") || lowerInput.contains("battery saver") || 
            lowerInput.contains("save battery") -> {
                val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                "Opening battery settings where you can enable power saving"
            }
            
            // Turn on flashlight/torch (not easily supported without camera permissions)
            lowerInput.contains("flashlight") || lowerInput.contains("torch") || 
            lowerInput.contains("turn on light") -> {
                phoneControlService.openSettings("display")
                "Sorry, I can't directly control the flashlight without special permissions"
            }
            
            // If no phone control command matched
            else -> null
        }
    }

    private fun extractAppName(command: String): String {
        // Remove command words
        val keywords = listOf("open", "launch", "start", "run", "app", "application", "the")
        var appName = command.lowercase()
        
        // Remove each keyword and clean up
        keywords.forEach { keyword ->
            appName = appName.replace(keyword, "")
        }
        
        // Clean up extra spaces and trim
        return appName.replace("\\s+".toRegex(), " ").trim()
    }

    private fun extractSettingType(command: String): String {
        val keywords = listOf("open settings", "go to settings", "settings for", "open setting", "go to setting")
        var settingType = command
        
        for (keyword in keywords) {
            settingType = settingType.replace(keyword, "").trim()
        }
        
        return if (settingType.isEmpty() || settingType == command) "general" else settingType
    }

    private fun requestWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, WRITE_SETTINGS_REQUEST_CODE)
                Toast.makeText(this, "Please grant permission to modify system settings", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            
            val needsPermission = permissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            
            if (needsPermission) {
                ActivityCompat.requestPermissions(this, permissions, BLUETOOTH_PERMISSION_CODE)
                Toast.makeText(this, "Please grant Bluetooth permissions", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val SPEECH_REQUEST_CODE = 100
        private const val WRITE_SETTINGS_REQUEST_CODE = 101
        private const val BLUETOOTH_PERMISSION_CODE = 102
    }
} 