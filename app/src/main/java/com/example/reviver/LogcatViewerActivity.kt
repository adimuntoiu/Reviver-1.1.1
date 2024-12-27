package com.example.reviver

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogcatViewerActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var backToMainButton: Button
    private val logcatReader = LogcatReader()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat_reader)

        // Initialize UI elements
        logTextView = findViewById(R.id.logTextView)
        backToMainButton = findViewById(R.id.returnButton)

        // Load logs into the TextView
        updateLogs()

        // Handle button click
        backToMainButton.setOnClickListener {
            // Update logs before navigating back
            updateLogs()

            // Navigate back to the main activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateLogs() {
        val logs = logcatReader.getLogs()
        val logString = logs.joinToString(separator = "\n")
        logTextView.text = logString
    }
}
