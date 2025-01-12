package com.example.reviver

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.*
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import org.json.JSONArray
import java.io.File
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private val selectedAppsFile = "selected_apps.json" // File to store selected apps
    private val appListContainer: LinearLayout by lazy {
        findViewById(R.id.appListContainer)
    }

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

        val addButton: Button = findViewById(R.id.addButton)
        addButton.setOnClickListener {
            showAppSelectionDialog()
        }
    }


    private fun showAppSelectionDialog() {
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        // Create lists to store app details
        val appNames = mutableListOf<String>()
        val appIcons = mutableListOf<Drawable>()
        val appPackages = mutableListOf<String>()

        // Filter and add non-system apps to the list
        for (app in installedApps) {
            if (packageManager.getLaunchIntentForPackage(app.packageName) != null) {
                appNames.add(app.loadLabel(packageManager).toString())
                appIcons.add(app.loadIcon(packageManager))
                appPackages.add(app.packageName)
            }
        }

        val appItems = appNames.zip(appIcons) // Combine names and icons

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose an app")

        // Create RecyclerView programmatically
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        // Create the adapter and set it to the RecyclerView
        val appListAdapter = AppListAdapter(this, appItems).apply {
            onAppSelected = { selectedIndex ->
                val selectedAppName = appNames[selectedIndex]
                val selectedAppIcon = appIcons[selectedIndex]
                val selectedPackage = appPackages[selectedIndex]

                addAppToMainLayout(selectedAppName, selectedAppIcon)
                saveSelectedApp(selectedPackage, selectedAppName)

                // Dismiss the dialog after selection
                builder.create().dismiss()
            }
        }
        recyclerView.adapter = appListAdapter

        // Set the RecyclerView in the dialog
        builder.setView(recyclerView)
        builder.create().show()
    }

    private fun addAppToMainLayout(appName: String, appIcon: Drawable) {
        // Create a new layout to display the app details
        val appItemView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 16)
            }
        }

        val iconView = ImageView(this).apply {
            setImageDrawable(appIcon)
            layoutParams = LinearLayout.LayoutParams(100, 100).apply {
                marginEnd = 16
            }
        }

        val nameView = TextView(this).apply {
            text = appName
            textSize = 16f
        }

        appItemView.addView(iconView)
        appItemView.addView(nameView)

        appListContainer.addView(appItemView)
    }


    private fun saveSelectedApp(packageName: String, appName: String) {
        // Save the app details (e.g., to a file or shared preferences)
        val sharedPreferences = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(packageName, appName)
        editor.apply()
    }

    private fun loadSelectedApps() {
        val file = File(filesDir, selectedAppsFile)
        if (!file.exists()) return

        val jsonArray = JSONArray(file.readText())
        val packageManager = packageManager

        for (i in 0 until jsonArray.length()) {
            val appData = jsonArray.getJSONObject(i)
            val appName = appData.getString("appName")
            val packageName = appData.getString("packageName")

            val appIcon = try {
                packageManager.getApplicationIcon(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            if (appIcon != null) {
                addAppToMainLayout(appName, appIcon)
            }
        }
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
