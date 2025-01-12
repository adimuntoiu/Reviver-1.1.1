package com.example.reviver

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class LogcatReader {

    fun getLogs(): List<String> {
        val logs = mutableListOf<String>()

        try {
            // Run the logcat command to capture logs
            val process = Runtime.getRuntime().exec("logcat -d")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

            // Read each line and add to the logs list
            var line: String? = bufferedReader.readLine()
            while (line != null) {
                logs.add(line)
                line = bufferedReader.readLine()
            }

            // Clear the Logcat buffer
            Runtime.getRuntime().exec("logcat -c")
        } catch (e: Exception) {
            Log.e("LogcatReader", "Failed to read logcat logs", e)
            logs.add("Error reading logcat logs: ${e.message}")
        }

        return logs
    }
}