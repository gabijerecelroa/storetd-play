package com.storetd.play.core.epg

import android.content.Context
import java.io.File

object LocalEpgCache {
    private const val FILE_NAME = "storetd_epg_cache.xml"
    private const val PREFS = "storetd_epg_cache_meta"

    fun rememberUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("url", url)
            .apply()
    }

    fun save(context: Context, url: String, xml: String) {
        File(context.filesDir, FILE_NAME).writeText(xml)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("url", url)
            .putLong("updatedAt", System.currentTimeMillis())
            .apply()
    }

    fun load(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)

        if (!file.exists()) return ""

        return runCatching { file.readText() }.getOrDefault("")
    }

    fun lastUrl(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("url", "")
            .orEmpty()
    }

    fun lastUpdatedAt(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("updatedAt", 0L)
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("updatedAt")
            .apply()
    }
}
