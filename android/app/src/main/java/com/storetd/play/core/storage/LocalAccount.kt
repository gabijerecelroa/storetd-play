package com.storetd.play.core.storage

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class LocalCustomerAccount(
    val isActivated: Boolean,
    val customerName: String,
    val activationCode: String,
    val status: String,
    val expiresAt: String,
    val deviceCode: String,
    val playlistUrl: String,
    val epgUrl: String,
    val maxDevices: Int,
    val deviceCount: Int
)

object LocalAccount {
    private const val PREFS = "storetd_play_account"
    private const val KEY_ACTIVATED = "activated"
    private const val KEY_CUSTOMER_NAME = "customer_name"
    private const val KEY_ACTIVATION_CODE = "activation_code"
    private const val KEY_STATUS = "status"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_PLAYLIST_URL = "playlist_url"
    private const val KEY_EPG_URL = "epg_url"
    private const val KEY_MAX_DEVICES = "max_devices"
    private const val KEY_DEVICE_COUNT = "device_count"
    private const val KEY_IS_DEMO = "is_demo"
    private const val KEY_DEMO_EXPIRES_AT_MS = "demo_expires_at_ms"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isActivated(context: Context): Boolean {
        val preferences = prefs(context)

        if (!preferences.getBoolean(KEY_ACTIVATED, false)) {
            return false
        }

        val isDemo = preferences.getBoolean(KEY_IS_DEMO, false)
        val demoExpiresAtMs = preferences.getLong(KEY_DEMO_EXPIRES_AT_MS, 0L)

        if (isDemo && demoExpiresAtMs > 0L && System.currentTimeMillis() > demoExpiresAtMs) {
            logout(context)
            return false
        }

        return true
    }

    fun getAccount(context: Context): LocalCustomerAccount {
        return LocalCustomerAccount(
            isActivated = isActivated(context),
            customerName = prefs(context).getString(KEY_CUSTOMER_NAME, "Cliente") ?: "Cliente",
            activationCode = prefs(context).getString(KEY_ACTIVATION_CODE, "-") ?: "-",
            status = prefs(context).getString(KEY_STATUS, "Activa") ?: "Activa",
            expiresAt = prefs(context).getString(KEY_EXPIRES_AT, defaultExpirationDate()) ?: defaultExpirationDate(),
            deviceCode = getDeviceCode(context),
            playlistUrl = prefs(context).getString(KEY_PLAYLIST_URL, "") ?: "",
            epgUrl = prefs(context).getString(KEY_EPG_URL, "") ?: "",
            maxDevices = prefs(context).getInt(KEY_MAX_DEVICES, 1),
            deviceCount = prefs(context).getInt(KEY_DEVICE_COUNT, 1)
        )
    }

    fun activate(
        context: Context,
        customerName: String,
        activationCode: String,
        status: String = "Activa",
        expiresAt: String = defaultExpirationDate(),
        playlistUrl: String = "",
        epgUrl: String = "",
        maxDevices: Int = 1,
        deviceCount: Int = 1
    ) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVATED, true)
            .putString(KEY_CUSTOMER_NAME, customerName.ifBlank { "Cliente" })
            .putString(KEY_ACTIVATION_CODE, activationCode)
            .putString(KEY_STATUS, status.ifBlank { "Activa" })
            .putString(KEY_EXPIRES_AT, expiresAt.ifBlank { defaultExpirationDate() })
            .putString(KEY_PLAYLIST_URL, playlistUrl)
            .putString(KEY_EPG_URL, epgUrl)
            .putInt(KEY_MAX_DEVICES, maxDevices)
            .putInt(KEY_DEVICE_COUNT, deviceCount)
            .apply()
    }

    fun activateDemo(
        context: Context,
        playlistUrl: String,
        epgUrl: String = ""
    ) {
        val expiresAtMs = System.currentTimeMillis() + (2L * 60L * 60L * 1000L)
        val expiresLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(expiresAtMs))

        prefs(context).edit()
            .putBoolean(KEY_ACTIVATED, true)
            .putBoolean(KEY_IS_DEMO, true)
            .putLong(KEY_DEMO_EXPIRES_AT_MS, expiresAtMs)
            .putString(KEY_CUSTOMER_NAME, "Cliente Demo")
            .putString(KEY_ACTIVATION_CODE, "253698")
            .putString(KEY_STATUS, "Demo 2 horas")
            .putString(KEY_EXPIRES_AT, expiresLabel)
            .putString(KEY_PLAYLIST_URL, playlistUrl)
            .putString(KEY_EPG_URL, epgUrl)
            .putInt(KEY_MAX_DEVICES, 1)
            .putInt(KEY_DEVICE_COUNT, 1)
            .apply()
    }

    fun logout(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun getDeviceCode(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown-device"

        val hash = MessageDigest.getInstance("SHA-256")
            .digest(androidId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        return hash.take(12).uppercase(Locale.US)
    }

    private fun defaultExpirationDate(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 30)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }
}
