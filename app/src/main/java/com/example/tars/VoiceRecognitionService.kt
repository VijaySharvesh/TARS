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
import androidx.core.app.NotificationCompat
import java.util.Locale

class VoiceRecognitionService : Service() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isActive = false
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var chatBotService: ChatBotService

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "VoiceRecognitionChannel"
        var isRunning = false
        private const val KEYWORD = "tarzan"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true
        isActive = true
        initializeSpeechRecognizer()
        initializeMediaPlayer()
        chatBotService = ChatBotService(this)
        playBeepSound()
    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer.create(this, R.raw.beep)?.apply {
            setOnCompletionListener { it.reset() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isListening) {
            startListening()
        }
        return START_STICKY
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

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TARS Voice Control")
            .setContentText("Listening for commands...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            Log.d("VoiceService", "Ready for speech")
            updateNotification("Listening for commands...")
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val command = matches[0].toLowerCase()
                Log.d("VoiceService", "Command received: $command")
                chatBotService.processCommand(command)
                updateNotification("Command received: $command")
            }
            
            // Always restart listening if we're still running
            if (isRunning) {
                handler.postDelayed({
                    startListening()
                }, 100)
            }
        }

        override fun onError(error: Int) {
            Log.e("VoiceService", "Error: $error")
            // Restart listening on any error if we're still running
            if (isRunning) {
                handler.postDelayed({
                    startListening()
                }, 100)
            }
        }

        override fun onBeginningOfSpeech() {
            Log.d("VoiceService", "Speech started")
            updateNotification("Speech detected...")
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

    private fun playBeepSound() {
        mediaPlayer?.start()
    }

    private fun startListening() {
        if (isRunning) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TARS Voice Control")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
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