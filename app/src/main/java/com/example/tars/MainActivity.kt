package com.example.tars

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.tars.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Config with context
        Config.init(this)
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
    }

    private fun setupUI() {
        binding.apply {
            aiChatButton.setOnClickListener {
                val intent = Intent(this@MainActivity, ChatBotActivity::class.java)
                startActivity(intent)
            }
        }
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
}