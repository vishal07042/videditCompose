package com.videdit.android.data.command

import android.content.Context
import java.io.File
import java.io.FileNotFoundException

class BinaryInstaller(private val context: Context) {

    fun installIfBundled(assetPath: String, destination: File, overwrite: Boolean = false): Boolean {
        return try {
            destination.parentFile?.mkdirs()
            if (destination.exists() && !overwrite) {
                return true
            }
            if (destination.exists()) {
                destination.delete()
            }

            context.assets.open(assetPath).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }

            destination.setExecutable(true, false)
            true
        } catch (_: FileNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isBundled(assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).use { }
            true
        } catch (_: Exception) {
            false
        }
    }
}
