package com.example.tars

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.tars.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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