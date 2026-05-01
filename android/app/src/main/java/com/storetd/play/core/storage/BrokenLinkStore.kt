package com.storetd.play.core.storage

import android.content.Context
import java.security.MessageDigest

object BrokenLinkStore {
    private const val PREFS_NAME = "storetd_broken_links"
    private const val KEY_URLS = "urls"
    private const val KEY_GLOBAL_HASHES = "global_hashes"

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
        val local = prefs.getStringSet(KEY_URLS, emptySet()).orEmpty()

        if (local.contains(cleanUrl)) return true

        val globalHashes = prefs.getStringSet(KEY_GLOBAL_HASHES, emptySet()).orEmpty()
        return globalHashes.contains(hashStreamUrl(cleanUrl))
    }

    fun replaceGlobalHashes(context: Context, hashes: Collection<String>) {
        val cleanHashes = hashes
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.edit()
            .putStringSet(KEY_GLOBAL_HASHES, cleanHashes)
            .apply()
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

    private fun hashStreamUrl(streamUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(streamUrl.trim().toByteArray(Charsets.UTF_8))

        return digest.joinToString("") { byte ->
            "%02x".format(byte)
        }
    }
}
