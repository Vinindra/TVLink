package com.tvlink.data.adb.model

sealed class InstallProgress {
    data class Installing(val percent: Int) : InstallProgress()
    object Success : InstallProgress()
    data class Failed(val reason: String) : InstallProgress()
}
