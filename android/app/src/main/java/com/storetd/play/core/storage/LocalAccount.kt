package com.storetd.play.core.storage

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class LocalCustomerAccount(
    val isActivated: Boolean,
    val customerName: String,
    val activationCode: String,
    val status: String,
    val expiresAt: String,
    val deviceCode: String
)

object LocalAccount {
    private const val PREFS = "storetd_play_account"
    private const val KEY_ACTIVATED = "activated"
    private const val KEY_CUSTOMER_NAME = "customer_name"
    private const val KEY_ACTIVATION_CODE = "activation_code"
    private const val KEY_STATUS = "status"
    private const val KEY_EXPIRES_AT = "expires_at"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isActivated(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ACTIVATED, false)
    }

    fun getAccount(context: Context): LocalCustomerAccount {
        return LocalCustomerAccount(
            isActivated = isActivated(context),
            customerName = prefs(context).getString(KEY_CUSTOMER_NAME, "Cliente") ?: "Cliente",
            activationCode = prefs(context).getString(KEY_ACTIVATION_CODE, "-") ?: "-",
            status = prefs(context).getString(KEY_STATUS, "Activa") ?: "Activa",
            expiresAt = prefs(context).getString(KEY_EXPIRES_AT, defaultExpirationDate()) ?: defaultExpirationDate(),
            deviceCode = getDeviceCode(context)
        )
    }

    fun activate(
        context: Context,
        customerName: String,
        activationCode: String,
        status: String = "Activa",
        expiresAt: String = defaultExpirationDate()
    ) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVATED, true)
            .putString(KEY_CUSTOMER_NAME, customerName.ifBlank { "Cliente" })
            .putString(KEY_ACTIVATION_CODE, activationCode)
            .putString(KEY_STATUS, status.ifBlank { "Activa" })
            .putString(KEY_EXPIRES_AT, expiresAt.ifBlank { defaultExpirationDate() })
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
