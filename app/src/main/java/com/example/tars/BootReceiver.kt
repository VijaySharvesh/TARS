package com.example.tars

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, checking if wake word service should start")
            
            // Check if wake word is enabled in preferences
            val prefs = context.getSharedPreferences("TarsPrefs", Context.MODE_PRIVATE)
            val wakeWordEnabled = prefs.getBoolean(WakeWordDetectionService.PREF_WAKE_WORD_ENABLED, false)
            
            if (wakeWordEnabled) {
                Log.d("BootReceiver", "Wake word enabled, starting service")
                // Start the wake word detection service
                val serviceIntent = Intent(context, WakeWordDetectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.d("BootReceiver", "Wake word disabled, not starting service")
            }
        }
    }
} 