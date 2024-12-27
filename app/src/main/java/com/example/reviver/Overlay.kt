package com.example.reviver

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.RelativeLayout
import kotlin.random.Random

class Overlay(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    fun showOverlay(message: String) {
        if (overlayView != null) {
            return  // Avoid showing multiple overlays
        }

        // Inflate the overlay layout
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)

        // Initialize WindowManager
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Set up layout parameters for the overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        // Set up the message in the TextView
        val messageTextView = overlayView!!.findViewById<TextView>(R.id.overlay_message)
        messageTextView.text = message

        // Set up the close button
        val closeButton = overlayView!!.findViewById<Button>(R.id.close_button)

        // Randomly position the button within the screen corners
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        // Generate random values for the position in a corner
        val randomCorner = Random.nextInt(4)  // Random value between 0 and 3 for 4 corners
        val layoutParams = closeButton.layoutParams as RelativeLayout.LayoutParams

        when (randomCorner) {
            0 -> {  // Top-left corner
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_START)
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            }
            1 -> {  // Top-right corner
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_END)
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            }
            2 -> {  // Bottom-left corner
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_START)
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
            3 -> {  // Bottom-right corner
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_END)
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
        }

        closeButton.layoutParams = layoutParams

        closeButton.setOnClickListener {
            removeOverlay()
        }

        // Add the overlay view to the window
        windowManager?.addView(overlayView, params)
    }

    fun removeOverlay() {
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
    }
}
