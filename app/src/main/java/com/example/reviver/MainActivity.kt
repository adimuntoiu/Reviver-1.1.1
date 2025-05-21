package com.example.reviver

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.*
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.reviver.ui.HomeFragment
import com.example.reviver.ui.StatsFragment
import com.example.reviver.ui.InfoFragment
import com.example.reviver.ui.SettingsFragment
import androidx.fragment.app.Fragment
import android.content.pm.ApplicationInfo
import android.view.ViewGroup
import android.app.Dialog
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.Window
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val selectedApps = mutableListOf<AppDetails>()
    private val appListContainer: LinearLayout by lazy {
        findViewById(R.id.appListContainer)
    }

    private var appEdited: Boolean = false
    private val NOTIFICATION_PERMISSION_REQ_CODE = 4321
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPrefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val lang = sharedPrefs.getString("app_lang", "en") ?: "en"
        setAppLocale(lang)
        setContentView(R.layout.activity_main)

        /// Trebuie sa avem permisiuniile necesare ca sa deschidem aplicatia
        ensureNotificationPermission()
        checkAndRequestOverlayPermission()
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        } else {
            startMonitoringService()
        }

        loadAndDisplaySelectedApps()
        /// Butonul de adaugare a unei aplicatii acceseaza functia showAppSelectionDialog
        val addButton: Button = findViewById(R.id.addButton)
        addButton.setOnClickListener {
            showAppSelectionDialog()
        }

        /// Aici schimbam elementele unei pagini din meniu
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
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
        handleEditAppIntent(intent)

        val lastFragment = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            .getString("last_fragment", "HomeFragment")

        val initialFragment = when (lastFragment) {
            "SettingsFragment" -> SettingsFragment()
            "InfoFragment" -> InfoFragment()
            "StatsFragment" -> StatsFragment()
            else -> HomeFragment()
        }
        replaceFragment(initialFragment)
    }

    /// Meniu custom pentru a adauga o aplicatie
    private fun showAppSelectionDialog() {
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        val launchableApps = installedApps.filter {
            packageManager.getLaunchIntentForPackage(it.packageName) != null
        }.sortedBy {
            packageManager.getApplicationLabel(it).toString().lowercase()
        }

        val selectedItems = BooleanArray(launchableApps.size)

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val listView = ListView(this)
        val adapter =
            object : ArrayAdapter<ApplicationInfo>(this, R.layout.app_list_item, launchableApps) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val inflater = LayoutInflater.from(context)
                    val row = inflater.inflate(R.layout.app_list_item, parent, false)

                    val appIcon = row.findViewById<ImageView>(R.id.appIcon)
                    val appName = row.findViewById<TextView>(R.id.appName)
                    val checkBox = row.findViewById<CheckBox>(R.id.appCheckBox)

                    val appInfo = getItem(position)!!
                    appIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo))
                    appName.text = packageManager.getApplicationLabel(appInfo).toString()

                    checkBox.isChecked = selectedItems[position]
                    checkBox.setOnCheckedChangeListener { _, isChecked ->
                        selectedItems[position] = isChecked
                    }

                    row.setOnClickListener {
                        checkBox.isChecked = !checkBox.isChecked
                    }

                    return row
                }
            }

        listView.adapter = adapter
        dialogLayout.addView(listView)

        AlertDialog.Builder(this)
            .setTitle(R.string.select_apps_title)
            .setView(dialogLayout)
            .setPositiveButton(R.string.add_button) { _, _ ->
                for (i in selectedItems.indices) {
                    if (selectedItems[i]) {
                        val appInfo = launchableApps[i]
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val packageName = appInfo.packageName

                        if (selectedApps.none { it.packageName == packageName }) {
                            val newApp = AppDetails(
                                appName = appName,
                                packageName = packageName,
                                timeLimit = 0,
                                mode = "Mode 1 (Time Limit)",
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
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private fun editAppDetailsDialog(appDetails: AppDetails, appItemView: ConstraintLayout) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.app_details_dialog)


        fun Int.dpToPx(context: Context): Int {
            return (this * context.resources.displayMetrics.density).toInt()
        }

        dialog.window?.apply {
            setLayout(380.dpToPx(context), 400.dpToPx(context))
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
        }

        val timeLimitInput = dialog.findViewById<EditText>(R.id.timeLimitInput)
        val modeSpinner = dialog.findViewById<Spinner>(R.id.modeSpinner)
        val maxOpensInput = dialog.findViewById<EditText>(R.id.maxOpensInput)
        val yourPasswordView = dialog.findViewById<TextView>(R.id.yourPassword)
        val passwordInput = dialog.findViewById<EditText>(R.id.passwordInput)
        val modeEntries = resources.getStringArray(R.array.modes_array)
        val modeValues = arrayOf("mode1", "mode2", "mode3", "mode4")


        timeLimitInput.setText(appDetails.timeLimit.toString())
        maxOpensInput.setText(appDetails.maxOpens.toString())

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            modeEntries
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = it
        }

        val position = modeValues.indexOf(appDetails.mode)
        modeSpinner.setSelection(if (position >= 0) position else 0)

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val selected = modeSpinner.selectedItem.toString()
                maxOpensInput.visibility =
                    if (selected.startsWith("Mode 2")) View.VISIBLE else View.GONE
                timeLimitInput.visibility =
                    if (selected.startsWith("Mode 2")) View.GONE else View.VISIBLE
                passwordInput.visibility =
                    if (selected.startsWith("Mode 3")) View.VISIBLE else View.GONE
                yourPasswordView.visibility =
                    if (selected.startsWith("Mode 3")) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val saveButton = dialog.findViewById<Button>(R.id.saveButton)
        val removeButton =
            dialog.findViewById<Button>(R.id.removeButton)
        val cancelButton =
            dialog.findViewById<Button>(R.id.cancelButton)

        yourPasswordView.text = if (!appDetails.password.isNullOrEmpty()) {
            getString(R.string.current_password, appDetails.password)
        } else {
            getString(R.string.no_password_set)
        }

        saveButton.setOnClickListener {
            val timeLimit = timeLimitInput.text.toString().toIntOrNull() ?: 0
            val mode = modeSpinner.selectedItem.toString()
            val maxOpens = maxOpensInput.text.toString().toIntOrNull() ?: 0
            val password = passwordInput.text.toString().trim()

            appDetails.timeLimit = timeLimit
            appDetails.mode = mode
            appDetails.maxOpens = maxOpens
            if (password != "") appDetails.password = password

            if (getModeType(mode) == 2 && appDetails.currentOpens <= 0) {
                appDetails.currentOpens = 0
            }

            val settingsView = appItemView.getChildAt(2) as? TextView
            val modeType = getModeType(mode)
            settingsView?.text = if (modeType == 2) {
                getString(R.string.max_opens_format, maxOpens, appDetails.currentOpens)
            } else {
                getString(R.string.time_limit_mode_format, timeLimit, mode)
            }

            appEdited = true
            saveSelectedApps()
            dialog.dismiss()
        }

        removeButton.setOnClickListener {
            removeApp(appDetails)
            appListContainer.removeView(appItemView)
            dialog.dismiss()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addAppToMainLayout(appDetails: AppDetails) {
        val packageManager = packageManager
        val appItemView = ConstraintLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 16, 16, 16)
            }
            setBackgroundResource(R.drawable.app_list_item_background)
            setPadding(24, 24, 24, 24)
            tag = appDetails.packageName
        }

        /// Iconita aplicatiei
        val iconView = ImageView(this).apply {
            id = View.generateViewId()
            try {
                setImageDrawable(packageManager.getApplicationIcon(appDetails.packageName))
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(
                    "MainActivity",
                    "Failed to load icon for ${appDetails.packageName}, using default icon."
                )
                setImageResource(R.drawable.ic_notification)
            }
            layoutParams = LayoutParams(120, 120).apply {
                marginEnd = 16
            }
        }
        appItemView.addView(iconView)

        /// Numele aplicatiei
        val nameView = TextView(this).apply {
            id = View.generateViewId()
            text = "${appDetails.appName}"
            textSize = 16f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = 16
                marginStart = 16
            }
        }
        appItemView.addView(nameView)

        /// Setarile aplicatiei
        val modeType = getModeType(appDetails.mode)
        val localizedModeName = getLocalizedModeName(appDetails.mode)
        val settingsView = TextView(this).apply {
            id = View.generateViewId()
            text = if (modeType == 2) {
                getString(R.string.max_opens_format, appDetails.maxOpens, appDetails.currentOpens)
            } else {
                getString(R.string.time_limit_mode_format, appDetails.timeLimit, localizedModeName)
            }
            textSize = 14f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8
                leftMargin = 20
            }
        }
        appItemView.addView(settingsView)

        /// Butonul de stergere
        val removeButton = Button(this).apply {
            id = View.generateViewId()
            background = ContextCompat.getDrawable(context, R.drawable.remove_button_bg)
            setOnClickListener {
                removeApp(appDetails)
                appListContainer.removeView(appItemView)
            }
            layoutParams = LayoutParams(48.dp, 48.dp).apply {
                marginEnd = 8.dp
                endToEnd = LayoutParams.PARENT_ID
                topToTop = LayoutParams.PARENT_ID
                bottomToBottom = LayoutParams.PARENT_ID
            }
        }
        appItemView.addView(removeButton)
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
        val appDoesntExistText = findViewById<TextView>(R.id.appDoesntExistText)
        appDoesntExistText.visibility = View.INVISIBLE

        if (!selectedApps.contains(appDetails)) {
            selectedApps.add(appDetails)
        }

        appItemView.setOnClickListener {
            editAppDetailsDialog(appDetails, appItemView)
        }

        appListContainer.addView(appItemView)
    }

    private fun removeApp(appDetails: AppDetails) {
        val initialCount = selectedApps.size
        selectedApps.removeIf { it.packageName == appDetails.packageName }
        if (selectedApps.size == initialCount) {
            Log.e("RemoveError", "Failed to remove app: ${appDetails.packageName}")
            return
        }
        runOnUiThread {
            appListContainer.removeAllViews()
            selectedApps.forEach { addAppToMainLayout(it) }
        }
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
                    put("password", app.password)
                })
            }

        }

        Log.d("SaveApps", "Saving JSON: ${jsonArray}")

        val editor = sharedPrefs.edit()
        editor.putString("selectedApps", jsonArray.toString())
        val success = editor.commit()

        if (success) {
            Log.d("SaveApps", "Successfully saved app data")
        } else {
            Log.e("SaveApps", "Failed to save app data")
        }

        val savedJson = sharedPrefs.getString("selectedApps", "")
        Log.d("SaveApps", "Verification - Read back: ${savedJson?.take(100)}...")

        val appDoesntExistText = findViewById<TextView>(R.id.appDoesntExistText)
        if (hasApps()) {
            appDoesntExistText.visibility = View.INVISIBLE
        } else {
            appDoesntExistText.visibility = View.VISIBLE
        }
    }

    private fun loadSelectedApps() {
        runCatching {
            val sharedPreferences = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
            val json = sharedPreferences.getString("selectedApps", null)

            Log.d("LoadDebug", "Loaded JSON: ${json?.take(100)}...")

            json?.let {
                JSONArray(it).let { jsonArray ->
                    selectedApps.clear()
                    for (i in 0 until jsonArray.length()) {
                        jsonArray.getJSONObject(i).let { obj ->
                            if (!obj.getString("packageName").contains("$")) {
                                selectedApps.add(
                                    AppDetails(
                                        packageName = obj.getString("packageName"),
                                        appName = obj.getString("name"),
                                        timeLimit = obj.getInt("timeLimit"),
                                        mode = obj.getString("mode"),
                                        maxOpens = obj.optInt("maxOpens", 0),
                                        currentOpens = obj.optInt("currentOpens", 0),
                                        password = obj.optString("password", "")
                                    )
                                )
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
                R.string.overlay_permission_toast,
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


    private fun replaceFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentContainer, fragment)
        transaction.commit()

        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragmentContainer, fragment)
            commit()
        }
        val addButton = findViewById<Button>(R.id.addButton)
        val appListScrollView = findViewById<ScrollView>(R.id.appListScrollView)
        val appDoesntExistText = findViewById<TextView>(R.id.appDoesntExistText)

        when (fragment) {
            is HomeFragment -> {
                addButton.visibility = View.VISIBLE
                appListScrollView.visibility = View.VISIBLE
                if (hasApps()) {
                    appDoesntExistText.visibility = View.INVISIBLE
                } else {
                    appDoesntExistText.setText(R.string.no_apps_message)
                    appDoesntExistText.visibility = View.VISIBLE
                }
            }

            is StatsFragment, is InfoFragment, is SettingsFragment -> {
                addButton.visibility = View.INVISIBLE
                appListScrollView.visibility = View.INVISIBLE
                appDoesntExistText.visibility = View.INVISIBLE
            }
        }
    }

    private fun hasApps(): Boolean {
        val sharedPreferences = getSharedPreferences("ReviverPrefs", Context.MODE_PRIVATE)
        val jsonString = sharedPreferences.getString("selectedApps", null)

        return if (jsonString.isNullOrEmpty()) {
            false
        } else {
            try {
                val jsonArray = JSONArray(jsonString)
                jsonArray.length() > 0
            } catch (e: Exception) {
                Log.e("HasApps", "Error parsing apps", e)
                false
            }
        }
    }

    private fun loadAndDisplaySelectedApps() {
        loadSelectedApps()
        appListContainer.removeAllViews()
        for (app in selectedApps) {
            addAppToMainLayout(app)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleEditAppIntent(intent)
    }

    private fun handleEditAppIntent(intent: Intent?) {
        val editAppPackage = intent?.getStringExtra("EDIT_APP_PACKAGE")
        if (!editAppPackage.isNullOrEmpty()) {
            val appToEdit = selectedApps.find { it.packageName == editAppPackage }
            if (appToEdit != null) {
                appListContainer.post {
                    for (i in 0 until appListContainer.childCount) {
                        val child = appListContainer.getChildAt(i)
                        if (child is ConstraintLayout && child.tag == appToEdit.packageName) {
                            editAppDetailsDialog(appToEdit, child)
                            break
                        }
                    }
                }
            }
        }
    }
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQ_CODE
                )
            }
        }
    }
    private fun setAppLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)

        baseContext.resources.updateConfiguration(
            config,
            baseContext.resources.displayMetrics
        )
    }

    private fun getLocalizedModeName(storedMode: String): String {
        return when {
            storedMode == "Mode 1 (Time Limit)" || storedMode == "Mod 1 (Limită de timp)" || storedMode == "Modus 1 (Zeitlimit)" || storedMode == "Modo 1 (Límite de tiempo)" || storedMode == "Mode 1 (Limite de temps)"->
                getString(R.string.mode1)
            storedMode == "Mode 2 (Launch Limit)" || storedMode == "Mod 2 (Limită deschideri)" || storedMode == "Modus 2 (Startlimit)"  || storedMode == "Modo 2 (Límite de aperturas)"  || storedMode == "Mode 2 (Limite d ouvertures)"  ->
                getString(R.string.mode2)
            storedMode == "Mode 3 (Password Protected)" || storedMode == "Mod 3 (Protejat cu parolă)" || storedMode == "Modus 3 (Passwortgeschützt)" || storedMode == "Modo 3 (Protegido por contraseña)" || storedMode == "Mode 3 (Protégé par mot de passe)" ->
                getString(R.string.mode3)
            storedMode == "Mode 4 (Constant Overlay)" || storedMode == "Mod 4 (Suprapunere constantă)" || storedMode == "Modus 4 (Konstantes Overlay)" || storedMode == "Modo 4 (Superposición constante)" || storedMode == "Mode 4 (Superposition constante)"->
                getString(R.string.mode4)

            else -> storedMode
        }
    }

    private fun getModeType(storedMode: String): Int {
        return when {
            storedMode.contains("Mode 1") || storedMode.contains("Mod 1") || storedMode.contains("Modus 1") || storedMode.contains("Modo 1") -> 1
            storedMode.contains("Mode 2") || storedMode.contains("Mod 2") || storedMode.contains("Modus 2") || storedMode.contains("Modo 2") -> 2
            storedMode.contains("Mode 3") || storedMode.contains("Mod 3") || storedMode.contains("Modus 3") || storedMode.contains("Modo 3") -> 3
            storedMode.contains("Mode 4") || storedMode.contains("Mod 4") || storedMode.contains("Modus 4") || storedMode.contains("Modo 4") -> 4
            else -> 1
        }
    }
}