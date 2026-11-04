package com.snapdown.app

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("snapdown_settings", Context.MODE_PRIVATE)

    var darkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", true)
        set(v) = prefs.edit().putBoolean("dark_theme", v).apply()

    var defaultQuality: String
        get() = prefs.getString("default_quality", "Best") ?: "Best"
        set(v) = prefs.edit().putString("default_quality", v).apply()

    var downloadFolder: String
        get() = prefs.getString("download_folder", "SnapDown") ?: "SnapDown"
        set(v) = prefs.edit().putString("download_folder", v).apply()
}
