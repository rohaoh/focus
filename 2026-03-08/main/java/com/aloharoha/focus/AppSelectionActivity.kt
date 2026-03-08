package com.aloharoha.focus

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView



class AppSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)

        val rv = findViewById<RecyclerView>(R.id.rv_app_list)
        val btnSave = findViewById<Button>(R.id.btn_save_apps)

        val prefs = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        val savedApps = prefs.getStringSet("allowed_packages", emptySet()) ?: emptySet()

        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { AppInfo(
                it.loadIcon(pm),
                it.loadLabel(pm).toString(),
                it.packageName,
                savedApps.contains(it.packageName)
            ) }
            .sortedBy { it.appName }

        val adapter = AppAdapter(apps)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        btnSave.setOnClickListener {
            val selected = apps.filter { it.isChecked }.map { it.packageName }.toSet()
            prefs.edit().putStringSet("allowed_packages", selected).apply()
            finish()
        }
    }

    inner class AppAdapter(val list: List<AppInfo>) : RecyclerView.Adapter<AppAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon = v.findViewById<ImageView>(R.id.img_app_icon)
            val appName = v.findViewById<TextView>(R.id.text_app_name)
            val check = v.findViewById<CheckBox>(R.id.check_app)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_app, p, false))
        override fun onBindViewHolder(h: VH, p: Int) {
            val item = list[p]
            h.icon.setImageDrawable(item.icon)
            h.appName.text = item.appName
            h.check.setOnCheckedChangeListener(null)
            h.check.isChecked = item.isChecked
            h.check.setOnCheckedChangeListener { _, isChecked -> item.isChecked = isChecked }
        }
        override fun getItemCount() = list.size
    }
}