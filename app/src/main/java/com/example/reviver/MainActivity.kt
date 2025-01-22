package com.example.reviver

import android.app.AppOpsManager
import android.content.pm.ApplicationInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.*
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private val selectedApps = mutableListOf<AppDetails>() // List of selected apps
    private val appListContainer: LinearLayout by lazy {
        findViewById(R.id.appListContainer)
    }
    companion object {
        private const val APP_PICKER_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestOverlayPermission()

        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        } else {
            startMonitoringService()
        }

        loadSelectedApps()

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

    private fun showAppDetailsDialog(appDetails: AppDetails) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Set Details for ${appDetails.appName}")

        val dialogLayout = layoutInflater.inflate(R.layout.app_details_dialog, null)
        val timeLimitInput = dialogLayout.findViewById<EditText>(R.id.timeLimitInput)
        val modeSpinner = dialogLayout.findViewById<Spinner>(R.id.modeSpinner)

        ArrayAdapter.createFromResource(
            this,
            R.array.modes_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = adapter
        }

        builder.setView(dialogLayout)

        builder.setPositiveButton("Add") { _, _ ->
            val timeLimit = timeLimitInput.text.toString().toIntOrNull() ?: 0
            val mode = modeSpinner.selectedItem.toString()

            val updatedAppDetails = appDetails.copy(timeLimit = timeLimit, mode = mode)
            selectedApps.add(updatedAppDetails)

            saveSelectedApps()
            addAppToMainLayout(updatedAppDetails)
        }

        builder.setNegativeButton("Cancel", null)
        builder.create().show()
    }

    private fun showAppSelectionDialog() {
        val intent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
            type = null
            putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        }
        startActivityForResult(intent, APP_PICKER_REQUEST_CODE)
    }

    /**
     * Handles the app picker result
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == APP_PICKER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val selectedComponent = data.component
            val selectedPackage = selectedComponent?.packageName
            val packageManager = packageManager

            if (selectedPackage != null) {
                try {
                    val appName = packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(selectedPackage, 0)
                    ).toString()
                    val appIcon = packageManager.getApplicationIcon(selectedPackage)

                    val appDetails = AppDetails(
                        packageName = selectedPackage,
                        appName = appName,
                        timeLimit = 0,
                        mode = "None"
                    )

                    if (!isAppAlreadySelected(selectedPackage)) {
                        selectedApps.add(appDetails)
                        saveSelectedApps()
                        addAppToMainLayout(appDetails)
                    } else {
                        Toast.makeText(this, "App already added!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    Toast.makeText(this, "Failed to retrieve app details.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addAppToMainLayout(appDetails: AppDetails) {
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
            setImageDrawable(packageManager.getApplicationIcon(appDetails.packageName))
            layoutParams = LinearLayout.LayoutParams(100, 100).apply {
                marginEnd = 16
            }
        }


        val nameView = TextView(this).apply {
            text = "${appDetails.appName} (Limit: ${appDetails.timeLimit} mins, Mode: ${appDetails.mode})"
            textSize = 16f
        }

        val removeButton = Button(this).apply {
            text = "Remove"
            setOnClickListener {
                removeApp(appDetails)
                appListContainer.removeView(appItemView)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }


        appItemView.addView(iconView)
        appItemView.addView(nameView)
        appItemView.addView(removeButton)

        appListContainer.addView(appItemView)
    }
    private fun removeApp(appDetails: AppDetails) {
        selectedApps.removeIf { it.packageName == appDetails.packageName }
        saveSelectedApps()
    }

    private fun saveSelectedApps() {
        val sharedPreferences = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val jsonArray = JSONArray()
        for (app in selectedApps) {
            val jsonObject = JSONObject()
            jsonObject.put("packageName", app.packageName)
            jsonObject.put("name", app.appName)
            jsonObject.put("timeLimit", app.timeLimit)
            jsonObject.put("mode", app.mode)
            jsonArray.put(jsonObject)
        }

        val jsonString = jsonArray.toString()
        Log.d("MainActivity", "Saved JSON: $jsonString") // Debug log

        editor.putString("selectedApps", jsonString)
        editor.apply()
    }

    private fun loadSelectedApps() {
        val sharedPreferences = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("selectedApps", null)

        if (json != null) {
            Log.d("MainActivity", "Loaded JSON: $json") // Debug log

            val jsonArray = JSONArray(json)
            selectedApps.clear()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val app = AppDetails(
                    packageName = jsonObject.getString("packageName"),
                    appName = jsonObject.getString("name"),
                    timeLimit = jsonObject.getInt("timeLimit"),
                    mode = jsonObject.getString("mode")
                )
                selectedApps.add(app)
            }

            for (app in selectedApps) {
                addAppToMainLayout(app)
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
        serviceIntent.putStringArrayListExtra(
            "monitoredApps",
            ArrayList(selectedApps.map { it.packageName })

        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun isAppAlreadySelected(packageName: String): Boolean {
        return selectedApps.any { it.packageName == packageName }
    }
}
