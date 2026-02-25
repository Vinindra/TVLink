package com.tvlink.data.adb.model

data class InstalledApp(
    val packageName: String,
    val label: String = packageName,
    val isSystemApp: Boolean = false,
    val iconUrl: String? = null,
    val versionName: String? = null
)
