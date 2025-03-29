package com.example.tars

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.tars.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val PERMISSION_REQUEST_CODE = 123
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

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
        binding.startVoiceButton.setOnClickListener {
            if (checkPermissions()) {
                startVoiceService()
            } else {
                requestPermissions()
            }
        }

        binding.stopVoiceButton.setOnClickListener {
            stopVoiceService()
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun startVoiceService() {
        val serviceIntent = Intent(this, VoiceRecognitionService::class.java).apply {
            action = "START_LISTENING"
        }
        startService(serviceIntent)
        updateStatus(true)
    }

    private fun stopVoiceService() {
        val serviceIntent = Intent(this, VoiceRecognitionService::class.java).apply {
            action = "STOP_LISTENING"
        }
        startService(serviceIntent)
        updateStatus(false)
    }

    private fun updateStatus(isActive: Boolean) {
        binding.statusText.text = "Voice Control: ${if (isActive) "Active" else "Inactive"}"
        binding.statusText.setTextColor(
            ContextCompat.getColor(
                this,
                if (isActive) R.color.neon_blue else R.color.white
            )
        )
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
        auth.signOut()
        // Navigate to login activity and clear back stack
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceService()
            } else {
                Toast.makeText(
                    this,
                    "Permission denied. Voice recognition will not work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}