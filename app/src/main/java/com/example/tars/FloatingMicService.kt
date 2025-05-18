package com.example.tars

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.util.Log
import androidx.core.app.NotificationCompat

class FloatingMicService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isVisible = false

    companion object {
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "FloatingMicChannel"
        const val ACTION_SHOW_FLOATING_MIC = "com.example.tars.SHOW_FLOATING_MIC"
        const val ACTION_HIDE_FLOATING_MIC = "com.example.tars.HIDE_FLOATING_MIC"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("TARS Floating Mic Active"))
        isRunning = true
        Log.d("FloatingMicService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_FLOATING_MIC && !isVisible) {
            showFloatingMic()
        } else if (intent?.action == ACTION_HIDE_FLOATING_MIC && isVisible) {
            hideFloatingMic()
        }
        return START_STICKY
    }

    private fun showFloatingMic() {
        if (windowManager == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        if (floatingView == null) {
            // Inflate the floating view layout
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_mic_layout, null)

            // Set up the layout parameters for the floating view
            val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            } else {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            }
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = 100

            // Set up touch listener for dragging the floating view
            setupTouchListener(floatingView!!)

            // Set up click listener for the mic button
            val micButton = floatingView!!.findViewById<ImageView>(R.id.floating_mic_button)
            micButton.setOnClickListener {
                // Start voice recognition
                val voiceIntent = Intent(this, ChatBotActivity::class.java)
                voiceIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(voiceIntent)
                
                // Hide the floating mic after it's clicked
                hideFloatingMic()
            }

            // Add the view to the window
            windowManager?.addView(floatingView, params)
            isVisible = true
            Log.d("FloatingMicService", "Floating mic shown")
        }
    }

    private fun setupTouchListener(view: View) {
        view.setOnTouchListener { v, event ->
            val layoutParams = v.layoutParams as WindowManager.LayoutParams

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(v, layoutParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun hideFloatingMic() {
        if (floatingView != null && windowManager != null) {
            try {
                windowManager?.removeView(floatingView)
                floatingView = null
                isVisible = false
                Log.d("FloatingMicService", "Floating mic hidden")
            } catch (e: Exception) {
                Log.e("FloatingMicService", "Error hiding floating mic", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Mic Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when TARS floating mic is active"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TARS Floating Mic")
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
        if (floatingView != null && windowManager != null) {
            windowManager?.removeView(floatingView)
            isVisible = false
        }
        isRunning = false
        Log.d("FloatingMicService", "Service destroyed")
    }
} 