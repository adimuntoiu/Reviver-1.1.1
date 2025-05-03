package com.example.reviver.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.reviver.R
import android.widget.*
import android.content.Intent
import com.example.reviver.LogcatViewerActivity

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.viewLogsButton).setOnClickListener {
            startActivity(Intent(requireContext(), LogcatViewerActivity::class.java))
        }
    }
}