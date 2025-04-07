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
import androidx.navigation.ui.NavigationUI
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.reviver.ui.HomeFragment
import com.example.reviver.ui.StatsFragment
import com.example.reviver.ui.InfoFragment
import com.example.reviver.ui.SettingsFragment
import androidx.fragment.app.Fragment
import java.io.File
import android.content.pm.ApplicationInfo


class MainActivity : AppCompatActivity() {
    private val selectedApps = mutableListOf<AppDetails>() // List of selected apps
    private val mode2LaunchCounts = mutableMapOf<String, Int>()
    private val appListContainer: LinearLayout by lazy {
        findViewById(R.id.appListContainer)
    }
    // val backgroundImage: ImageView = findViewById(R.id.backgroundImage)
    private lateinit var appListScrollView: ScrollView

    private var appEdited: Boolean = false
    companion object {
        private const val APP_PICKER_REQUEST_CODE = 1
    }
    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: Drawable
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestOverlayPermission()

        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        } else {
            startMonitoringService()
        }
        debugPreferences()
        loadSelectedApps()

        val addButton: Button = findViewById(R.id.addButton)
        addButton.setOnClickListener {
            showAppSelectionDialog()
        }

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Set up navigation listener
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
                }
                R.id.nav_stats -> {
                    replaceFragment(StatsFragment())
                }
                R.id.nav_info -> {
                    replaceFragment(InfoFragment())
                }
                R.id.nav_settings -> {
                    replaceFragment(SettingsFragment())
                }
            }
            true
        }
        /*
        bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> {
                    switchFragment("HOME") { HomeFragment() }
                    true
                }
                R.id.nav_settings -> {
                    switchFragment("SETTINGS") { SettingsFragment() }
                    true
                }
                R.id.nav_info -> {
                    switchFragment("INFO") { InfoFragment() }
                    true
                }
                R.id.nav_stats ->{
                    switchFragment("STATS") { StatsFragment() }
                    true
                }
                else -> false
            }
        }
         */
    }

    private fun showAppSelectionDialog() {
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        val launchableApps = installedApps.filter { app ->
            packageManager.getLaunchIntentForPackage(app.packageName) != null
        }.sortedBy { app ->
            packageManager.getApplicationLabel(app).toString().lowercase()
        }

        val appNames = launchableApps.map { app ->
            packageManager.getApplicationLabel(app).toString()
        }

        val selectedItems = BooleanArray(launchableApps.size) // Track which items are checked

        AlertDialog.Builder(this)
            .setTitle("Select Apps")
            .setMultiChoiceItems(appNames.toTypedArray(), selectedItems) { _, which, isChecked ->
                selectedItems[which] = isChecked
            }
            .setPositiveButton("Add") { _, _ ->
                for (i in selectedItems.indices) {
                    if (selectedItems[i]) {
                        val appInfo = launchableApps[i]
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val packageName = appInfo.packageName

                        // Avoid duplicates
                        if (selectedApps.none { it.packageName == packageName }) {
                            val newApp = AppDetails(
                                appName = appName,
                                packageName = packageName,
                                timeLimit = 0,
                                mode = "Mode 1",
                                maxOpens = 0,
                                currentOpens = 0
                            )
                            selectedApps.add(newApp)
                            addAppToMainLayout(newApp)
                        }
                    }
                }
                saveSelectedApps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showAppDetailsDialog(appDetails: AppDetails? = null) {
        val builder = AlertDialog.Builder(this)
        val isEditMode = appDetails != null
        builder.setTitle(if (isEditMode) "Edit ${appDetails?.appName}" else "Add New App")

        val dialogLayout = layoutInflater.inflate(R.layout.app_details_dialog, null)
        val timeLimitInput = dialogLayout.findViewById<EditText>(R.id.timeLimitInput)
        val modeSpinner = dialogLayout.findViewById<Spinner>(R.id.modeSpinner)
        val maxOpensInput = dialogLayout.findViewById<EditText>(R.id.maxOpensInput)

        // Set up spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.modes_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = adapter
        }

        // Set initial values if editing
        if (isEditMode) {
            timeLimitInput.setText(appDetails?.timeLimit?.toString())
            maxOpensInput.setText(appDetails?.maxOpens?.toString())

            // Fixed line - get modes array first, then find index
            val modesArray = resources.getStringArray(R.array.modes_array)
            val modeIndex = modesArray.indexOf(appDetails?.mode ?: "Mode 1")
            modeSpinner.setSelection(if (modeIndex >= 0) modeIndex else 0)
        }


        // Initially hide maxOpensInput if not Mode 2
        maxOpensInput.visibility = if (modeSpinner.selectedItem.toString() == "Mode 2") View.VISIBLE else View.GONE

        // Mode selection listener
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                maxOpensInput.visibility = if (parent?.getItemAtPosition(position).toString() == "Mode 2")
                    View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        builder.setView(dialogLayout)

        builder.setPositiveButton(if (isEditMode) "Save" else "Add") { _, _ ->
            val timeLimit = timeLimitInput.text.toString().toIntOrNull() ?: 0
            val mode = modeSpinner.selectedItem.toString()
            val maxOpens = if (mode == "Mode 2") maxOpensInput.text.toString().toIntOrNull() ?: 0 else 0

            if (isEditMode && appDetails != null) {
                // Update existing app
                appDetails.timeLimit = timeLimit
                appDetails.mode = mode
                appDetails.maxOpens = maxOpens
                saveSelectedApps()
                refreshAppList()
            } else {
                // Add new app (this part needs to be called from the right context)
                val newApp = AppDetails(
                    packageName = "", // You'll need to set this from your app picker
                    appName = "",    // You'll need to set this from your app picker
                    timeLimit = timeLimit,
                    mode = mode,
                    maxOpens = maxOpens
                )
                selectedApps.add(newApp)
                saveSelectedApps()
                addAppToMainLayout(newApp)
            }
        }

        builder.setNegativeButton("Cancel", null)

        if (isEditMode) {
            builder.setNeutralButton("Remove") { _, _ ->
                appDetails?.let {
                    selectedApps.remove(it)
                    saveSelectedApps()
                    refreshAppList()
                }
            }
        }

        builder.create().show()
    }


    private fun editAppDetailsDialog(appDetails: AppDetails, appItemView: ConstraintLayout) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit Details for ${appDetails.appName}")

        val dialogLayout = LayoutInflater.from(this).inflate(R.layout.app_details_dialog, null)
        val timeLimitInput = dialogLayout.findViewById<EditText>(R.id.timeLimitInput)
        val modeSpinner = dialogLayout.findViewById<Spinner>(R.id.modeSpinner)
        val maxOpensInput = dialogLayout.findViewById<EditText>(R.id.maxOpensInput)

        // Set initial values
        timeLimitInput.setText(appDetails.timeLimit.toString())
        maxOpensInput.setText(appDetails.maxOpens.toString())

        // Set up spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.modes_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = adapter
        }
        modeSpinner.setSelection(resources.getStringArray(R.array.modes_array).indexOf(appDetails.mode))

        // Show/hide maxOpensInput based on current mode
        maxOpensInput.visibility = if (appDetails.mode == "Mode 2") View.VISIBLE else View.GONE

        // Mode selection listener
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMode = parent?.getItemAtPosition(position).toString()
                maxOpensInput.visibility = if (selectedMode == "Mode 2") View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        builder.setView(dialogLayout)

        builder.setPositiveButton("Save") { _, _ ->
            val timeLimit = timeLimitInput.text.toString().toIntOrNull() ?: 0
            val mode = modeSpinner.selectedItem.toString()
            val maxOpens = if (mode == "Mode 2") {
                maxOpensInput.text.toString().toIntOrNull() ?: 0
            } else 0

            // Update the app details
            appDetails.timeLimit = timeLimit
            appDetails.mode = mode
            appDetails.maxOpens = maxOpens

            // Update the settings view
            val settingsView = appItemView.getChildAt(2) as? TextView
            settingsView?.text = if (mode == "Mode 2") {
                "Max Opens: $maxOpens, Current: ${appDetails.currentOpens}"
            } else {
                "Limit: $timeLimit seconds, Mode: $mode"
            }

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == APP_PICKER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val selectedComponent = data.component
            val packageName = selectedComponent?.packageName ?: return
            val packageManager = packageManager

            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()

                // Create a new AppDetails with default values
                val newApp = AppDetails(
                    packageName = packageName,
                    appName = appName,
                    timeLimit = 0,
                    mode = "Mode 1",
                    maxOpens = 0,
                    currentOpens = 0
                )

                // Show the dialog to configure the app
                showAppDetailsDialog(newApp)

            } catch (e: PackageManager.NameNotFoundException) {
                Toast.makeText(this, "App not found!", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "App not found: $packageName")
            }
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
        /*
        if (selectedApps.isEmpty()) {
            val backgroundImage: ImageView = findViewById(R.id.backgroundImage)
            backgroundImage.setImageResource(R.drawable.custom_background_no_text)
        }
        */

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
        if (!selectedApps.contains(appDetails)){
            selectedApps.add(appDetails)
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
        val sharedPrefs = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray().apply {
            selectedApps.forEach { app ->
                put(JSONObject().apply {
                    put("packageName", app.packageName)
                    put("name", app.appName)
                    put("timeLimit", app.timeLimit)
                    put("mode", app.mode)
                    put("maxOpens", app.maxOpens)
                    put("currentOpens", app.currentOpens)
                })
            }
        }

        sharedPrefs.edit().apply {
            putString("selectedApps", jsonArray.toString())
            commit() // Using commit() for immediate write
        }

        // Verify save
        Log.d("SaveDebug", "Saved apps: ${sharedPrefs.getString("selectedApps", "")}")
    }

    private fun loadSelectedApps() {
        runCatching {
            val sharedPreferences = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
            val json = sharedPreferences.getString("selectedApps", null)

            Log.d("LoadDebug", "Loaded JSON: ${json?.take(100)}...") // Log first 100 chars

            json?.let {
                JSONArray(it).let { jsonArray ->
                    selectedApps.clear()
                    for (i in 0 until jsonArray.length()) {
                        jsonArray.getJSONObject(i).let { obj ->
                            if (!obj.getString("packageName").contains("$")) {
                                selectedApps.add(AppDetails(
                                    packageName = obj.getString("packageName"),
                                    appName = obj.getString("name"),
                                    timeLimit = obj.getInt("timeLimit"),
                                    mode = obj.getString("mode"),
                                    maxOpens = obj.optInt("maxOpens", 0),
                                    currentOpens = obj.optInt("currentOpens", 0)
                                ))
                            }
                        }
                    }
                }
            }
            Log.d("LoadDebug", "Loaded ${selectedApps.size} apps")
        }.onFailure {
            Log.e("LoadError", "Failed to load apps", it)
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

    private fun replaceFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentContainer, fragment)
        transaction.commit()

        // Ensure we access addButton after the view is fully initialized
        val addButton = findViewById<Button>(R.id.addButton)
        val viewLogsButton = findViewById<Button>(R.id.viewLogsButton)
        val appListScrollView = findViewById<ScrollView>(R.id.appListScrollView)

        // Hide addButton
        when (fragment) {
            is HomeFragment -> addButton.visibility = View.VISIBLE
            is StatsFragment, is InfoFragment, is SettingsFragment -> addButton.visibility = View.INVISIBLE
        }
        // HideScrollView
        when (fragment){
            is HomeFragment -> appListScrollView.visibility = View.VISIBLE
            is StatsFragment, is InfoFragment, is SettingsFragment -> appListScrollView.visibility = View.INVISIBLE
        }
    }
    private val fragmentMap = mutableMapOf<String, Fragment>()

    private fun getFragment(tag: String, creator: () -> Fragment): Fragment {
        return fragmentMap.getOrPut(tag) { creator() }
    }

    private fun switchFragment(tag: String, creator: () -> Fragment) {
        val fragment = getFragment(tag, creator)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, tag)
            .commitAllowingStateLoss()
    }
    private fun refreshAppList() {
        appListContainer.removeAllViews()
        for (app in selectedApps) {
            addAppToMainLayout(app)
        }
    }
    private fun debugPreferences() {
        try {
            val sharedPreferences = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
            val content = sharedPreferences.getString("selectedApps", "NULL")
            Log.d("PrefsContent", "Preferences content: $content")

            // Verify file content directly
            val prefsFile = File(filesDir.parentFile, "shared_prefs/ReviverPrefs.xml")
            if (prefsFile.exists()) {
                Log.d("PrefsContent", "File content:\n${prefsFile.readText()}")
            }
        } catch (e: Exception) {
            Log.e("PrefsContent", "Error reading preferences", e)
        }
    }
}
