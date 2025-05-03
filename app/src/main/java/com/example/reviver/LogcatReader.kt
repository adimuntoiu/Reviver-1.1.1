package com.example.reviver

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class LogcatReader {

    fun getLogs(): List<String> {
        val logs = mutableListOf<String>()

        try {
            /// Verificam ultima comanda de logcat
            val process = Runtime.getRuntime().exec("logcat -d")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

            /// Adaugam fiecare log intr o lista
            var line: String? = bufferedReader.readLine()
            while (line != null) {
                logs.add(line)
                line = bufferedReader.readLine()
            }

            Runtime.getRuntime().exec("logcat -c")
        } catch (e: Exception) {
            Log.e("LogcatReader", "Failed to read logcat logs", e)
            logs.add("Error reading logcat logs: ${e.message}")
        }

        return logs
    }
}