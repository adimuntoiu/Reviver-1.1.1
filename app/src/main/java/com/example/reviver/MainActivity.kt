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
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams

class MainActivity : AppCompatActivity() {
    private val selectedApps = mutableListOf<AppDetails>() // List of selected apps
    private val appListContainer: LinearLayout by lazy {
        findViewById(R.id.appListContainer)
    }
    private var appEdited: Boolean = false
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

    private fun showAppSelectionDialog() {
        val intent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
            type = null
            putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        }
        startActivityForResult(intent, APP_PICKER_REQUEST_CODE)
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



    private fun editAppDetailsDialog(appDetails: AppDetails, appItemView: ConstraintLayout) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit Details for ${appDetails.appName}")

        val dialogLayout = LayoutInflater.from(this).inflate(R.layout.app_details_dialog, null)
        val timeLimitInput = dialogLayout.findViewById<EditText>(R.id.timeLimitInput)
        val modeSpinner = dialogLayout.findViewById<Spinner>(R.id.modeSpinner)

        timeLimitInput.setText(appDetails.timeLimit.toString())

        ArrayAdapter.createFromResource(
            this,
            R.array.modes_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = adapter
        }
        modeSpinner.setSelection(resources.getStringArray(R.array.modes_array).indexOf(appDetails.mode))

        builder.setView(dialogLayout)

        builder.setPositiveButton("Save") { _, _ ->
            val timeLimit = timeLimitInput.text.toString().toIntOrNull() ?: 0
            val mode = modeSpinner.selectedItem.toString()

            // Update the app details
            appDetails.timeLimit = timeLimit
            appDetails.mode = mode

            // Find and update the settings view (which displays the limit and mode)
            val settingsView = appItemView.getChildAt(2) as? TextView
            settingsView?.text = "Limit: ${appDetails.timeLimit} seconds, Mode: ${appDetails.mode}"

            appEdited = true
            saveSelectedApps()
        }

        builder.setNegativeButton("Remove") { _, _ ->
            removeApp(appDetails)
            appListContainer.removeView(appItemView)
        }

        builder.setNeutralButton("Cancel", null)
        builder.create().show()
    }


    /*override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == APP_PICKER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val selectedPackage = data.component?.packageName
            val packageManager = packageManager

            if (selectedPackage != null) {
                val appName = packageManager.getApplicationLabel(packageManager.getApplicationInfo(selectedPackage, 0)).toString()
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
            }
        }
    }
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == APP_PICKER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val selectedComponent = data.component
            val originalPackage = selectedComponent?.packageName
            val packageManager = packageManager
            var finalPackage: String? = originalPackage

            if (originalPackage != null) {
                try {
                    val appInfo = packageManager.getApplicationInfo(originalPackage, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()

                    val appDetails = AppDetails(
                        packageName = originalPackage,
                        appName = appName,
                        timeLimit = 0,
                        mode = "None"
                    )

                    if (!isAppAlreadySelected(originalPackage)) {
                        selectedApps.add(appDetails)
                        saveSelectedApps()
                        addAppToMainLayout(appDetails)
                    } else {
                        Toast.makeText(this, "App already added!", Toast.LENGTH_SHORT).show()
                    }
                    return // ✅ Stop execution if package was found
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e("MainActivity", "App not found: $originalPackage, trying to resolve real package name...")
                }
            }

            // ✅ If app was not found, try finding it in the installed apps list
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val matchedApp = installedApps.find { app ->
                originalPackage?.let { app.packageName.contains(it) } ?: false
            }

            finalPackage = matchedApp?.packageName ?: "com.android.unknown"

            Log.e("MainActivity", "Assigned fallback package: $finalPackage")

            val appDetails = AppDetails(
                packageName = finalPackage,
                appName = "Unknown App ($finalPackage)",
                timeLimit = 0,
                mode = "None"
            )

            selectedApps.add(appDetails)
            saveSelectedApps()
            addAppToMainLayout(appDetails)
        }
    }





    private fun addAppToMainLayout(appDetails: AppDetails) {
        val packageManager = packageManager
        var packageName = appDetails.packageName
        var appName = appDetails.appName
        var appIcon: Drawable? = null

        try {
            appIcon = packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("MainActivity", "App not found: $packageName, assigning default name")
            packageName = "com.android.${appDetails.appName.lowercase().replace(" ", "")}"
            appName = "Unknown App (${appDetails.appName})" // Provide a fallback display name
        }

        val appItemView = ConstraintLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 16, 16, 16) // Increased margins
            }
        }

        // App Icon
        val iconView = ImageView(this).apply {
            id = View.generateViewId()
            try {
                setImageDrawable(packageManager.getApplicationIcon(appDetails.packageName))
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e("MainActivity", "Failed to load icon for ${appDetails.packageName}, using default icon.")
                setImageResource(R.drawable.ic_notification) // Replace with a default icon
            }
            layoutParams = LayoutParams(120, 120).apply {
                marginEnd = 16
            }
        }
        appItemView.addView(iconView)

        // App Name
        val nameView = TextView(this).apply {
            id = View.generateViewId()
            text = "${appDetails.appName}"
            textSize = 16f
            maxLines = 2 // Allow text to wrap up to two lines
            ellipsize = android.text.TextUtils.TruncateAt.END // Add "..." if the text is too long
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = 16 // Add margin between text and button
            }
        }
        appItemView.addView(nameView)

        // App settings
        val settingsView = TextView(this).apply {
            id = View.generateViewId()
            text = "Limit: ${appDetails.timeLimit} seconds, Mode: ${appDetails.mode}" // Show settings here
            textSize = 14f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8
            }
        }
        appItemView.addView(settingsView)

        // Remove Button
        val removeButton = Button(this).apply {
            id = View.generateViewId()
            text = "Remove"
            setOnClickListener {
                removeApp(appDetails)
                appListContainer.removeView(appItemView)
            }
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
        }
        appItemView.addView(removeButton)

        // Set layout constraints
        (iconView.layoutParams as LayoutParams).apply {
            startToStart = LayoutParams.PARENT_ID
            topToTop = LayoutParams.PARENT_ID
            bottomToBottom = LayoutParams.PARENT_ID
        }
        (nameView.layoutParams as LayoutParams).apply {
            startToEnd = iconView.id
            topToTop = LayoutParams.PARENT_ID
            endToStart = removeButton.id
        }
        (settingsView.layoutParams as LayoutParams).apply {
            startToEnd = iconView.id
            topToBottom = nameView.id
            endToStart = removeButton.id
        }
        (removeButton.layoutParams as LayoutParams).apply {
            endToEnd = LayoutParams.PARENT_ID
            topToTop = LayoutParams.PARENT_ID
            bottomToBottom = LayoutParams.PARENT_ID
        }

        appItemView.setOnClickListener {
            editAppDetailsDialog(appDetails, appItemView)
        }

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
            if (app.packageName.contains("$")) {
                Log.e("MainActivity", "Skipping invalid package during save: ${app.packageName}")
                continue
            }
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
                val packageName = jsonObject.getString("packageName")

                if (packageName.contains("$")) {
                    Log.e("MainActivity", "Skipping invalid package: $packageName")
                    continue // Ignore this entry
                }

                val app = AppDetails(
                    packageName = packageName,
                    appName = jsonObject.getString("name"),
                    timeLimit = jsonObject.getInt("timeLimit"),
                    mode = jsonObject.getString("mode")
                )
                selectedApps.add(app)
            }

            for (app in selectedApps) {
                try {
                    addAppToMainLayout(app)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to add ${app.packageName} to layout", e)
                }
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