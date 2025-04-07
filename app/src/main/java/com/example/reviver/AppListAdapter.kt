package com.example.reviver

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.ArrayAdapter

class AppListAdapter(
    context: Context,
    private val apps: List<AppInfo>
) : ArrayAdapter<AppInfo>(context, R.layout.app_list_item, R.id.appName, apps) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.app_list_item, parent, false)

        val app = apps[position]
        view.findViewById<ImageView>(R.id.appIcon).setImageDrawable(app.icon)
        view.findViewById<TextView>(R.id.appName).text = app.name

        return view
    }
}
