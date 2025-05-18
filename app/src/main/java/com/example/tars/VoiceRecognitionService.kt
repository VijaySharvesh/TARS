package com.example.tars

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Locale

class  VoiceRecognitionService : Service() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isActive = false
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var chatBotService: ChatBotService
    private var commandsHistory = mutableListOf<String>()
    private val MAX_HISTORY_SIZE = 5

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "VoiceRecognitionChannel"
        var isRunning = false
        private const val WAKE_WORD = "tars"
        private const val LISTENING_TIMEOUT = 5000L // 5 seconds timeout for voice input
        
        // Intent actions
        const val ACTION_START_LISTENING = "com.example.tars.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.example.tars.STOP_LISTENING"
    }

    override fun onCreate() {
        try {
            super.onCreate()
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("TARS Voice Service Ready"))
            isRunning = true
            isActive = true
            
            // Initialize media player first
            try {
                initializeMediaPlayer()
                Log.d("VoiceService", "Media player initialized")
            } catch (e: Exception) {
                Log.e("VoiceService", "Error initializing media player: ${e.message}")
            }
            
            // Initialize speech recognizer
            try {
                initializeSpeechRecognizer()
                Log.d("VoiceService", "Speech recognizer initialized")
            } catch (e: Exception) {
                Log.e("VoiceService", "Error initializing speech recognizer: ${e.message}")
            }
            
            // Initialize chat bot service
            try {
                chatBotService = ChatBotService(this)
                Log.d("VoiceService", "Chat bot service initialized")
            } catch (e: Exception) {
                Log.e("VoiceService", "Error initializing chat bot service: ${e.message}")
                // Send error broadcast if chat bot service fails
                val errorIntent = Intent("com.example.tars.VOICE_ERROR")
                errorIntent.putExtra("error", "Failed to initialize chat service: ${e.message}")
                sendBroadcast(errorIntent)
            }
            
            Log.d("VoiceService", "Service created and ready")
        } catch (e: Exception) {
            Log.e("VoiceService", "Error creating service: ${e.message}")
            // Try to notify any potential listeners about the error
            try {
                val errorIntent = Intent("com.example.tars.VOICE_ERROR")
                errorIntent.putExtra("error", "Service initialization error: ${e.message}")
                sendBroadcast(errorIntent)
            } catch (broadcastError: Exception) {
                Log.e("VoiceService", "Error sending broadcast: ${broadcastError.message}")
            }
        }
    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer.create(this, R.raw.beep)?.apply {
            setOnCompletionListener { it.reset() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            intent?.let {
                when (it.action) {
                    ACTION_START_LISTENING -> {
                        if (!isListening) {
                            playBeepSound()
                            startListening()
                            updateNotification("Listening for commands...")
                        }
                    }
                    ACTION_STOP_LISTENING -> {
                        if (isListening) {
                            stopListening()
                            updateNotification("Service ready")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceService", "Error in onStartCommand: ${e.message}")
            // Send error broadcast
            val errorIntent = Intent("com.example.tars.VOICE_ERROR")
            errorIntent.putExtra("error", "Service error: ${e.message}")
            sendBroadcast(errorIntent)
            
            isListening = false
            updateNotification("Service error: ${e.message}")
        }
        
        return START_STICKY
    }

    private fun stopListening() {
        try {
            if (isListening) {
                isListening = false
                speechRecognizer?.stopListening()
                Log.d("VoiceService", "Stopped listening")
                
                // Cancel any pending timeouts
                handler.removeCallbacksAndMessages(null)
            }
        } catch (e: Exception) {
            Log.e("VoiceService", "Error stopping speech recognition: ${e.message}")
            // Make sure we set isListening to false even if there's an error
            isListening = false
            
            // Try to send an error broadcast
            try {
                val errorIntent = Intent("com.example.tars.VOICE_ERROR")
                errorIntent.putExtra("error", "Error stopping recognition: ${e.message}")
                sendBroadcast(errorIntent)
            } catch (broadcastError: Exception) {
                Log.e("VoiceService", "Error sending broadcast: ${broadcastError.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Recognition Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when TARS is listening for voice commands"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TARS Voice Control")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun initializeSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer?.setRecognitionListener(createRecognitionListener())
                Log.d("VoiceService", "Speech recognizer initialized successfully")
            } else {
                Log.e("VoiceService", "Speech recognition not available on this device")
                Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
                
                // Send error broadcast
                val errorIntent = Intent("com.example.tars.VOICE_ERROR")
                errorIntent.putExtra("error", "Speech recognition not available on this device")
                sendBroadcast(errorIntent)
                
                updateNotification("Speech recognition not available")
            }
        } catch (e: Exception) {
            Log.e("VoiceService", "Error initializing speech recognizer: ${e.message}")
            speechRecognizer = null
            
            // Send error broadcast
            val errorIntent = Intent("com.example.tars.VOICE_ERROR")
            errorIntent.putExtra("error", "Error initializing speech recognizer: ${e.message}")
            sendBroadcast(errorIntent)
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            Log.d("VoiceService", "Ready for speech")
            updateNotification("Listening for commands...")
            
            // Set a timeout for listening
            handler.postDelayed({
                if (isListening) {
                    stopListening()
                    updateNotification("Listening timeout")
                    
                    // Broadcast timeout error
                    val errorIntent = Intent("com.example.tars.VOICE_ERROR")
                    errorIntent.putExtra("error", "Listening timeout")
                    sendBroadcast(errorIntent)
                }
            }, LISTENING_TIMEOUT)
        }

        override fun onResults(results: Bundle?) {
            try {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0].toLowerCase()
                    Log.d("VoiceService", "Command received: $command")
                    
                    // Add to history
                    commandsHistory.add(command)
                    if (commandsHistory.size > MAX_HISTORY_SIZE) {
                        commandsHistory.removeAt(0)
                    }
                    
                    // Broadcast result to activity
                    val resultIntent = Intent("com.example.tars.VOICE_RESULT")
                    resultIntent.putExtra("result", command)
                    sendBroadcast(resultIntent)
                    
                    // For background service use, also process the command
                    processCommand(command)
                    updateNotification("Command processed: $command")
                } else {
                    // No matches found
                    Log.d("VoiceService", "No speech matches found")
                    val errorIntent = Intent("com.example.tars.VOICE_ERROR")
                    errorIntent.putExtra("error", "No speech matches found")
                    sendBroadcast(errorIntent)
                }
            } catch (e: Exception) {
                Log.e("VoiceService", "Error processing results: ${e.message}")
                val errorIntent = Intent("com.example.tars.VOICE_ERROR")
                errorIntent.putExtra("error", "Error processing voice: ${e.message}")
                sendBroadcast(errorIntent)
            } finally {
                // Don't restart listening automatically - wait for button press
                isListening = false
            }
        }

        override fun onError(error: Int) {
            isListening = false
            val errorMessage = getErrorMessage(error)
            Log.e("VoiceService", "Error: $errorMessage ($error)")
            updateNotification("Error: $errorMessage")
            
            // Broadcast error to activity
            val errorIntent = Intent("com.example.tars.VOICE_ERROR")
            errorIntent.putExtra("error", errorMessage)
            sendBroadcast(errorIntent)
        }

        override fun onBeginningOfSpeech() {
            Log.d("VoiceService", "Speech started")
            updateNotification("Speech detected...")
            // Cancel the timeout handler as speech has begun
            handler.removeCallbacksAndMessages(null)
        }
        
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        
        override fun onEndOfSpeech() {
            Log.d("VoiceService", "Speech ended")
            updateNotification("Processing speech...")
        }
        
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
    
    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            else -> "Unknown error"
        }
    }

    private fun processCommand(command: String) {
        // Process command regardless of wake word
        val processedCommand = command.replace(WAKE_WORD, "").trim()
        if (processedCommand.isNotEmpty()) {
            chatBotService.processCommand(processedCommand)
        }
    }

    private fun playBeepSound() {
        mediaPlayer?.start()
    }

    private fun startListening() {
        if (isRunning && !isListening) {
            try {
                if (speechRecognizer == null) {
                    // Try to re-initialize if null
                    initializeSpeechRecognizer()
                    
                    // If still null after re-init, report error
                    if (speechRecognizer == null) {
                        Log.e("VoiceService", "Failed to initialize speech recognizer")
                        val errorIntent = Intent("com.example.tars.VOICE_ERROR")
                        errorIntent.putExtra("error", "Failed to initialize speech recognizer")
                        sendBroadcast(errorIntent)
                        updateNotification("Error: Speech recognizer not available")
                        return
                    }
                }
                
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                }
                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                Log.e("VoiceService", "Error starting listening: ${e.message}")
                isListening = false
                
                // Send error broadcast
                val errorIntent = Intent("com.example.tars.VOICE_ERROR")
                errorIntent.putExtra("error", "Failed to start listening: ${e.message}")
                sendBroadcast(errorIntent)
                
                updateNotification("Error starting speech recognition")
            }
        }
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        isListening = false
        isActive = false
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
        mediaPlayer?.release()
        mediaPlayer = null
        chatBotService.shutdown()
        super.onDestroy()
    }
} 