package com.example.reviver

import android.app.*
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
import android.app.usage.UsageEvents

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

    private fun checkAndResetDailyLaunchCounts() {
        val prefs = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val lastResetTime = prefs.getLong("lastResetTime", 0L)
        val now = System.currentTimeMillis()

        if (now - lastResetTime >= 24 * 60 * 60 * 1000L) { // 24 hours passed
            Log.d("LaunchReset", "Resetting all currentOpens counters (24 hours passed)")

            for (app in monitoredApps) {
                app.currentOpens = 0
            }
            saveLaunchCounts()

            prefs.edit()
                .putLong("lastResetTime", now)
                .apply()
        }
    }


    private val monitorTask = object : Runnable {
        override fun run() {
            checkAndResetDailyLaunchCounts()
            monitorAppUsage()
            handler.postDelayed(this, interval)
        }
    }

    private fun monitorAppUsage() {
        loadAppSettings()

        val currentPackageName = getLastUsedApp() ?: return
        val app = monitoredApps.find { it.packageName == currentPackageName } ?: return

        when (app.mode) {
            "Mode 2 (Launch Limit)" -> {
                checkAndUpdateAppLaunches(app)
            }
            else -> {
                if (currentPackageName != lastPackageName) {
                    lastPackageName = currentPackageName
                    lastPackageStartTime = System.currentTimeMillis()
                }
                
                when (app.mode) {
                    "Mode 1 (Time Limit)" -> handleTimeLimit(app)
                    "Mode 3 (Password Protected)" -> handlePasswordMode(app)
                }
            }
        }
    }
    private fun checkAndUpdateAppLaunches(app: AppDetails) {
        if (app.currentOpens > app.maxOpens) {
            showOverlay("${app.appName} exceeded open limit")
        }
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        // Look back 12 hours (adjust timeframe as needed)
        val startTime = endTime - 1000L * 60 * 60 * 12

        // Query for specific app events
        val usageEvents = usm.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()

        // Track foreground events for our specific app
        val foregroundEventTimes = mutableListOf<Long>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)

            // Only process events for our target app
            if (event.packageName == app.packageName) {
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    foregroundEventTimes.add(event.timeStamp)
                    Log.d("LaunchTracker", "Foreground event for ${app.appName} at ${event.timeStamp}")
                }
            }
        }

        // If we have foreground events and the most recent one is new
        if (foregroundEventTimes.isNotEmpty()) {
            val mostRecentLaunch = foregroundEventTimes.maxOrNull() ?: 0L
            val prefs = getSharedPreferences("LaunchTracking", Context.MODE_PRIVATE)
            val lastProcessedLaunch = prefs.getLong("last_launch_${app.packageName}", 0L)

            Log.d("LaunchTracker", "Most recent: $mostRecentLaunch, Last processed: $lastProcessedLaunch")

            // If this is a new launch we haven't counted yet
            if (mostRecentLaunch > lastProcessedLaunch) {
                // Increment the counter
                app.currentOpens++
                Log.d("LaunchTracker", "NEW LAUNCH for ${app.appName}, count now: ${app.currentOpens}")

                // Save the updated count to app settings
                saveUpdatedAppCount(app)

                // Remember that we've processed this launch
                prefs.edit().putLong("last_launch_${app.packageName}", mostRecentLaunch).apply()

                // Check if we need to show the overlay
            }
        }
    }
    private fun saveUpdatedAppCount(app: AppDetails) {
        val prefs = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        try {
            val jsonArray = JSONArray(prefs.getString("selectedApps", "[]"))

            // Find and update the app's entry
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("packageName") == app.packageName) {
                    obj.put("currentOpens", app.currentOpens)
                    break
                }
            }

            // Save the updated JSON
            val success = prefs.edit().putString("selectedApps", jsonArray.toString()).commit()
            Log.d("LaunchTracker", "Saved count for ${app.appName}: ${app.currentOpens}, success=$success")
        } catch (e: Exception) {
            Log.e("LaunchTracker", "Error saving app count", e)
        }
    }
    private fun handleTimeLimit(app: AppDetails){
        val elapsedTime = (System.currentTimeMillis() - lastPackageStartTime) / 1000
        if (elapsedTime >= app.timeLimit) {
            showOverlay("Time's up for ${app.appName}")
            lastPackageStartTime = System.currentTimeMillis()
        }
    }

    private fun handlePasswordMode(app: AppDetails) {
        if (app.password.isNullOrEmpty()) {
            // If no password set, fallback to time limit
            handleTimeLimit(app)
        } else {
            val elapsedTime = (System.currentTimeMillis()-lastPackageStartTime) / 1000
            if (elapsedTime >= app.timeLimit) {
                showOverlay("Password required for ${app.appName}")
                lastPackageStartTime = System.currentTimeMillis()
            }
        }
    }

    private fun showOverlay(message: String) {
        if (!Settings.canDrawOverlays(this)) {
            Log.e("Overlay", "Permission denied")
            return
        }
        val currentApp = monitoredApps.find { it.packageName == lastPackageName }
        if (currentApp != null) {
            overlay.showOverlay(message, currentApp) // <-- FIXED
        } else {
            overlay.showOverlay(message) // fallback if somehow app not found
        }
    }

    private fun getLastUsedApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        // look back 12 hours (adjust as needed)
        val startTime = endTime - 1000L * 60 * 60 * 12

        val usageEvents = usm.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var lastPackage: String? = null

        // Walk through all events; the final MOVE_TO_FOREGROUND is our target
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
            }
        }

        Log.d("MonitoringService", "getLastUsedApp(): $lastPackage")
        return lastPackage
    }

    private fun loadAppSettings() {
        val prefs = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("selectedApps", "[]") ?: "[]"

        try {
            val newApps = JSONArray(json).let { array ->
                List(array.length()) { i ->
                    array.getJSONObject(i).let { obj ->
                        AppDetails(
                            packageName = obj.getString("packageName"),
                            appName = obj.getString("name"),
                            timeLimit = obj.getInt("timeLimit"),
                            mode = obj.getString("mode"),
                            maxOpens = obj.optInt("maxOpens", 0),
                            currentOpens = obj.optInt("currentOpens", 0),
                            password = obj.optString("password", "")
                        )
                    }
                }
            }

            // Merge with existing monitoredApps to preserve state
            monitoredApps.apply {
                clear()
                addAll(newApps)
            }
            Log.d("AppLoad", "Loaded ${monitoredApps.size} apps")
        } catch (e: Exception) {
            Log.e("AppLoad", "Error loading apps", e)
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
            obj.put("password", app.password)
            array.put(obj)
        }
        editor.putString("selectedApps", array.toString())
        editor.apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorTask)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("RESET_PACKAGE")?.let { packageName ->
            monitoredApps.find { it.packageName == packageName }?.let { app ->
                app.currentOpens = 0
                saveLaunchCounts()
            }
        }
        return START_STICKY
    }

}
