package com.storetd.play.core.device

import android.content.Context
import android.provider.Settings
import java.util.UUID

object DeviceIdentity {
    private const val PREFS_NAME = "storetd_device_identity"
    private const val KEY_DEVICE_CODE = "device_code"

    fun getOrCreateDeviceCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_CODE, "").orEmpty()

        if (existing.isNotBlank()) {
            return existing
        }

        val androidId = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
        }.getOrNull().orEmpty()

        val base = if (
            androidId.isNotBlank() &&
            androidId.lowercase() != "9774d56d682e549c"
        ) {
            "android-$androidId"
        } else {
            "android-${UUID.randomUUID()}"
        }

        val clean = base
            .replace(Regex("[^A-Za-z0-9_-]"), "")
            .take(80)

        prefs.edit()
            .putString(KEY_DEVICE_CODE, clean)
            .apply()

        return clean
    }

    fun resetDeviceCode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DEVICE_CODE)
            .apply()
    }
}
