package com.storetd.play.core.storage

import android.content.Context

object LocalSettings {
    private const val PREFS = "storetd_play_settings"
    private const val KEY_HIDE_ADULT = "hide_adult_content"
    private const val KEY_PARENTAL_PIN = "parental_pin"
    private const val KEY_CONTENT_SYNC_STARTED_AT = "content_sync_started_at"
    private const val KEY_CONTENT_SYNC_SUCCESS_AT = "content_sync_success_at"
    private const val KEY_CONTENT_SYNC_MESSAGE = "content_sync_message"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAdultContentHidden(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_HIDE_ADULT, true)
    }

    fun setAdultContentHidden(context: Context, hidden: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_ADULT, hidden).apply()
    }

    fun getPin(context: Context): String {
        return prefs(context).getString(KEY_PARENTAL_PIN, "1234") ?: "1234"
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        return pin == getPin(context)
    }

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PARENTAL_PIN, pin).apply()
    }


    fun markContentSyncStarted(context: Context) {
        prefs(context)
            .edit()
            .putLong(KEY_CONTENT_SYNC_STARTED_AT, System.currentTimeMillis())
            .putString(KEY_CONTENT_SYNC_MESSAGE, "Sincronización iniciada.")
            .apply()
    }

    fun markContentSyncSuccess(
        context: Context,
        message: String = "Contenido sincronizado."
    ) {
        val now = System.currentTimeMillis()

        prefs(context)
            .edit()
            .putLong(KEY_CONTENT_SYNC_STARTED_AT, now)
            .putLong(KEY_CONTENT_SYNC_SUCCESS_AT, now)
            .putString(KEY_CONTENT_SYNC_MESSAGE, message)
            .apply()
    }

    fun markContentSyncFailed(
        context: Context,
        message: String = "Sincronización no confirmada."
    ) {
        prefs(context)
            .edit()
            .putLong(KEY_CONTENT_SYNC_STARTED_AT, System.currentTimeMillis())
            .putString(KEY_CONTENT_SYNC_MESSAGE, message)
            .apply()
    }

    fun getContentSyncSuccessAt(context: Context): Long {
        return prefs(context).getLong(KEY_CONTENT_SYNC_SUCCESS_AT, 0L)
    }

    fun getContentSyncMessage(context: Context): String {
        return prefs(context).getString(KEY_CONTENT_SYNC_MESSAGE, "") ?: ""
    }

    fun resetSettings(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
