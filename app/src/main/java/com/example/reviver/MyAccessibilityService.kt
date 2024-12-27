package com.example.reviver  // Adjust this to match your package name

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {
    private var startTime: Long = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            val packageName = event.packageName?.toString()

            // Check if Instagram is in the foreground
            if (packageName == "com.instagram.android") {
                val currentTime = System.currentTimeMillis()

                // If this is the first time Instagram is detected, record the start time
                if (startTime == 0L) {
                    startTime = currentTime
                }

                // If 3 minutes have passed, trigger the closing action
                if ((currentTime - startTime) / 1000 >= 180) { // 180 seconds = 3 minutes
                    performGlobalAction(GLOBAL_ACTION_HOME)  // Close app by returning to home screen
                    startTime = 0L  // Reset the timer
                }
            } else {
                // Reset the timer if Instagram is not active
                startTime = 0L
            }
        }
    }


    override fun onInterrupt() {
        // Handle service interruptions here, if needed
    }
}
