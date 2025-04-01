package com.example.tars

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.tars.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val PERMISSION_REQUEST_CODE = 123
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Set up sign out button
        binding.signOutButton.setOnClickListener {
            signOut()
        }

        // Load user information
        loadUserInfo()

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        binding.apply {
            startVoiceButton.setOnClickListener {
                if (checkPermissions()) {
                    startVoiceService()
                }
            }

            stopVoiceButton.setOnClickListener {
                stopVoiceService()
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
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
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startVoiceService()
            } else {
                Toast.makeText(this, "Permissions required for voice control", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startVoiceService() {
        try {
            if (!isServiceRunning) {
                val serviceIntent = Intent(this, VoiceRecognitionService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                isServiceRunning = true
                updateStatus(true)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting voice service: ${e.message}", Toast.LENGTH_SHORT).show()
            isServiceRunning = false
            updateStatus(false)
        }
    }

    private fun stopVoiceService() {
        try {
            if (isServiceRunning) {
                val serviceIntent = Intent(this, VoiceRecognitionService::class.java)
                stopService(serviceIntent)
                isServiceRunning = false
                updateStatus(false)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping voice service: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus(isActive: Boolean) {
        binding.statusText.text = if (isActive) "Voice Control: Active" else "Voice Control: Inactive"
        binding.statusText.setTextColor(getColor(if (isActive) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
    }

    private fun loadUserInfo() {
        val user = auth.currentUser
        if (user != null) {
            // Get user data from Firestore
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val name = document.getString("name") ?: "User"
                        binding.userNameText.text = "Hello, $name"
                        binding.userEmailText.text = user.email
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun signOut() {
        binding.progressBar.visibility = View.VISIBLE
        stopVoiceService() // Stop the service before signing out
        auth.signOut()
        // Navigate to login activity and clear back stack
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        try {
            // Check if service is running and update UI accordingly
            isServiceRunning = VoiceRecognitionService.isRunning
            updateStatus(isServiceRunning)
        } catch (e: Exception) {
            Toast.makeText(this, "Error checking service status: ${e.message}", Toast.LENGTH_SHORT).show()
            updateStatus(false)
        }
    }
}