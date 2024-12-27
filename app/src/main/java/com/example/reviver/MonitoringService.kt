package com.example.reviver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import android.provider.Settings

class MonitoringService : Service() {

    private var lastPackageName: String? = null
    private var lastPackageStartTime: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val interval: Long = 1000 // 1 second for monitoring
    private val timeLimit: Long = 20 // 1 minute time limit
    private lateinit var overlay: Overlay

    override fun onCreate() {
        overlay = Overlay(this)
        super.onCreate()

        // Ensure startForeground() is called immediately
        startForegroundServiceWithNotification()

        // Start the monitoring task
        handler.post(monitorTask)
    }

    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "monitoring_service"
            val channelName = "App Monitoring Service"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("App Monitoring")
                .setContentText("Monitoring app usage")
                .setSmallIcon(R.drawable.ic_notification) // Change this to your icon
                .build()

            startForeground(1, notification)
        }
    }

    private val monitorTask = object : Runnable {
        override fun run() {
            monitorAppUsage()
            handler.postDelayed(this, interval) // Re-run every 1 second
        }
    }

    private fun monitorAppUsage() {
        val currentPackageName = getLastUsedApp()

        // If the app has changed, reset the timer
        if (currentPackageName != null && currentPackageName != lastPackageName) {
            lastPackageName = currentPackageName
            lastPackageStartTime = System.currentTimeMillis() // Reset the timer
        }

        // Calculate elapsed time in seconds
        val elapsedTime = (System.currentTimeMillis() - lastPackageStartTime) / 1000 // In seconds

        Log.d("AppMonitoring", "Current package: $lastPackageName, Elapsed time: $elapsedTime seconds")

        if (lastPackageName != null &&
            lastPackageName != "com.google.android.apps.nexuslauncher" &&
            lastPackageName != "com.google.android.dialer" &&
            lastPackageName != "com.example.reviver") {
            if (elapsedTime >= timeLimit) {
                Log.d("AppMonitoring", "Time limit exceeded for $lastPackageName")
                showOverlayMessage("Hello from Reviver!")
                /// triggerOverlay()
                // Reset the start time but keep lastPackageName
                lastPackageStartTime = System.currentTimeMillis()
            }
        }
    }

    private fun getLastUsedApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 60 * 24 // Check for the last 24 hours

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        var lastUsedApp: UsageStats? = null
        if (usageStatsList != null && usageStatsList.isNotEmpty()) {
            for (usageStats in usageStatsList) {
                if (lastUsedApp == null || usageStats.lastTimeUsed > lastUsedApp.lastTimeUsed) {
                    lastUsedApp = usageStats
                }
            }
        }

        return lastUsedApp?.packageName
    }


    private fun showOverlayMessage(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e("MonitoringService", "Overlay permission is not granted")
            // Optionally notify the user or take alternative action
            return
        }
        overlay.showOverlay("salut")
        Log.d("MonitoringService", "Overlay is displayed")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorTask) // Stop the monitoring task
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // No binding
    }
    private fun triggerOverlay() {
        val overlay = Overlay(this)
        overlay.showOverlay("salut")

        // Hide the overlay after a few seconds if needed
        Handler(Looper.getMainLooper()).postDelayed({
            ///overlay.hideOverlay()
        }, 5000) // 5 seconds delay
    }

}
