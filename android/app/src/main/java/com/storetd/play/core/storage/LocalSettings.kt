package com.storetd.play.core.storage

import android.content.Context

object LocalSettings {
    private const val PREFS = "storetd_play_settings"
    private const val KEY_HIDE_ADULT = "hide_adult_content"
    private const val KEY_PARENTAL_PIN = "parental_pin"

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

    fun resetSettings(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
