package com.example.reviver.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.reviver.LogcatViewerActivity
import com.example.reviver.R
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

        val languageSpinner = view.findViewById<Spinner>(R.id.languageSpinner)
        setupLanguageSpinner(languageSpinner)
    }

    private fun setupLanguageSpinner(spinner: Spinner) {
        val context = requireContext()
        val langCodes = listOf("en", "ro", "de", "es", "fr")

        val adapter = ArrayAdapter.createFromResource(
            context,
            R.array.languages_array,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val sharedPrefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val currentLang = sharedPrefs.getString("app_lang", "en") ?: "en"
        val currentIndex = langCodes.indexOf(currentLang).takeIf { it >= 0 } ?: 0
        spinner.setSelection(currentIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val newLang = langCodes[position]
                if (newLang != currentLang) {
                    setAppLanguage(newLang)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setAppLanguage(languageCode: String) {
        val sharedPrefs = requireContext().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putString("app_lang", languageCode)
            putString("last_fragment", "SettingsFragment")
            apply()
        }

        updateLocale(requireContext(), languageCode)

        requireActivity().recreate()
    }

    private fun updateLocale(context: Context, langCode: String) {
        val locale = Locale(langCode)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        requireActivity().applicationContext.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
