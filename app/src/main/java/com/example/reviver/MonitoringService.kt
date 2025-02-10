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
import org.json.JSONArray

class MonitoringService : Service() {

    private var lastPackageName: String? = null
    private var lastPackageStartTime: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val interval: Long = 1000 // Check every second
    private lateinit var overlay: Overlay
    private val monitoredApps = mutableMapOf<String, Long>() // PackageName -> TimeLimit mapping
    private val appEdited: Boolean = false

    override fun onCreate() {
        overlay = Overlay(this)
        super.onCreate()

        startForegroundServiceWithNotification()
        loadMonitoredApps()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(monitorTask)
        return START_STICKY
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
                .setSmallIcon(R.drawable.ic_notification)
                .build()

            startForeground(1, notification)
        }
    }

    private val monitorTask = object : Runnable {
        override fun run() {
            monitorAppUsage()
            handler.postDelayed(this, interval)
        }
    }

    private fun saveAppEditedFlag() {
        val sharedPreferences = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("appEdited", appEdited)
        editor.apply()
    }

    private fun monitorAppUsage() {
        val currentPackageName = getLastUsedApp()
        loadMonitoredAppsLow()

        if (currentPackageName != null && monitoredApps.containsKey(currentPackageName)) {
            val timeLimit = monitoredApps[currentPackageName] ?: return

            // Reset timer when switching apps
            if (currentPackageName != lastPackageName) {
                lastPackageName = currentPackageName
                lastPackageStartTime = System.currentTimeMillis()
            }

            val elapsedTime = (System.currentTimeMillis() - lastPackageStartTime) / 1000L // Convert to seconds
            Log.d("AppMonitoring", "Current package: $lastPackageName, Elapsed time: $elapsedTime seconds")

            if (elapsedTime >= timeLimit) {
                Log.d("AppMonitoring", "Time limit exceeded for $lastPackageName")
                showOverlayMessage()
                lastPackageStartTime = System.currentTimeMillis() // Reset timer after overlay is shown
            }
        }
    }

    private fun getLastUsedApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 60 * 24 // Last 24 hours

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

    private fun showOverlayMessage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e("MonitoringService", "Overlay permission is not granted")
            return
        }
        overlay.showOverlay("Time limit has been reached. Please take a break.")
        Log.d("MonitoringService", "Overlay is displayed")
    }

    private fun loadMonitoredApps() {
        val sharedPreferences = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("selectedApps", null)

        if (json != null) {
            try {
                val jsonArray = JSONArray(json)
                monitoredApps.clear()

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val packageName = jsonObject.getString("packageName")
                    val timeLimit = jsonObject.getInt("timeLimit").toLong() // Convert Int to Long

                    monitoredApps[packageName] = timeLimit
                }

                Log.d("MonitoringService", "Loaded monitored apps: $monitoredApps") // Debug log
            } catch (e: Exception) {
                Log.e("MonitoringService", "Failed to parse monitored apps", e)
            }
        } else {
            Log.d("MonitoringService", "No monitored apps found in SharedPreferences")
        }
    }

    private fun loadMonitoredAppsLow(){
        val sharedPreferences = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("selectedApps", null)

        if (json != null) {
            val jsonArray = JSONArray(json)
            monitoredApps.clear()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val packageName = jsonObject.getString("packageName")
                val timeLimit = jsonObject.getInt("timeLimit").toLong()

                monitoredApps[packageName] = timeLimit
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorTask)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
