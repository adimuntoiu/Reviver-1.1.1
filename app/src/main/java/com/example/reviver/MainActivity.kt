package com.example.reviver

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.reviver.LogcatViewerActivity


class MainActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView // For displaying logs
    private val logcatReader = LogcatReader() // Instance of LogcatReader
    private val handler = Handler(Looper.getMainLooper()) // Handler for periodic updates
    private val logUpdateInterval = 10000L // Update interval (10 seconds)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestOverlayPermission()
        // Check and request Usage Stats permission
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        } else {
            startMonitoringService()
        }

        val viewLogsButton: Button = findViewById(R.id.viewLogsButton)
        viewLogsButton.setOnClickListener {
            val intent = Intent(this, LogcatViewerActivity::class.java)
            startActivity(intent)
        }
    }
    private fun startLogUpdates() {
        handler.post(object : Runnable {
            override fun run() {
                updateLogs()
                handler.postDelayed(this, logUpdateInterval) // Repeat every 10 seconds
            }
        })
    }

    private fun updateLogs() {
        val logs = logcatReader.getLogs()
        val logString = logs.joinToString(separator = "\n")
        logTextView.text = logString
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // Stop updates when activity is destroyed
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Please grant overlay permissions to allow this feature.",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }

    private fun startMonitoringService() {
        val serviceIntent = Intent(this, MonitoringService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Monitoring Service Started", Toast.LENGTH_SHORT).show()
    }
}
