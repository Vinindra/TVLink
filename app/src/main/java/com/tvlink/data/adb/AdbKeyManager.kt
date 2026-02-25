package com.tvlink.data.adb

import android.content.Context
import dadb.AdbKeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the ADB RSA key pair stored in the app's private files directory.
 * Dadb by default looks for the key at ~/.android/adbkey which doesn't exist
 * on Android — so we generate and store our own in filesDir.
 */
@Singleton
class AdbKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val privateKeyFile: File
        get() = File(context.filesDir, "adbkey")

    private val publicKeyFile: File
        get() = File(context.filesDir, "adbkey.pub")

    val keyPair: AdbKeyPair by lazy {
        ensureKeyExists()
        AdbKeyPair.read(privateKeyFile, publicKeyFile)
    }

    private fun ensureKeyExists() {
        if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
            AdbKeyPair.generate(privateKeyFile, publicKeyFile)
        }
    }
}
