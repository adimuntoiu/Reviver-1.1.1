package com.example.reviver.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.reviver.R
import kotlin.random.Random

class InfoFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tips = resources.getStringArray(R.array.tip_suggestions)
        val randomTip = tips[Random.nextInt(tips.size)]
        view.findViewById<TextView>(R.id.tipText).text = randomTip
    }
}
