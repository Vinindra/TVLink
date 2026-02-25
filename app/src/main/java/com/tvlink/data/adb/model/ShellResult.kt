package com.tvlink.data.adb.model

data class ShellResult(
    val output: String,
    val exitCode: Int,
    val isError: Boolean = exitCode != 0
)
