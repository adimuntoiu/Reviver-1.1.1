package com.example.reviver

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class TimeLimitExceededActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_limit_exceeded)

        // Display any relevant messages or UI elements to indicate time limit exceeded
    }

    // Disable the back button to prevent exiting this activity
    override fun onBackPressed() {
        // Do nothing to block back button
    }
}
