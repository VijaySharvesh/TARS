package com.example.tars

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    private lateinit var apiKeyInput: EditText
    private lateinit var saveButton: Button
    private lateinit var apiKeyInfo: TextView
    private lateinit var apiKeyLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Initialize Config
        Config.init(this)

        // Initialize views
        apiKeyInput = findViewById(R.id.apiKeyInput)
        saveButton = findViewById(R.id.saveButton)
        apiKeyInfo = findViewById(R.id.apiKeyInfo)
        apiKeyLabel = findViewById(R.id.apiKeyLabel)

        // Set current API key if available
        val currentApiKey = Config.getGrokKey()
        if (currentApiKey.isNotEmpty()) {
            apiKeyInput.setText(currentApiKey)
        }

        // Check if API key is set
        if (currentApiKey.isNotEmpty()) {
            startChatBot()
            return
        }

        saveButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isNotEmpty()) {
                Config.setGrokKey(apiKey)
                startChatBot()
            } else {
                Toast.makeText(this, "Please enter a valid API key", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startChatBot() {
        startActivity(Intent(this, ChatBotActivity::class.java))
        finish()
    }
} 