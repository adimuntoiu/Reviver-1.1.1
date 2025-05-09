package com.example.reviver.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.reviver.R
import com.example.reviver.LogcatViewerActivity
import java.util.*

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

        val languageButton = view.findViewById<Button>(R.id.switchLanguageButton)
        updateLanguageButtonText(languageButton)

        languageButton.setOnClickListener {
            toggleLanguage()
            requireActivity().recreate()
        }
    }

    private fun toggleLanguage() {
        val sharedPrefs = requireContext().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val currentLang = sharedPrefs.getString("app_lang", "en") ?: "en"
        val newLang = if (currentLang == "en") "ro" else "en"

        sharedPrefs.edit().putString("app_lang", newLang).apply()
        setAppLocale(newLang)
    }

    private fun updateLanguageButtonText(button: Button) {
        val currentLang = requireContext()
            .getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            .getString("app_lang", "en") ?: "en"

        button.text = if (currentLang == "en") {
            getString(R.string.switch_language)
        } else {
            getString(R.string.switch_language_ro)
        }
    }

    private fun setAppLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)

        requireContext().resources.updateConfiguration(
            config,
            requireContext().resources.displayMetrics
        )
    }
}