package com.example.reviver

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LogcatViewerActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private val logcatReader = LogcatReader()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat_reader)

        logTextView = findViewById(R.id.logTextView)
        val copyLogsButton: Button = findViewById(R.id.copyLogsButton)
        val backToMainButton: Button = findViewById(R.id.backToMainButton)

        // Enable scrolling for the TextView
        logTextView.movementMethod = android.text.method.ScrollingMovementMethod()

        // Populate the log view
        updateLogs()

        copyLogsButton.setOnClickListener {
            copyLogsToClipboard()
        }

        backToMainButton.setOnClickListener {
            updateLogs() // Refresh logs
            navigateToMainPage() // Navigate to main
        }
    }

    private fun updateLogs() {
        val logs = logcatReader.getLogs()
        val logString = logs.joinToString(separator = "\n")
        logTextView.text = logString
    }

    private fun copyLogsToClipboard() {
        val logs = logTextView.text.toString()
        if (logs.isNotEmpty()) {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Logcat Logs", logs)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No logs to copy", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToMainPage() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP // Clears all other activities in the stack
        startActivity(intent)
    }
}