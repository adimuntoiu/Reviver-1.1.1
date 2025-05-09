package com.example.reviver

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import android.app.usage.UsageEvents
import android.content.res.Configuration
import kotlinx.coroutines.*
import java.util.Locale

class MonitoringService : Service() {

    private var lastPackageName: String? = null
    private var lastPackageStartTime: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val interval: Long = 1000 // 1 second
    private lateinit var overlay: Overlay
    private val monitoredApps = mutableListOf<AppDetails>()
    private val mode4Timers = mutableMapOf<String, Long>() // Store individual timers for Mode 4 apps
    private var lastForegroundTime = 0L
    private var appInBackground = true
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val ONGOING_NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "reviver_channel_01"
        private const val WAKELOCK_TAG = "reviver:monitoring_wakelock"
    }
    
    override fun onCreate() {
        super.onCreate()
        overlay = Overlay(this)
        
        // Create notification channel for Android O and above
        createNotificationChannel()
        
        // Start as foreground service with persistent notification
        startForeground(ONGOING_NOTIFICATION_ID, createPersistentNotification())
        
        // Acquire partial wake lock to keep service running
        acquireWakeLock()
        
        // Start monitoring task
        handler.post(monitorTask)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_title),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    getString(R.string.notification_content)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun createPersistentNotification(): Notification {
        // Create intent to open app when notification is tapped
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
                PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Reviver is running")
            .setContentText("Monitoring your app usage")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSound(null)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        ).apply {
            acquire(10*60*1000L /*10 minutes*/)
        }
    }

    private val monitorTask = object : Runnable {
        override fun run() {
            checkAndResetDailyLaunchCounts()
            monitorAppUsage()
            handler.postDelayed(this, interval)
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

    private fun monitorAppUsage() {
        loadAppSettings()
        
        val currentPackageName = getLastUsedApp()
        
        // Check if we've gone to home screen or app switcher (app exit)
        if (currentPackageName == null || isHomeOrRecentsScreen(currentPackageName)) {
            // App was exited, reset timers for Mode 4
            if (!appInBackground) {
                Log.d("Mode4Tracker", "App went to background, setting flag")
                appInBackground = true
                
                // Save the last package name before clearing it
                val previousPackage = lastPackageName
                lastPackageName = ""
                
                // Reset Mode 4 timers for the app that went to background
                if (previousPackage != null) {
                    val app = monitoredApps.find { it.packageName == previousPackage }
                    if (app?.mode == "Mode 4 (Constant Overlay)") {
                        mode4Timers[previousPackage] = System.currentTimeMillis()
                        Log.d("Mode4Tracker", "Reset timer for $previousPackage as it went to background")
                    }
                }
            }
            return
        }

        val app = monitoredApps.find { it.packageName == currentPackageName } ?: return
        
        // Track app switching and returning from background
        if (currentPackageName != lastPackageName || appInBackground) {
            Log.d("AppTracker", "App switch or returning from background: $currentPackageName")
            lastPackageName = currentPackageName
            lastPackageStartTime = System.currentTimeMillis()
            
            // Reset Mode 4 timer when returning to the app from background
            if (app.mode == "Mode 4 (Constant Overlay)") {
                if (appInBackground) {
                    // Reset the timer when coming back from background
                    mode4Timers[currentPackageName] = System.currentTimeMillis()
                    Log.d("Mode4Tracker", "Reset timer for $currentPackageName (coming from background)")
                } else if (!mode4Timers.containsKey(currentPackageName)) {
                    // Initialize timer if it doesn't exist
                    mode4Timers[currentPackageName] = System.currentTimeMillis()
                    Log.d("Mode4Tracker", "New timer for $currentPackageName")
                }
            }
            
            appInBackground = false
        }

        lastForegroundTime = System.currentTimeMillis()
        
        when (app.mode) {
            "Mode 1 (Time Limit)"  -> handleTimeLimit(app)
            "Mode 2 (Launch Limit)" -> checkAndUpdateAppLaunches(app)
            "Mode 3 (Password Protected)" -> handlePasswordMode(app)
            "Mode 4 (Constant Overlay)" -> handleConstantOverlay(app)

            "Mod 1 (Limită de timp)" -> handleTimeLimit(app)
            "Mod 2 (Limită deschideri)" -> checkAndUpdateAppLaunches(app)
            "Mod 3 (Protejat cu parolă)" -> handlePasswordMode(app)
            "Mod 4 (Suprapunere constantă)" -> handleConstantOverlay(app)
        }
    }
    
    private fun isHomeOrRecentsScreen(packageName: String): Boolean {
        val systemUiPackages = listOf(
            "com.android.launcher",
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oneplus.launcher",
            "com.microsoft.launcher"
        )
        return systemUiPackages.any { packageName.contains(it) }
    }

    private fun checkAndUpdateAppLaunches(app: AppDetails) {
        if (app.currentOpens >= app.maxOpens && app.maxOpens > 0) {
            showOverlay(getString(R.string.exceeded_open_limit,app.appName))
            return
        }
        
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000L * 60 * 60 * 12

        val usageEvents = usm.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()

        val foregroundEventTimes = mutableListOf<Long>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)

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

            if (mostRecentLaunch > lastProcessedLaunch) {
                app.currentOpens++
                saveUpdatedAppCount(app)

                Log.d("LaunchTracker", "NEW LAUNCH for ${app.appName}, count now: ${app.currentOpens}")
                prefs.edit().putLong("last_launch_${app.packageName}", mostRecentLaunch).apply()
            }
        }
    }

    private fun handleConstantOverlay(app: AppDetails) {
        val appTimer = mode4Timers.getOrPut(app.packageName) { 
            System.currentTimeMillis() 
        }
        
        val elapsedTime = (System.currentTimeMillis() - appTimer) / 1000
        Log.d("Mode4Tracker", "App: ${app.appName}, Elapsed: $elapsedTime, Limit: ${app.timeLimit}")
        
        if (elapsedTime >= app.timeLimit && app.timeLimit > 0) {
            showOverlay(getString(R.string.time_up_message, app.appName))
        }
    }

    private fun handleTimeLimit(app: AppDetails) {
        val elapsedTime = (System.currentTimeMillis() - lastPackageStartTime) / 1000
        if (elapsedTime >= app.timeLimit && app.timeLimit > 0) {
            showOverlay(getString(R.string.time_up_message,app.appName))
            lastPackageStartTime = System.currentTimeMillis()
        }
    }

    private fun handlePasswordMode(app: AppDetails) {
        if (app.password.isNullOrEmpty()) {
            // If no password set, fallback to time limit
            handleTimeLimit(app)
        } else {
            val elapsedTime = (System.currentTimeMillis() - lastPackageStartTime) / 1000
            if (elapsedTime >= app.timeLimit && app.timeLimit > 0) {
                showOverlay(getString(R.string.password_required_message,app.appName))
                lastPackageStartTime = System.currentTimeMillis()
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

    private fun showOverlay(message: String) {
        val localizedContext = getLocalizedContext()
        val currentApp = monitoredApps.find { it.packageName == lastPackageName }
        if (currentApp != null) {
            overlay.showOverlay(
                localizedContext.getString(R.string.time_up_message, currentApp.appName), // Use localized string
                currentApp
            )
        } else {
            overlay.showOverlay(message)
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
        
        // Release wake lock if held
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        // Restart our service if it's killed
        val restartIntent = Intent(applicationContext, MonitoringService::class.java)
        restartIntent.setPackage(packageName)
        startService(restartIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("RESET_PACKAGE")?.let { packageName ->
            monitoredApps.find { it.packageName == packageName }?.let { app ->
                app.currentOpens = 0
                saveLaunchCounts()
                
                // Also reset Mode 4 timer if applicable
                if (app.mode == "Mode 4 (Constant Overlay)") {
                    mode4Timers[packageName] = System.currentTimeMillis()
                    Log.d("Mode4Tracker", "Reset timer for ${app.appName} via RESET_PACKAGE intent")
                }
            }
        }
        
        // Ensure wake lock is acquired
        if (wakeLock?.isHeld != true) {
            acquireWakeLock()
        }
        
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        
        // Create a restart intent for our service
        val restartServiceIntent = Intent(applicationContext, MonitoringService::class.java)
        restartServiceIntent.setPackage(packageName)
        
        // Create a pending intent that will restart our service when triggered
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 
            1, 
            restartServiceIntent, 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE 
            else 
                PendingIntent.FLAG_ONE_SHOT
        )
        
        // Schedule service restart with AlarmManager
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
        
        Log.d("MonitoringService", "Service scheduled for restart after task removed")
    }

    private fun getLocalizedContext(): Context {
        val sharedPrefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = sharedPrefs.getString("app_lang", "en") ?: "en"
        val locale = Locale(langCode)
        val config = Configuration(resources.configuration).apply {
            setLocale(locale)
        }
        return createConfigurationContext(config)
    }
}
