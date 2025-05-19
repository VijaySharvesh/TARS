package com.example.tars

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

class PhoneControlService(private val context: Context) {
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }
    private val packageManager = context.packageManager
    
    companion object {
        private const val TAG = "PhoneControlService"
    }
    
    /**
     * Volume Control Methods
     */
    fun adjustVolume(isUp: Boolean): String {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        
        return when {
            isUp && currentVolume >= maxVolume -> {
                "Volume is already at maximum"
            }
            !isUp && currentVolume <= 0 -> {
                "Volume is already at minimum"
            }
            else -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (isUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
                if (isUp) "Volume increased" else "Volume decreased"
            }
        }
    }
    
    fun maxVolume(): String {
        try {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            
            return if (currentVolume >= maxVolume) {
                "Volume is already at maximum"
            } else {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    maxVolume,
                    AudioManager.FLAG_SHOW_UI
                )
                "Volume set to maximum"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting max volume: ${e.message}")
            return "Failed to set maximum volume"
        }
    }
    
    fun minVolume(): String {
        try {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            
            return if (currentVolume <= 0) {
                "Volume is already at minimum"
            } else {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    0,
                    AudioManager.FLAG_SHOW_UI
                )
                "Volume set to minimum"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting min volume: ${e.message}")
            return "Failed to set minimum volume"
        }
    }
    
    /**
     * Brightness Control Methods
     */
    fun adjustBrightness(level: Int): String {
        try {
            Log.d(TAG, "Attempting to set brightness level to: $level")
            
            // Check if the app has permission to write settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.System.canWrite(context)) {
                    Log.d(TAG, "No permission to write settings, opening settings page")
                    // If we don't have permission, open settings to allow it
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return "Please grant permission to modify system settings for brightness control"
                }
            }
            
            // Ensure level is between 0 and 255
            val brightnessLevel = when {
                level < 0 -> 0
                level > 255 -> 255
                else -> level
            }
            
            // Try direct settings method first
            try {
                Log.d(TAG, "Trying direct brightness adjustment to level: $brightnessLevel")
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightnessLevel
                )
                
                // Apply changes immediately if possible by sending intent
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                
                return "Brightness set to ${(brightnessLevel / 255.0 * 100).toInt()}%"
            } catch (e: Exception) {
                Log.e(TAG, "Direct brightness adjustment failed: ${e.message}")
                
                // Try alternative method - open brightness settings
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                
                return "Opening brightness settings for manual adjustment"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to adjust brightness", e)
            return "Failed to adjust brightness: ${e.message}"
        }
    }
    
    fun increaseBrightness(): String {
        try {
            Log.d(TAG, "Attempting to increase brightness")
            
            // Check if we have permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
                // Request permission through settings
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return "Please grant permission to modify system settings for brightness control"
            }
            
            try {
                val currentBrightness = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                )
                Log.d(TAG, "Current brightness level: $currentBrightness")
                
                val newBrightness = (currentBrightness + 51).coerceAtMost(255) // about 20% increase
                return adjustBrightness(newBrightness)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get current brightness", e)
                
                // Fallback - open brightness settings
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                
                return "Opening brightness settings for manual adjustment"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in increaseBrightness", e)
            return "Failed to increase brightness: ${e.message}"
        }
    }
    
    fun decreaseBrightness(): String {
        try {
            Log.d(TAG, "Attempting to decrease brightness")
            
            // Check if we have permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
                // Request permission through settings
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return "Please grant permission to modify system settings for brightness control"
            }
            
            try {
                val currentBrightness = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                )
                Log.d(TAG, "Current brightness level: $currentBrightness")
                
                val newBrightness = (currentBrightness - 51).coerceAtLeast(30) // about 20% decrease, not too dark
                return adjustBrightness(newBrightness)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get current brightness", e)
                
                // Fallback - open brightness settings
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                
                return "Opening brightness settings for manual adjustment"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in decreaseBrightness", e)
            return "Failed to decrease brightness: ${e.message}"
        }
    }
    
    /**
     * WiFi Control Methods
     */
    fun toggleWifi(enable: Boolean): String {
        try {
            Log.d(TAG, "Attempting to ${if (enable) "enable" else "disable"} WiFi")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "Android 10+ detected, using settings panel for WiFi")
                // For Android 10 and above, direct control is not allowed
                // Try using intent broadcast first before panel
                try {
                    // Try using the hidden API via reflection for newer Android versions
                    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val setWifiEnabledMethod = wifiManager::class.java.getDeclaredMethod(
                        "setWifiEnabled", 
                        Boolean::class.java
                    )
                    setWifiEnabledMethod.isAccessible = true
                    val result = setWifiEnabledMethod.invoke(wifiManager, enable) as Boolean
                    
                    if (result) {
                        Log.d(TAG, "Successfully toggled WiFi using reflection")
                        return if (enable) "WiFi enabled" else "WiFi disabled"
                    } else {
                        Log.d(TAG, "Failed to toggle WiFi using reflection, falling back to settings panel")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using reflection for WiFi control: ${e.message}")
                    // Continue to fallback
                }
                
                // Fallback to settings panel
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                panelIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(panelIntent)
                return "Opening WiFi settings panel. Please ${if (enable) "enable" else "disable"} WiFi manually."
            } else {
                // For older Android versions
                try {
                    Log.d(TAG, "Trying direct WiFi control")
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = enable
                    return if (enable) "WiFi enabled" else "WiFi disabled"
                } catch (e: Exception) {
                    Log.e(TAG, "Direct WiFi control failed: ${e.message}")
                    
                    // Fallback to settings
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return "Opening WiFi settings for manual adjustment"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle WiFi", e)
            return "Failed to toggle WiFi: ${e.message}"
        }
    }
    
    /**
     * Bluetooth Control Methods
     */
    fun toggleBluetooth(enable: Boolean): String {
        try {
            Log.d(TAG, "Attempting to ${if (enable) "enable" else "disable"} Bluetooth")
            
            if (bluetoothAdapter == null) {
                return "Bluetooth is not supported on this device"
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.d(TAG, "Android 12+ detected, checking BLUETOOTH_CONNECT permission")
                
                // For Android 12 and above, we need BLUETOOTH_CONNECT permission
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == 
                        PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "BLUETOOTH_CONNECT permission granted, attempting direct control")
                    
                    try {
                        if (enable) {
                            bluetoothAdapter.enable()
                            return "Bluetooth enabled"
                        } else {
                            bluetoothAdapter.disable()
                            return "Bluetooth disabled"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Direct Bluetooth control failed with permission: ${e.message}")
                        // Fall back to reflection as a last resort
                        return tryBluetoothReflection(enable)
                    }
                } else {
                    Log.d(TAG, "BLUETOOTH_CONNECT permission not granted, trying reflection method")
                    
                    // Try reflection method first
                    val reflectionResult = tryBluetoothReflection(enable)
                    if (reflectionResult.contains("successfully")) {
                        return if (enable) "Bluetooth enabled" else "Bluetooth disabled"
                    }
                    
                    // If reflection fails, open settings
                    Log.d(TAG, "Opening Bluetooth settings as fallback")
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return "Opening Bluetooth settings. Please ${if (enable) "enable" else "disable"} Bluetooth manually."
                }
            } else {
                // For older Android versions
                try {
                    Log.d(TAG, "Attempting direct Bluetooth control on older Android")
                    if (enable) {
                        bluetoothAdapter.enable()
                        return "Bluetooth enabled"
                    } else {
                        bluetoothAdapter.disable()
                        return "Bluetooth disabled"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Direct Bluetooth control failed: ${e.message}")
                    
                    // Try reflection method as backup
                    val reflectionResult = tryBluetoothReflection(enable)
                    if (reflectionResult.contains("successfully")) {
                        return if (enable) "Bluetooth enabled" else "Bluetooth disabled"
                    }
                    
                    // If all else fails, go to settings
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return "Opening Bluetooth settings for manual adjustment"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle Bluetooth", e)
            return "Failed to toggle Bluetooth: ${e.message}"
        }
    }
    
    // Helper function to try Bluetooth toggle via reflection
    private fun tryBluetoothReflection(enable: Boolean): String {
        try {
            Log.d(TAG, "Attempting Bluetooth toggle via reflection")
            val method = bluetoothAdapter.javaClass.getMethod(if (enable) "enable" else "disable")
            method.isAccessible = true
            method.invoke(bluetoothAdapter)
            Log.d(TAG, "Bluetooth toggle via reflection completed successfully")
            return "Bluetooth toggle completed successfully via alternative method"
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth reflection method failed: ${e.message}")
            return "Could not toggle Bluetooth via alternative method: ${e.message}"
        }
    }
    
    /**
     * App Launch Methods
     */
    fun launchApp(appName: String): String {
        try {
            Log.d(TAG, "Attempting to launch app: $appName")
            
            // First try direct package name mapping
            val directPackageName = getPackageName(appName)
            Log.d(TAG, "Direct package mapping for '$appName' is: $directPackageName")
            
            if (directPackageName != null) {
                try {
                    val directIntent = packageManager.getLaunchIntentForPackage(directPackageName)
                    if (directIntent != null) {
                        Log.d(TAG, "Found direct package match: $directPackageName")
                        directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(directIntent)
                        return "Opening $appName"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching direct package: ${e.message}")
                }
            }

            // Get installed apps
            val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            }
            
            // Find apps that might match the requested name
            val matchingApps = mutableListOf<Triple<String, String, Float>>() // Package name, Display name, Match score
            val searchName = appName.lowercase().trim()
            
            Log.d(TAG, "Searching for apps matching: '$searchName'")
            
            for (app in installedApps) {
                try {
                    val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
                    val appLabel = packageManager.getApplicationLabel(appInfo).toString()
                    val lowerAppLabel = appLabel.lowercase()
                    val lowerPackageName = app.packageName.lowercase()
                    
                    // Calculate match score
                    var matchScore = calculateMatchScore(searchName, lowerAppLabel, lowerPackageName)
                    
                    // Only add if there's some match and it has a launch intent
                    if (matchScore > 0.2f) { // Increased threshold for better matches
                        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                        if (intent != null) {
                            matchingApps.add(Triple(app.packageName, appLabel, matchScore))
                            Log.d(TAG, "Found potential match: '$appLabel' (${app.packageName}) with score $matchScore")
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing app ${app.packageName}: ${e.message}")
                    continue
                }
            }
            
            // Sort by match score (highest first)
            matchingApps.sortByDescending { it.third }
            
            when {
                matchingApps.isEmpty() -> {
                    Log.d(TAG, "No matching apps found")
                    return "Sorry, I couldn't find an app matching '$appName'"
                }
                matchingApps.size == 1 -> {
                    val (packageName, displayName, _) = matchingApps.first()
                    Log.d(TAG, "Found single match: $displayName ($packageName)")
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return "Opening $displayName"
                    }
                }
                else -> {
                    // Take the best match if it has a significantly higher score
                    val bestMatch = matchingApps.first()
                    val secondBest = matchingApps.getOrNull(1)
                    
                    if (secondBest == null || bestMatch.third > secondBest.third + 0.3f) {
                        // Best match is significantly better
                        val launchIntent = packageManager.getLaunchIntentForPackage(bestMatch.first)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            return "Opening ${bestMatch.second}"
                        }
                    }
                    
                    // Multiple close matches found, show top 3
                    val appList = matchingApps.take(3)
                        .map { it.second }
                        .joinToString(", ")
                    Log.d(TAG, "Multiple matches found: $appList")
                    return "Found multiple matching apps: $appList. Please be more specific."
                }
            }
            
            return "Unable to launch $appName"
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: ${e.message}", e)
            return "Error launching $appName: ${e.message}"
        }
    }
    
    private fun calculateMatchScore(searchName: String, appLabel: String, packageName: String): Float {
        var score = 0.0f
        
        // Exact matches get highest score
        if (appLabel == searchName) {
            score = 1.0f
        } else if (appLabel.contains(searchName)) {
            score = 0.8f
        } else if (searchName.contains(appLabel)) {
            score = 0.7f
        } else {
            // Check for partial word matches
            val searchWords = searchName.split(" ")
            val appWords = appLabel.split(" ")
            
            for (searchWord in searchWords) {
                if (searchWord.length > 2) {
                    // Check if any app word starts with the search word
                    for (appWord in appWords) {
                        if (appWord.startsWith(searchWord)) {
                            score = maxOf(score, 0.6f)
                        } else if (appWord.contains(searchWord)) {
                            score = maxOf(score, 0.4f)
                        }
                    }
                }
            }
            
            // Check package name for matches
            if (packageName.contains(searchName)) {
                score = maxOf(score, 0.5f)
            }
            
            // Check for acronym match (e.g., "gm" matches "Google Maps")
            if (isAcronymMatch(searchName, appLabel)) {
                score = maxOf(score, 0.7f)
            }
        }
        
        return score
    }
    
    private fun isAcronymMatch(searchName: String, appLabel: String): Boolean {
        val words = appLabel.split(" ")
        if (words.size > 1) {
            val acronym = words.joinToString("") { it.take(1) }.lowercase()
            return searchName == acronym
        }
        return false
    }
    
    private fun getPackageName(appName: String): String? {
        // Common package names mapping
        return when (appName.lowercase().trim()) {
            "whatsapp" -> "com.whatsapp"
            "facebook" -> "com.facebook.katana"
            "messenger", "facebook messenger" -> "com.facebook.orca"
            "instagram" -> "com.instagram.android"
            "youtube" -> "com.google.android.youtube"
            "maps", "google maps" -> "com.google.android.apps.maps"
            "chrome", "google chrome" -> "com.android.chrome"
            "camera" -> "com.android.camera2"
            "gallery", "photos" -> "com.google.android.apps.photos"
            "phone", "dialer" -> "com.android.dialer"
            "messages", "sms" -> "com.android.messaging"
            "settings" -> "com.android.settings"
            "play store", "google play" -> "com.android.vending"
            "gmail", "email" -> "com.google.android.gm"
            "calendar" -> "com.google.android.calendar"
            "clock", "alarm" -> "com.android.deskclock"
            "calculator" -> "com.android.calculator2"
            "files", "file manager" -> "com.android.documentsui"
            "contacts" -> "com.android.contacts"
            "twitter", "x" -> "com.twitter.android"
            "telegram" -> "org.telegram.messenger"
            "snapchat" -> "com.snapchat.android"
            "tiktok" -> "com.zhiliaoapp.musically"
            "netflix" -> "com.netflix.mediaclient"
            "spotify" -> "com.spotify.music"
            "google" -> "com.google.android.googlequicksearchbox"
            "drive", "google drive" -> "com.google.android.apps.docs"
            "meet", "google meet" -> "com.google.android.apps.meetings"
            "duo", "google duo" -> "com.google.android.apps.tachyon"
            "keep", "google keep" -> "com.google.android.keep"
            "lens", "google lens" -> "com.google.ar.lens"
            "photos", "google photos" -> "com.google.android.apps.photos"
            "translate", "google translate" -> "com.google.android.apps.translate"
            "waze" -> "com.waze"
            "outlook" -> "com.microsoft.office.outlook"
            "teams", "microsoft teams" -> "com.microsoft.teams"
            "word", "microsoft word" -> "com.microsoft.office.word"
            "excel", "microsoft excel" -> "com.microsoft.office.excel"
            "powerpoint", "microsoft powerpoint" -> "com.microsoft.office.powerpoint"
            "onedrive", "microsoft onedrive" -> "com.microsoft.skydrive"
            "skype" -> "com.skype.raider"
            "linkedin" -> "com.linkedin.android"
            "pinterest" -> "com.pinterest"
            "reddit" -> "com.reddit.frontpage"
            "twitch" -> "tv.twitch.android.app"
            "discord" -> "com.discord"
            "zoom" -> "us.zoom.videomeetings"
            "uber" -> "com.ubercab"
            "lyft" -> "me.lyft.android"
            "amazon" -> "com.amazon.mShop.android.shopping"
            "ebay" -> "com.ebay.mobile"
            "paypal" -> "com.paypal.android.p2pmobile"
            "venmo" -> "com.venmo"
            "cash", "cash app" -> "com.squareup.cash"
            "bank of america", "bofa" -> "com.infonow.bofa"
            "chase" -> "com.chase.sig.android"
            "wells fargo" -> "com.wf.wellsfargomobile"
            "citi", "citibank" -> "com.citi.citimobile"
            else -> null
        }
    }
    
    /**
     * Settings Navigation Methods
     */
    fun openSettings(settingType: String): String {
        val intent = when (settingType.lowercase()) {
            "wifi", "wi-fi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "display", "screen" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound", "audio" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "apps", "applications" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "network" -> Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
            "date", "time" -> Intent(Settings.ACTION_DATE_SETTINGS)
            "language" -> Intent(Settings.ACTION_LOCALE_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "security" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
            "privacy" -> Intent(Settings.ACTION_PRIVACY_SETTINGS)
            "developer" -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return "Opening $settingType settings"
    }
} 