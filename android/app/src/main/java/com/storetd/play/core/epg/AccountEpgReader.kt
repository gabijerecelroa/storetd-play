package com.storetd.play.core.epg

import android.content.Context

object AccountEpgReader {
    private val possiblePrefs = listOf(
        "storetd_play_account",
        "storetd_account",
        "account",
        "StoreTDPlayAccount"
    )

    private val possibleKeys = listOf(
        "epgUrl",
        "epg_url",
        "EPG_URL",
        "assignedEpgUrl",
        "customerEpgUrl"
    )

    fun epgUrl(context: Context): String {
        for (prefsName in possiblePrefs) {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

            for (key in possibleKeys) {
                val value = prefs.getString(key, "").orEmpty().trim()

                if (value.startsWith("http://") || value.startsWith("https://")) {
                    return value
                }
            }
        }

        val cached = LocalEpgCache.lastUrl(context)

        if (cached.startsWith("http://") || cached.startsWith("https://")) {
            return cached
        }

        return ""
    }
}
