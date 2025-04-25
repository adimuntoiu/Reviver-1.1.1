package com.example.reviver

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object AppPreferences {
    private const val PREFS_NAME = "ReviverPrefs"
    private const val APPS_KEY = "selectedApps"

    /**
     * Saves the list of apps to SharedPreferences.
     */
    fun saveSelectedApps(context: Context, apps: List<AppDetails>) {
        try {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonArray = JSONArray().apply {
                apps.forEach { app ->
                    // You can also validate here if needed.
                    put(JSONObject().apply {
                        put("packageName", app.packageName)
                        put("name", app.appName)
                        put("timeLimit", app.timeLimit)
                        put("mode", app.mode)
                        put("maxOpens", app.maxOpens)
                        put("currentOpens", app.currentOpens)
                        put("password", app.password ?: "")
                    })
                }
            }
            val success = sharedPrefs.edit().putString(APPS_KEY, jsonArray.toString()).commit()
            if (success) {
                Log.d("AppPreferences", "Saved apps successfully: ${sharedPrefs.getString(APPS_KEY, "")}")
            } else {
                Log.e("AppPreferences", "Failed to save apps")
            }
        } catch (e: Exception) {
            Log.e("AppPreferences", "Error saving apps", e)
        }
    }

    /**
     * Loads the list of apps from SharedPreferences.
     */
    fun loadSelectedApps(context: Context): List<AppDetails> {
        val apps = mutableListOf<AppDetails>()
        try {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = sharedPrefs.getString(APPS_KEY, null)
            json?.let {
                val jsonArray = JSONArray(it)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    apps.add(
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
            Log.d("AppPreferences", "Loaded ${apps.size} apps")
        } catch (e: Exception) {
            Log.e("AppPreferences", "Error loading apps", e)
        }
        return apps
    }
}
