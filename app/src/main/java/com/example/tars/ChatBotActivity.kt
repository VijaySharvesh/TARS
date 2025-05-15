package com.example.tars
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import com.example.tars.ai.GrokProvider
import com.example.tars.ai.Personality
import com.example.tars.databinding.ActivityChatBotBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.Locale

class ChatBotActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBotBinding
    private lateinit var messageAdapter: MessageAdapter
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isProcessing = false
    private var isListening = false
    private var humorSetting = 100
    private var honestySetting = 100
    private var sarcasmSetting = 0
    
    private var aiProvider: AIProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Config with API keys
        Config.init(this)

        setupRecyclerView()
        setupSpeechRecognizer()
        setupTextToSpeech()
        setupClickListeners()
        setupHumorSlider()
        checkPermissions()
        setupAIProvider()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter()
        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatBotActivity)
            adapter = messageAdapter
        }
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        } else {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_LONG).show()
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

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            binding.statusText.text = "Listening..."
            binding.micButton.setBackgroundResource(R.drawable.mic_button_pressed)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                processUserInput(spokenText)
            }
            isListening = false
            binding.micButton.setBackgroundResource(R.drawable.mic_button_background)
            binding.statusText.text = "Hold to speak"
        }

        override fun onError(error: Int) {
            isListening = false
            binding.micButton.setBackgroundResource(R.drawable.mic_button_background)
            binding.statusText.text = "Hold to speak"
            Toast.makeText(this@ChatBotActivity, "Error: $error", Toast.LENGTH_SHORT).show()
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

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

    private fun startListening() {
        if (checkPermissions() && !isListening) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    private fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 123)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startListening()
        }
    }

    private fun processUserInput(input: String) {
        if (isProcessing) return
        isProcessing = true

        // Add user message to chat
        messageAdapter.addMessage(Message(input, true, getCurrentTime()))

        // Scroll to bottom
        binding.messagesRecyclerView.post {
            binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
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

        // Check for TARS commands first
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
                        e.message?.contains("API key", ignoreCase = true) == true -> "Invalid API key. Please update your Grok API key in settings."
                        e.message?.contains("Too many requests", ignoreCase = true) == true -> "The server is busy. Please try again in a moment."
                        else -> "I'm experiencing a technical issue. Please try again."
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
    }

    private fun speakText(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun handleTarsCommand(input: String): String? {
        val lowerInput = input.lowercase().trim()
        
        return when {
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
            
            lowerInput == "show commands" || lowerInput == "what are your commands" || 
            lowerInput == "help" || lowerInput == "show help" -> {
                """
                Available TARS commands:
                1. "Show settings" - Display current settings
                2. "Set humor/honesty/sarcasm to [0-100]" - Adjust settings
                3. "Reset settings" - Reset to defaults
                4. "Tell me a joke" - Get a joke (if humor > 0)
                5. "Who are you" - Learn about TARS
                6. "Hello TARS" - Greet TARS
                7. "Thank you TARS" - Express gratitude
                8. "Goodbye TARS" - End conversation
                
                For any other questions, I'll provide AI-powered responses!
                """.trimIndent()
            }
            
            else -> null
        }
    }

    private fun extractNumber(input: String): Int {
        val regex = Regex("\\d+")
        return regex.find(input)?.value?.toIntOrNull() ?: -1
    }

    private fun setupAIProvider() {
        val apiKey = Config.getGrokKey()
        if (apiKey.isNotEmpty()) {
            aiProvider = GrokProvider(apiKey)
        } else {
            startSetupActivity()
            return
        }
        
        // Add welcome message based on humor setting
        val humorDescription = when(humorSetting) {
            0 -> "I'm in strictly professional mode."
            in 1..20 -> "My humor is set to minimal."
            in 21..40 -> "I'm slightly humorous today."
            in 41..60 -> "I have a balanced sense of humor."
            in 61..80 -> "I'm feeling quite humorous."
            else -> "I'm at maximum wit and sarcasm."
        }
        
        messageAdapter.addMessage(Message(
            "Hello, I am TARS. My humor setting is currently at $humorSetting%. $humorDescription Feel free to adjust it using the slider above.",
            false,
            getCurrentTime()
        ))
    }
    
    private fun startSetupActivity() {
        startActivity(Intent(this, SetupActivity::class.java))
        finish()
    }

    private suspend fun getAIResponse(input: String): String {
        if (!isNetworkAvailable()) {
            throw IOException("No internet connection")
        }
        
        // Use AI provider
        aiProvider?.let {
            val personality = Personality(
                humorLevel = humorSetting,
                honestyLevel = honestySetting,
                sarcasmLevel = sarcasmSetting
            )
            return it.getResponse(input, personality)
        }
        
        throw IOException("Grok API provider not initialized")
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

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chatbot, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SetupActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
} 