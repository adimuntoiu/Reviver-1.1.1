package com.example.reviver

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.RelativeLayout
import kotlin.random.Random
import android.net.Uri
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.widget.EditText
import android.widget.Toast
import android.view.inputmethod.InputMethodManager
import android.util.Log

class Overlay(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    fun showOverlay(message: String, app: AppDetails? = null) {
        if (overlayView != null) {
            return  // Avoid showing multiple overlays
        }

        // Inflate the overlay layout
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)
        val rootLayout = overlayView!!.findViewById<RelativeLayout>(R.id.overlay_root)
        val passwordInput = overlayView!!.findViewById<EditText>(R.id.passwordInput)
        val submitButton = overlayView!!.findViewById<Button>(R.id.submitButton)
        val forgotPassword = overlayView!!.findViewById<Button>(R.id.forgotPassword)

        passwordInput.isEnabled = true
        passwordInput.isFocusable = true
        passwordInput.isFocusableInTouchMode = true
        // Initialize WindowManager
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val flags = if (app?.mode == "Mode 3 (Password Protected)") {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }

        // Set up layout parameters for the overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            flags,
            PixelFormat.TRANSLUCENT
        )

        // Set up the message in the TextView
        val messageTextView = overlayView!!.findViewById<TextView>(R.id.overlay_message)
        messageTextView.text = message

        // Set up the close button
        val closeButton = overlayView!!.findViewById<Button>(R.id.close_button)

        val layoutParams = closeButton.layoutParams as RelativeLayout.LayoutParams
        when (app?.mode) {
            "Mode 3 (Password Protected)" -> {
                passwordInput.visibility = View.VISIBLE
                submitButton.visibility = View.VISIBLE
                closeButton.visibility = View.GONE

                // Enable input
                passwordInput.isEnabled = true
                passwordInput.isFocusable = true

                submitButton.setOnClickListener {
                    val input = passwordInput.text.toString().trim()

                    if (input == app.password) {
                        removeOverlay()
                        val intent = Intent(context, MonitoringService::class.java).apply {
                            putExtra("RESET_PACKAGE", app?.packageName)
                        }
                        context.startService(intent)
                    } else {
                        Toast.makeText(context, "Wrong password!", Toast.LENGTH_SHORT).show()
                        passwordInput.text.clear()
                        passwordInput.requestFocus()
                    }
                }
                forgotPassword.setOnClickListener {
                    removeOverlay()
                    sendToAppDetails(app)
                }
                passwordInput.visibility = View.VISIBLE
                submitButton.visibility = View.VISIBLE
                forgotPassword.visibility = View.VISIBLE
                closeButton.visibility = View.GONE
            }

            "Mode 2 (Launch Limit)" -> {
                // Launch limit mode
                passwordInput.visibility = View.GONE
                submitButton.visibility = View.GONE
                closeButton.visibility = View.VISIBLE
                forgotPassword.visibility = View.GONE
                messageTextView.text = "$message\nCurrent opens: ${app.currentOpens}/${app.maxOpens}"
                setupRandomCloseButton(closeButton)
            }

            else -> {
                // Default mode (Time Limit)
                passwordInput.visibility = View.GONE
                submitButton.visibility = View.GONE
                forgotPassword.visibility = View.GONE
                closeButton.visibility = View.VISIBLE
                setupRandomCloseButton(closeButton)
            }
        }
        passwordInput.post {
            passwordInput.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(passwordInput, InputMethodManager.SHOW_IMPLICIT)
        }
        closeButton.layoutParams = layoutParams

        closeButton.setOnClickListener {
            removeOverlay()
        }


        // Add the overlay view to the window
        windowManager?.addView(overlayView, params)
    }

    private fun removeOverlay() {
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
    }
    private fun setupRandomCloseButton(button: Button) {
        val params = button.layoutParams as RelativeLayout.LayoutParams
        when (Random.nextInt(4)) {
            0 -> params.addRule(RelativeLayout.ALIGN_PARENT_START)
            1 -> params.addRule(RelativeLayout.ALIGN_PARENT_END)
            2 -> params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            3 -> params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        }
        button.layoutParams = params
        button.setOnClickListener { removeOverlay() }
    }
    private fun sendToAppDetails(app: AppDetails) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EDIT_APP_PACKAGE", app.packageName)
            }

            context.startActivity(intent)
            removeOverlay() // Optional: Remove overlay after launching
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open app settings", Toast.LENGTH_SHORT).show()
        }
    }
}
