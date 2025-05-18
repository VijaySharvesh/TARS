package com.example.tars

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class WakeWordDetectionService : Service() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var handler = Handler(Looper.getMainLooper())
    private var restartListeningTimer: Timer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var prefs: SharedPreferences
    private var chatBotActive = false
    private var consecutiveErrors = 0
    private var passiveListeningMode = true
    private var lastWakeWordDetectionTime = 0L
    private var isWakeWordEnabled = false
    
    companion object {
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "WakeWordDetectionChannel"
        const val WAKE_WORD = "open the one"
        const val RESTART_LISTENING_DELAY = 1000L // 1 second for faster restart
        const val ERROR_THRESHOLD = 3 // Reduced from 5 to 3 for faster recovery
        const val DETECTION_COOLDOWN = 5000L // Reduced from 10s to 5s for better responsiveness
        const val ACTION_CHAT_BOT_OPENED = "com.example.tars.CHAT_BOT_OPENED"
        const val ACTION_CHAT_BOT_CLOSED = "com.example.tars.CHAT_BOT_CLOSED"
        const val ACTION_PAUSE_LISTENING = "com.example.tars.PAUSE_LISTENING"
        const val ACTION_RESUME_LISTENING = "com.example.tars.RESUME_LISTENING"
        const val PREF_WAKE_WORD_ENABLED = "wake_word_enabled"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("TarsPrefs", MODE_PRIVATE)
        isWakeWordEnabled = prefs.getBoolean(PREF_WAKE_WORD_ENABLED, false)
        
        if (!isWakeWordEnabled) {
            Log.d("WakeWordService", "Wake word detection is disabled in settings")
            stopSelf()
            return
        }
        
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("WakeWordService", "Speech recognition not available on this device")
            stopSelf()
            return
        }
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("TARS Wake Word Detection Active"))
        isRunning = true
        Log.d("WakeWordService", "Service created")
        
        // Acquire a partial wake lock to ensure service keeps running
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TARS:WakeWordWakeLock")
        wakeLock?.acquire(10*60*1000L) // 10 minutes max to avoid battery drain, will be renewed
        
        // Register for broadcast messages
        val filter = IntentFilter().apply {
            addAction(ACTION_CHAT_BOT_OPENED)
            addAction(ACTION_CHAT_BOT_CLOSED)
            addAction(ACTION_PAUSE_LISTENING)
            addAction(ACTION_RESUME_LISTENING)
        }
        registerReceiver(communicationReceiver, filter)
        
        initializeSpeechRecognizer()
        startListening()
        
        // Schedule periodic service checks
        scheduleServiceChecks()
    }
    
    private val communicationReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action) {
                ACTION_CHAT_BOT_OPENED -> {
                    chatBotActive = true
                    stopListening("ChatBotActivity opened")
                }
                ACTION_CHAT_BOT_CLOSED -> {
                    chatBotActive = false
                    // Delay slightly to let ChatBotActivity fully close
                    handler.postDelayed({
                        startListening()
                    }, 500)
                }
                ACTION_PAUSE_LISTENING -> {
                    stopListening("Temporarily paused")
                }
                ACTION_RESUME_LISTENING -> {
                    if (!chatBotActive) {
                        startListening()
                    }
                }
            }
        }
    }
    
    private fun scheduleServiceChecks() {
        val serviceCheckTimer = Timer()
        serviceCheckTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                handler.post {
                    // Renew wake lock periodically to ensure service keeps running
                    if (wakeLock?.isHeld == true) {
                        wakeLock?.release()
                    }
                    wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TARS:WakeWordWakeLock")
                    wakeLock?.acquire(10*60*1000L)
                    
                    // Ensure we're listening if we should be
                    if (!chatBotActive && !isListening && consecutiveErrors < ERROR_THRESHOLD) {
                        Log.d("WakeWordService", "Service check: restarting listening")
                        startListening()
                    }
                    
                    // Reset error counter periodically to recover from bad states
                    if (consecutiveErrors >= ERROR_THRESHOLD) {
                        Log.d("WakeWordService", "Resetting high error count")
                        consecutiveErrors = 0
                        recreateSpeechRecognizer()
                        startListening()
                    }
                }
            }
        }, 60000, 60000) // Check every minute
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_LISTENING -> {
                stopListening("Pause requested")
            }
            ACTION_RESUME_LISTENING -> {
                if (!chatBotActive && !isListening) {
                    startListening()
                }
            }
            else -> {
                if (!chatBotActive && !isListening) {
                    startListening()
                }
            }
        }
        return START_STICKY
    }
    
    private fun recreateSpeechRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Log.e("WakeWordService", "Speech recognition not available")
                stopSelf()
                return
            }
            
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(createRecognitionListener())
            }
            
            Log.d("WakeWordService", "Speech recognizer initialized successfully")
        } catch (e: Exception) {
            Log.e("WakeWordService", "Error initializing speech recognizer: ${e.message}")
            speechRecognizer = null
            handler.postDelayed({
                if (consecutiveErrors < ERROR_THRESHOLD) {
                    initializeSpeechRecognizer()
                } else {
                    stopSelf()
                }
            }, RESTART_LISTENING_DELAY)
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            consecutiveErrors = 0 // Reset error counter on successful start
            Log.d("WakeWordService", "Ready for wake word detection")
            updateNotification("Listening for 'Open The One'")
        }

        override fun onBeginningOfSpeech() {
            Log.d("WakeWordService", "Speech started")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // We can use this to detect audio levels and improve wake word sensitivity
            // but we'll skip detailed implementation for now
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Not needed for wake word detection
        }

        override fun onEndOfSpeech() {
            Log.d("WakeWordService", "Speech ended")
            isListening = false
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
            
            // Only increment error counter for serious errors
            if (error != SpeechRecognizer.ERROR_NO_MATCH && 
                error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                consecutiveErrors++
                Log.e("WakeWordService", "Serious speech recognition error: $errorMessage ($error), count: $consecutiveErrors")
            } else {
                Log.d("WakeWordService", "Minor speech recognition error: $errorMessage ($error)")
            }
            
            isListening = false
            
            // Vary restart delay based on error count
            if (consecutiveErrors >= ERROR_THRESHOLD) {
                // Many errors in a row - take a longer break and recreate recognizer
                updateNotification("Cooling down after errors...")
                scheduleLongRestartDelay()
            } else {
                // Normal quick restart
                scheduleRestartListening()
            }
        }

        override fun onResults(results: Bundle?) {
            try {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0].lowercase(Locale.getDefault())
                    Log.d("WakeWordService", "Speech recognized: $spokenText")
                    
                    // Check if the wake word was detected
                    if (spokenText.contains(WAKE_WORD)) {
                        val now = System.currentTimeMillis()
                        // Prevent multiple quick activations
                        if (now - lastWakeWordDetectionTime > DETECTION_COOLDOWN) {
                            Log.d("WakeWordService", "Wake word detected: $WAKE_WORD")
                            lastWakeWordDetectionTime = now
                            showFloatingMic()
                        } else {
                            Log.d("WakeWordService", "Wake word detected but in cooldown period")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WakeWordService", "Error processing results: ${e.message}")
            } finally {
                isListening = false
                // Only restart if we're not showing the chat interface
                if (!chatBotActive) {
                    scheduleRestartListening()
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Check for wake word in partial results too for faster response
            try {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0].lowercase(Locale.getDefault())
                    
                    // Check if the wake word was detected
                    if (spokenText.contains(WAKE_WORD)) {
                        val now = System.currentTimeMillis()
                        // Prevent multiple quick activations
                        if (now - lastWakeWordDetectionTime > DETECTION_COOLDOWN) {
                            Log.d("WakeWordService", "Wake word detected in partial results: $WAKE_WORD")
                            lastWakeWordDetectionTime = now
                            showFloatingMic()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WakeWordService", "Error processing partial results: ${e.message}")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Not needed for wake word detection
        }
    }

    private fun startListening() {
        if (chatBotActive) {
            Log.d("WakeWordService", "Not starting listening because ChatBotActivity is active")
            return
        }
        
        try {
            if (speechRecognizer == null) {
                initializeSpeechRecognizer()
            }
            
            if (speechRecognizer != null && !isListening) {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    
                    // Balance between sensitivity and battery usage
                    if (passiveListeningMode) {
                        // Passive mode: longer timeouts for battery savings
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
                    } else {
                        // Active mode: shorter timeouts for better responsiveness
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500)
                    }
                }
                
                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d("WakeWordService", "Started listening for wake word")
                updateNotification("Listening for 'Open The One'")
            }
        } catch (e: Exception) {
            Log.e("WakeWordService", "Error starting speech recognition: ${e.message}")
            isListening = false
            consecutiveErrors++
            
            // Try to restart after error, with increasing delays
            if (consecutiveErrors >= ERROR_THRESHOLD) {
                scheduleLongRestartDelay()
            } else {
                scheduleRestartListening()
            }
        }
    }
    
    private fun stopListening(reason: String) {
        try {
            if (isListening) {
                speechRecognizer?.stopListening()
                isListening = false
                Log.d("WakeWordService", "Stopped listening: $reason")
                updateNotification("Paused: $reason")
            }
        } catch (e: Exception) {
            Log.e("WakeWordService", "Error stopping speech recognition: ${e.message}")
        }
    }

    private fun scheduleRestartListening() {
        if (restartListeningTimer != null) {
            restartListeningTimer?.cancel()
        }
        
        // Use a shorter delay if we have fewer errors
        val delay = if (consecutiveErrors > 0) 
            RESTART_LISTENING_DELAY * consecutiveErrors
        else 
            RESTART_LISTENING_DELAY
            
        restartListeningTimer = Timer()
        restartListeningTimer?.schedule(object : TimerTask() {
            override fun run() {
                handler.post {
                    if (!chatBotActive && !isListening) {
                        startListening()
                    }
                }
            }
        }, delay)
    }
    
    private fun scheduleLongRestartDelay() {
        if (restartListeningTimer != null) {
            restartListeningTimer?.cancel()
        }
        
        // Take a longer break (10 seconds) after multiple errors
        restartListeningTimer = Timer()
        restartListeningTimer?.schedule(object : TimerTask() {
            override fun run() {
                handler.post {
                    if (!chatBotActive && !isListening) {
                        // Recreate the speech recognizer
                        recreateSpeechRecognizer()
                        startListening()
                    }
                }
            }
        }, 10000) // 10 second cooldown
    }

    private fun showFloatingMic() {
        try {
            // Stop listening while floating mic is shown
            stopListening("Wake word detected")
            
            // Start FloatingMicService to show the floating mic button
            val intent = Intent(this, FloatingMicService::class.java).apply {
                action = FloatingMicService.ACTION_SHOW_FLOATING_MIC
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            handler.post {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
            Log.d("WakeWordService", "Floating mic service started")
        } catch (e: Exception) {
            Log.e("WakeWordService", "Error showing floating mic: ${e.message}")
            // Try to recover by restarting listening
            scheduleRestartListening()
        }
    }
    
    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Detection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when TARS is listening for wake word"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TARS Voice Assistant")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            isRunning = false
            
            // Release wake lock
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            
            // Unregister receiver
            try {
                unregisterReceiver(communicationReceiver)
            } catch (e: Exception) {
                Log.e("WakeWordService", "Error unregistering receiver: ${e.message}")
            }
            
            // Cancel any pending tasks
            handler.removeCallbacksAndMessages(null)
            if (restartListeningTimer != null) {
                restartListeningTimer?.cancel()
                restartListeningTimer = null
            }
        } catch (e: Exception) {
            Log.e("WakeWordService", "Error destroying service", e)
        }
        Log.d("WakeWordService", "Service destroyed")
    }
} 