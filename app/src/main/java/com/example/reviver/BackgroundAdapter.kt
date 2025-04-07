package com.example.reviver

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView

class BackgroundAdapter(
    context: Context,
    private val backgroundResIds: Array<Int>
) : ArrayAdapter<Int>(context, R.layout.background_item, backgroundResIds) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.background_item, parent, false)

        view.findViewById<ImageView>(R.id.backgroundPreview).setImageResource(backgroundResIds[position])
        return view
    }
}
