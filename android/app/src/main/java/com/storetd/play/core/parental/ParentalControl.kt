package com.storetd.play.core.parental

import android.content.Context
import java.security.MessageDigest

object ParentalControl {
    private const val PREFS_NAME = "storetd_parental_control"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_ADULT_HIDDEN = "adult_hidden"

    private const val DEFAULT_PIN = "1234"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAdultContentHidden(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ADULT_HIDDEN, true)
    }

    fun setAdultContentHidden(context: Context, hidden: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_ADULT_HIDDEN, hidden)
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val cleanPin = pin.trim()

        if (cleanPin.isBlank()) return false

        return hashPin(cleanPin) == getOrCreatePinHash(context)
    }

    fun changePin(
        context: Context,
        currentPin: String,
        newPin: String
    ): Boolean {
        val cleanNewPin = newPin.trim()

        if (cleanNewPin.length < 4) {
            return false
        }

        if (!verifyPin(context, currentPin)) {
            return false
        }

        prefs(context)
            .edit()
            .putString(KEY_PIN_HASH, hashPin(cleanNewPin))
            .apply()

        return true
    }

    fun resetToDefault(context: Context) {
        prefs(context)
            .edit()
            .putString(KEY_PIN_HASH, hashPin(DEFAULT_PIN))
            .putBoolean(KEY_ADULT_HIDDEN, true)
            .apply()
    }

    fun isUsingDefaultPin(context: Context): Boolean {
        return getOrCreatePinHash(context) == hashPin(DEFAULT_PIN)
    }

    private fun getOrCreatePinHash(context: Context): String {
        val preferences = prefs(context)
        val existing = preferences.getString(KEY_PIN_HASH, null)

        if (!existing.isNullOrBlank()) {
            return existing
        }

        val defaultHash = hashPin(DEFAULT_PIN)

        preferences
            .edit()
            .putString(KEY_PIN_HASH, defaultHash)
            .putBoolean(KEY_ADULT_HIDDEN, true)
            .apply()

        return defaultHash
    }

    private fun hashPin(value: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())

        return bytes.joinToString("") { "%02x".format(it) }
    }
}
