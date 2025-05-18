package com.example.tars

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
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
    private val bluetoothManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }
    
    companion object {
        private const val TAG = "PhoneControlService"
    }
    
    /**
     * Volume Control Methods
     */
    fun adjustVolume(isUp: Boolean): String {
        val result = if (isUp) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
            "Volume increased"
        } else {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            "Volume decreased"
        }
        return result
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
            val packageManager = context.packageManager
            
            // First try direct package name mapping
            val directPackageName = getPackageName(appName)
            Log.d(TAG, "Direct package mapping for '$appName' is: $directPackageName")
            
            if (directPackageName != appName) { // Only if we found a mapping
                val directIntent = packageManager.getLaunchIntentForPackage(directPackageName)
                if (directIntent != null) {
                    Log.d(TAG, "Found direct package match: $directPackageName")
                    directIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(directIntent)
                    return "Launching $appName"
                }
            }
            
            // Get installed apps
            val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0)
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
                    
                    // Calculate match score - exact match gets highest score
                    var matchScore = 0.0f
                    
                    if (lowerAppLabel == searchName) {
                        matchScore = 1.0f
                    } else if (lowerAppLabel.contains(searchName)) {
                        matchScore = 0.8f
                    } else if (searchName.contains(lowerAppLabel)) {
                        matchScore = 0.6f
                    } else {
                        // Check for partial word matches (e.g., "whats" matching "whatsapp")
                        val searchWords = searchName.split(" ")
                        for (word in searchWords) {
                            if (word.length > 2 && lowerAppLabel.contains(word)) {
                                matchScore = maxOf(matchScore, 0.4f)
                            }
                        }
                        
                        // Check for acronyms (e.g., "fb" for "facebook")
                        if (isAcronymMatch(searchName, lowerAppLabel)) {
                            matchScore = maxOf(matchScore, 0.5f)
                        }
                    }
                    
                    // Only add if there's some match and it has a launch intent
                    if (matchScore > 0 && packageManager.getLaunchIntentForPackage(app.packageName) != null) {
                        matchingApps.add(Triple(app.packageName, appLabel, matchScore))
                        Log.d(TAG, "Potential match: '$appLabel' with score $matchScore")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing app ${app.packageName}: ${e.message}")
                    continue
                }
            }
            
            // Sort by match score (highest first)
            matchingApps.sortByDescending { it.third }
            
            if (matchingApps.isNotEmpty()) {
                // Launch the best matching app
                val (packageName, displayName, _) = matchingApps.first()
                Log.d(TAG, "Launching best match: '$displayName' ($packageName)")
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return "Launching $displayName"
                }
            }
            
            Log.d(TAG, "No matching app found for: $appName")
            return "Cannot find app: $appName. Please check if it's installed or try with a different name."
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app: $appName", e)
            return "Failed to launch $appName: ${e.message}"
        }
    }
    
    // Helper to check if input might be an acronym of app name
    private fun isAcronymMatch(input: String, appName: String): Boolean {
        // Handle common acronyms
        val commonAcronyms = mapOf(
            "fb" to "facebook",
            "ig" to "instagram",
            "wa" to "whatsapp",
            "yt" to "youtube",
            "gm" to "gmail",
            "li" to "linkedin",
            "tg" to "telegram",
            "sc" to "snapchat"
        )
        
        // Check common acronyms dictionary
        if (commonAcronyms.containsKey(input) && appName.contains(commonAcronyms[input]!!)) {
            return true
        }
        
        // Try to match first letters of words
        val words = appName.split(" ")
        if (words.size > 1) {
            val acronym = words.joinToString("") { it.take(1) }
            return input == acronym
        }
        
        return false
    }
    
    private fun getPackageName(appName: String): String {
        // This is a mapping of common app names to package names
        val appMapping = mapOf(
            // Google apps
            "settings" to "com.android.settings",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "chrome" to "com.android.chrome",
            "google chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "email" to "com.google.android.gm",
            "mail" to "com.google.android.gm",
            "youtube" to "com.google.android.youtube",
            "calculator" to "com.google.android.calculator",
            "calendar" to "com.google.android.calendar",
            "google calendar" to "com.google.android.calendar",
            "phone" to "com.google.android.dialer",
            "dialer" to "com.google.android.dialer",
            "google phone" to "com.google.android.dialer",
            "messages" to "com.google.android.apps.messaging",
            "sms" to "com.google.android.apps.messaging",
            "google messages" to "com.google.android.apps.messaging",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "google photos" to "com.google.android.apps.photos",
            "camera" to "com.android.camera2",
            "clock" to "com.google.android.deskclock",
            "alarm" to "com.google.android.deskclock",
            "google clock" to "com.google.android.deskclock",
            "play store" to "com.android.vending",
            "google play" to "com.android.vending",
            "drive" to "com.google.android.apps.docs",
            "google drive" to "com.google.android.apps.docs",
            "contacts" to "com.google.android.contacts",
            "google contacts" to "com.google.android.contacts",
            
            // Samsung apps
            "samsung gallery" to "com.sec.android.gallery3d",
            "samsung messages" to "com.samsung.android.messaging",
            "samsung camera" to "com.sec.android.app.camera",
            "samsung calendar" to "com.samsung.android.calendar",
            "samsung browser" to "com.sec.android.app.sbrowser",
            "galaxy store" to "com.sec.android.app.samsungapps",
            
            // Social media
            "facebook" to "com.facebook.katana",
            "instagram" to "com.instagram.android",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "snapchat" to "com.snapchat.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "messenger" to "com.facebook.orca",
            "facebook messenger" to "com.facebook.orca",
            "pinterest" to "com.pinterest",
            "linkedin" to "com.linkedin.android",
            
            // Streaming
            "netflix" to "com.netflix.mediaclient",
            "spotify" to "com.spotify.music",
            "amazon prime" to "com.amazon.avod.thirdpartyclient",
            "prime video" to "com.amazon.avod.thirdpartyclient",
            "disney plus" to "com.disney.disneyplus",
            "disney+" to "com.disney.disneyplus",
            "hulu" to "com.hulu.plus",
            "youtube music" to "com.google.android.apps.youtube.music",
            
            // Productivity
            "microsoft office" to "com.microsoft.office.officehubrow",
            "office" to "com.microsoft.office.officehubrow",
            "word" to "com.microsoft.office.word",
            "microsoft word" to "com.microsoft.office.word",
            "excel" to "com.microsoft.office.excel",
            "microsoft excel" to "com.microsoft.office.excel",
            "powerpoint" to "com.microsoft.office.powerpoint",
            "microsoft powerpoint" to "com.microsoft.office.powerpoint",
            "outlook" to "com.microsoft.office.outlook",
            "microsoft outlook" to "com.microsoft.office.outlook",
            "onedrive" to "com.microsoft.skydrive",
            "microsoft onedrive" to "com.microsoft.skydrive",
            "evernote" to "com.evernote",
            "google docs" to "com.google.android.apps.docs.editors.docs",
            "docs" to "com.google.android.apps.docs.editors.docs",
            "google sheets" to "com.google.android.apps.docs.editors.sheets",
            "sheets" to "com.google.android.apps.docs.editors.sheets",
            "google slides" to "com.google.android.apps.docs.editors.slides",
            "slides" to "com.google.android.apps.docs.editors.slides"
        )
        
        // Search the mapping case-insensitively
        for ((key, value) in appMapping) {
            if (appName.equals(key, ignoreCase = true)) {
                return value
            }
        }
        
        return appName // Return as-is if not in the mapping
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
    
    /**
     * System Navigation Methods
     */
    fun performSystemAction(actionType: String): String {
        try {
            Log.d(TAG, "Attempting system action: $actionType")
            
            when (actionType.lowercase()) {
                "home", "go home", "home screen" -> {
                    // Send HOME intent
                    val intent = Intent(Intent.ACTION_MAIN)
                    intent.addCategory(Intent.CATEGORY_HOME)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return "Going to home screen"
                }
                
                "back", "go back" -> {
                    // Try to simulate back button via AccessibilityService or instrumentation
                    if (simulateBackButton()) {
                        return "Going back"
                    }
                    return "Unable to go back. This requires special permissions."
                }
                
                "recent apps", "recents", "show recent apps", "recent tasks" -> {
                    // This generally requires special permissions, but we'll try
                    if (openRecentApps()) {
                        return "Showing recent apps"
                    }
                    return "Unable to show recent apps. This requires special permissions."
                }
                
                "lock", "lock screen" -> {
                    if (lockScreen()) {
                        return "Locking screen"
                    }
                    return "Unable to lock screen. This requires device administrator permissions."
                }
                
                "notifications", "open notifications", "show notifications" -> {
                    if (openNotifications()) {
                        return "Opening notifications"
                    }
                    return "Unable to open notifications. This requires special permissions."
                }
                
                "quick settings", "open quick settings" -> {
                    if (openQuickSettings()) {
                        return "Opening quick settings"
                    }
                    return "Unable to open quick settings. This requires special permissions."
                }
                
                "split screen" -> {
                    if (activateSplitScreen()) {
                        return "Activating split screen mode"
                    }
                    return "Unable to activate split screen. This requires special permissions."
                }
                
                "screenshot" -> {
                    if (takeScreenshot()) {
                        return "Taking screenshot"
                    }
                    return "Unable to take screenshot. This requires special permissions."
                }
                
                "do not disturb" -> {
                    if (toggleDoNotDisturb()) {
                        return "Toggling Do Not Disturb mode"
                    }
                    return "Opening Do Not Disturb settings"
                }
                
                "power saving" -> {
                    if (togglePowerSaving()) {
                        return "Toggling power saving mode"
                    }
                    return "Opening battery settings"
                }
                
                "flashlight" -> {
                    if (toggleFlashlight()) {
                        return "Toggling flashlight"
                    }
                    return "Unable to toggle flashlight. This may not be supported on your device."
                }
                
                else -> return "Unknown system action: $actionType"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform system action: $actionType", e)
            return "Failed to perform action: ${e.message}"
        }
    }
    
    private fun simulateBackButton(): Boolean {
        try {
            // Try using instrumentation if available (works on some devices, requires permission)
            try {
                val runtime = Runtime.getRuntime()
                runtime.exec("input keyevent " + android.view.KeyEvent.KEYCODE_BACK)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to simulate back via runtime exec: ${e.message}")
            }
            
            // Try alternative methods
            try {
                // Try using accessibility service if enabled
                // This is a partial implementation that will need to be expanded
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return false // Return false because we're just showing settings
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error in simulateBackButton", e)
            return false
        }
    }
    
    private fun openRecentApps(): Boolean {
        try {
            // Try different methods to open recent apps
            
            // Method 1: Try using system service on newer Android versions
            try {
                // For newer Android versions, try using overview screen intent
                val serviceIntent = Intent("com.android.systemui.TOGGLE_RECENTS")
                serviceIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(serviceIntent)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show recents via intent: ${e.message}")
            }
            
            // Method 2: Try using runtime command (requires permission)
            try {
                val runtime = Runtime.getRuntime()
                runtime.exec("input keyevent " + android.view.KeyEvent.KEYCODE_APP_SWITCH)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show recents via runtime command: ${e.message}")
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error in openRecentApps", e)
            return false
        }
    }
    
    private fun lockScreen(): Boolean {
        try {
            // Method 1: Try using DevicePolicyManager (requires device admin app)
            try {
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                // This requires the app to be a device administrator
                devicePolicyManager.lockNow()
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to lock using DevicePolicyManager: ${e.message}")
            }
            
            // Method 2: Show information to become device admin
            try {
                val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return false // Not really successful, just showing admin screen
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show device admin screen: ${e.message}")
            }
            
            // Method 3: For older devices, try to lock using power manager (rarely works)
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val powerManagerClass = powerManager::class.java
                val goToSleepMethod = powerManagerClass.getMethod("goToSleep", Long::class.java)
                goToSleepMethod.invoke(powerManager, System.currentTimeMillis())
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to lock using PowerManager: ${e.message}")
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error in lockScreen", e)
            return false
        }
    }
    
    private fun openNotifications(): Boolean {
        try {
            // Try using reflection to access StatusBarManager (works on some devices)
            try {
                val statusBarService = context.getSystemService("statusbar")
                val statusBarManager = Class.forName("android.app.StatusBarManager")
                val expandNotificationsPanel = statusBarManager.getMethod("expandNotificationsPanel")
                expandNotificationsPanel.invoke(statusBarService)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open notifications via reflection: ${e.message}")
            }
            
            // Alternative method - try using runtime command
            try {
                val runtime = Runtime.getRuntime()
                runtime.exec("service call statusbar 1")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open notifications via runtime command: ${e.message}")
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error in openNotifications", e)
            return false
        }
    }
    
    private fun openQuickSettings(): Boolean {
        try {
            // Try using reflection to access StatusBarManager for quick settings
            try {
                val statusBarService = context.getSystemService("statusbar")
                val statusBarManager = Class.forName("android.app.StatusBarManager")
                val expandSettingsPanel = statusBarManager.getMethod("expandSettingsPanel")
                expandSettingsPanel.invoke(statusBarService)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open quick settings via reflection: ${e.message}")
            }
            
            // Alternative method - try using runtime command
            try {
                val runtime = Runtime.getRuntime()
                runtime.exec("service call statusbar 2")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open quick settings via runtime command: ${e.message}")
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error in openQuickSettings", e)
            return false
        }
    }
    
    private fun activateSplitScreen(): Boolean {
        try {
            // Method 1: Try using runtime command (requires permission)
            try {
                val runtime = Runtime.getRuntime()
                // KEYCODE_TOGGLE_SPLIT_SCREEN is 285
                runtime.exec("input keyevent 285")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to activate split screen via runtime command: ${e.message}")
            }
            
            // Method 2: Try accessibility service route
            try {
                // Direct user to accessibility settings to enable this functionality
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error in activateSplitScreen", e)
            return false
        }
    }
    
    private fun takeScreenshot(): Boolean {
        try {
            // Method 1: Try using runtime command (requires permission)
            try {
                val runtime = Runtime.getRuntime()
                // Different devices may use different key combinations
                runtime.exec("input keyevent 120") // KEYCODE_SCREENSHOT on some devices
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take screenshot via runtime command: ${e.message}")
            }
            
            // Method 2: Alternative key combination
            try {
                val runtime = Runtime.getRuntime()
                // Simulate pressing power + volume down
                runtime.exec("input keyevent KEYCODE_POWER+KEYCODE_VOLUME_DOWN")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take screenshot via key combination: ${e.message}")
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error in takeScreenshot", e)
            return false
        }
    }
    
    private fun toggleDoNotDisturb(): Boolean {
        try {
            // Requires notification policy access permission
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Check if app has notification policy access
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    // Toggle DND mode
                    val currentMode = notificationManager.currentInterruptionFilter
                    val newMode = if (currentMode != android.app.NotificationManager.INTERRUPTION_FILTER_NONE) {
                        android.app.NotificationManager.INTERRUPTION_FILTER_NONE // Turn on DND
                    } else {
                        android.app.NotificationManager.INTERRUPTION_FILTER_ALL // Turn off DND
                    }
                    notificationManager.setInterruptionFilter(newMode)
                    return true
                } else {
                    // Request notification policy access permission
                    val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return false
                }
            } else {
                // For older Android versions, open sound settings
                val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in toggleDoNotDisturb", e)
            
            // Fallback to opening sound settings
            try {
                val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open sound settings: ${e2.message}")
            }
            
            return false
        }
    }
    
    private fun togglePowerSaving(): Boolean {
        try {
            // Open battery saver settings
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            
            // We can't directly toggle power saving mode without system app privileges
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error in togglePowerSaving", e)
            return false
        }
    }
    
    private fun toggleFlashlight(): Boolean {
        try {
            // Method 1: Try using CameraManager for devices with Android M and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                    val cameraId = cameraManager.cameraIdList[0] // Usually the back camera
                    
                    // Get current torch state via reflection
                    val getTorchModeMethod = cameraManager.javaClass.getDeclaredMethod("getTorchMode", String::class.java)
                    val currentTorchMode = getTorchModeMethod.invoke(cameraManager, cameraId) as Boolean
                    
                    // Toggle torch mode
                    cameraManager.setTorchMode(cameraId, !currentTorchMode)
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to toggle flashlight via CameraManager: ${e.message}")
                }
            }
            
            // Method 2: Open torch/flashlight app or quick settings
            try {
                // Try to open a common flashlight app
                val packageManager = context.packageManager
                val intent = packageManager.getLaunchIntentForPackage("com.motorola.flashlight") 
                    ?: packageManager.getLaunchIntentForPackage("com.asus.flashlight")
                    ?: packageManager.getLaunchIntentForPackage("com.oppo.flashlight")
                    
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return true
                } else {
                    // If no flashlight app found, open quick settings
                    return openQuickSettings()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open flashlight app: ${e.message}")
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error in toggleFlashlight", e)
            return false
        }
    }
} 