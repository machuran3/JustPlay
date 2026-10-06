package com.example.minicine01

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

/**
 * Single Responsibility Principle (SRP):
 * Proveer información confiable y estructurada sobre la identidad del dispositivo móvil actual.
 */
object DeviceInfoProvider {

    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    fun getAndroidVersion(): String {
        return "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
    }

    fun getDeviceId(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_ID"
            val md = MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(androidId.toByteArray())
            hashBytes.take(6).joinToString("") { "%02x".format(it) }.uppercase()
        } catch (_: Exception) {
            "DEV_UNKNOWN"
        }
    }

    fun getFullDeviceSummary(context: Context): String {
        return "${getDeviceModel()} [ID: ${getDeviceId(context)}]"
    }
}
