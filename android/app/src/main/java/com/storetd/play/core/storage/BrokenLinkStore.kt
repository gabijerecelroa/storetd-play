package com.storetd.play.core.storage

import android.content.Context

object BrokenLinkStore {
    private const val PREFS_NAME = "storetd_broken_links"
    private const val KEY_URLS = "urls"

    fun markReported(context: Context, streamUrl: String) {
        val cleanUrl = streamUrl.trim()
        if (cleanUrl.isBlank()) return

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_URLS, emptySet()).orEmpty().toMutableSet()

        current.add(cleanUrl)

        prefs.edit()
            .putStringSet(KEY_URLS, current)
            .apply()
    }

    fun isReported(context: Context, streamUrl: String): Boolean {
        val cleanUrl = streamUrl.trim()
        if (cleanUrl.isBlank()) return false

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_URLS, emptySet()).orEmpty().contains(cleanUrl)
    }

    fun clear(context: Context, streamUrl: String) {
        val cleanUrl = streamUrl.trim()
        if (cleanUrl.isBlank()) return

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_URLS, emptySet()).orEmpty().toMutableSet()

        if (current.remove(cleanUrl)) {
            prefs.edit()
                .putStringSet(KEY_URLS, current)
                .apply()
        }
    }
}
