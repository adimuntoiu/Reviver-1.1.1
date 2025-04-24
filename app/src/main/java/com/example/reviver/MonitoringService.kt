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
import com.example.reviver.AppDetails

class MonitoringService : Service() {

    private var lastPackageName: String? = null
    private var lastPackageStartTime: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val interval: Long = 1000 // 1 second
    private lateinit var overlay: Overlay
    private val monitoredApps = mutableListOf<AppDetails>()
    private var lastCountedPackage: String? = null // tracks which package has already been counted for this session

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

            if (app.mode == "Mode 2 (Launch Limit)") {
                incrementLaunchCount(app)
            }
        }

        if (app.packageName!="Reviver") {
            when (app.mode) {
                "Mode 1 (Time Limit)" -> handleTimeLimit(app)
                "Mode 2 (Launch Limit)" -> handleLaunchLimit(app,currentPackageName)
                "Mode 3 (Password Protected)" -> handlePasswordMode(app)
            }
        }
    }
    private fun handleTimeLimit(app: AppDetails){
        val elapsedTime = (System.currentTimeMillis() - lastPackageStartTime) / 1000
        if (elapsedTime >= app.timeLimit) {
            showOverlay("Time's up for ${app.appName}")
            lastPackageStartTime = System.currentTimeMillis()
        }
    }

    private fun handleLaunchLimit(app: AppDetails, currentPackageName: String){
        if (lastCountedPackage != currentPackageName) {
            incrementLaunchCount(app)
            lastCountedPackage = currentPackageName
        }

        if (app.currentOpens > app.maxOpens) {
            showOverlay("${app.appName} exceeded open limit")
        }
    }

    private fun handlePasswordMode(app: AppDetails) {
        val backgroundUri = app.background
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e("Overlay", "Permission denied")
            return
        }
        val selectedApps = loadSelectedApps()
        val currentApp = monitoredApps.find { it.packageName == lastPackageName }
        val backgroundUri = currentApp?.background
        overlay.showOverlay(message, backgroundUri, currentApp)
    }

    private fun getLastUsedApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 1000 * 60 * 60 * 24
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)

        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
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
                            background = obj.optString("background", null),
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
            obj.put("background", app.background ?: "")
            obj.put("password", app.password)
            array.put(obj)
        }
        editor.putString("selectedApps", array.toString())
        editor.commit()
    }

    fun incrementLaunchCount(app: AppDetails) {
        // Update in-memory first
        app.currentOpens++

        // Immediately persist to SharedPreferences
        val prefs = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray(prefs.getString("selectedApps", "[]")).apply {
            for (i in 0 until length()) {
                val obj = getJSONObject(i)
                if (obj.getString("packageName") == app.packageName) {
                    obj.put("currentOpens", app.currentOpens)
                }
            }
        }

        prefs.edit().putString("selectedApps", jsonArray.toString()).apply()
        Log.d("LaunchCount", "Saved ${app.appName} opens: ${app.currentOpens}")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorTask)
    }

    private fun loadSelectedApps(): List<AppDetails> {
        val sharedPreferences = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("selectedApps", null) ?: return emptyList()

        return try {
            JSONArray(json).let { jsonArray ->
                List(jsonArray.length()) { i ->
                    jsonArray.getJSONObject(i).let { obj ->
                        AppDetails(
                            packageName = obj.getString("packageName"),
                            appName = obj.getString("name"),
                            timeLimit = obj.getInt("timeLimit"),
                            mode = obj.getString("mode"),
                            maxOpens = obj.optInt("maxOpens", 0),
                            currentOpens = obj.optInt("currentOpens", 0),
                            background = obj.optString("background"),
                            password = obj.optString("password")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
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
