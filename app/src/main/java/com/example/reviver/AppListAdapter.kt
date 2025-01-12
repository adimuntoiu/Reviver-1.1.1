package com.example.reviver

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val context: Context,
    private val appList: List<Pair<String, Drawable>>
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    var onAppSelected: ((Int) -> Unit)? = null // Callback for item clicks

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.app_item, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = appList[position]
        holder.appName.text = app.first
        holder.appIcon.setImageDrawable(app.second)

        holder.itemView.setOnClickListener {
            onAppSelected?.invoke(position)
        }
    }

    override fun getItemCount(): Int = appList.size

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)
    }
}
