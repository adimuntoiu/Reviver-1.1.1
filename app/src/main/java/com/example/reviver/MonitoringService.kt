package com.example.reviver

import android.app.*
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
import org.json.JSONObject

class MonitoringService : Service() {

    private var lastPackageName: String? = null
    private var lastPackageStartTime: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val interval: Long = 1000 // 1 second
    private lateinit var overlay: Overlay
    private val monitoredApps = mutableListOf<AppDetails>()

    override fun onCreate() {
        super.onCreate()
        overlay = Overlay(this)
        startForegroundServiceWithNotification()
        handler.post(monitorTask)
    }

    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "monitoring_service"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, "App Monitoring Service", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Reviver is Monitoring")
                .setContentText("Monitoring your app usage.")
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

    private fun monitorAppUsage() {
        loadAppSettings() // Reload every time in case settings changed

        val currentPackageName = getLastUsedApp() ?: return
        val app = monitoredApps.find { it.packageName == currentPackageName } ?: return

        if (currentPackageName != lastPackageName) {
            lastPackageName = currentPackageName
            lastPackageStartTime = System.currentTimeMillis()

            // Mode 2: Increment launch count
            if (app.mode == "Mode 2") {
                incrementLaunchCount(currentPackageName)
                saveLaunchCounts()
            }
        }

        if (app.mode == "Mode 1") {
            val elapsedTime = (System.currentTimeMillis() - lastPackageStartTime) / 1000
            if (elapsedTime >= app.timeLimit) {
                showOverlay("Time's up for ${app.appName}")
                lastPackageStartTime = System.currentTimeMillis()
            }
        } else if (app.mode == "Mode 2") {
            if (app.currentOpens >= app.maxOpens) {
                showOverlay("${app.appName} exceeded open limit")
            }
        }
    }

    private fun showOverlay(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e("Overlay", "Permission denied")
            return
        }
        overlay.showOverlay(message)
    }

    private fun getLastUsedApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 1000 * 60 * 60 * 24
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)

        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun loadAppSettings() {
        monitoredApps.clear()
        val prefs = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("selectedApps", "[]") ?: return
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            monitoredApps.add(
                AppDetails(
                    packageName = obj.getString("packageName"),
                    appName = obj.getString("name"),
                    timeLimit = obj.getInt("timeLimit"),
                    mode = obj.getString("mode"),
                    maxOpens = obj.optInt("maxOpens", 0),
                    currentOpens = obj.optInt("currentOpens", 0),
                    background = obj.optString("background", null)
                )
            )
        }
    }

    private fun saveLaunchCounts() {
        val prefs = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val array = JSONArray()
        for (app in monitoredApps) {
            val obj = JSONObject()
            obj.put("packageName", app.packageName)
            obj.put("name", app.appName)
            obj.put("timeLimit", app.timeLimit)
            obj.put("mode", app.mode)
            obj.put("maxOpens", app.maxOpens)
            obj.put("currentOpens", app.currentOpens)
            obj.put("background", app.background ?: "")
            array.put(obj)
        }
        editor.putString("selectedApps", array.toString())
        editor.commit()
    }

    fun incrementLaunchCount(packageName: String) {
        val sharedPreferences = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val jsonString = sharedPreferences.getString("selectedApps", "[]") ?: "[]"

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val appObject = jsonArray.getJSONObject(i)
                if (appObject.getString("packageName") == packageName) {
                    // Handle both string and number types for robustness
                    val current = appObject.optInt("currentOpens", 0)
                    appObject.put("currentOpens", current + 1)
                    break
                }
            }

            sharedPreferences.edit()
                .putString("selectedApps", jsonArray.toString())
                .apply()  // Use commit() for immediate results if needed

        } catch (e: Exception) {
            Log.e("LaunchCount", "Error updating launch count", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorTask)
    }
}
