package com.storetd.play.core.cache

import com.storetd.play.core.model.Channel

object PlaylistMemoryCache {
    /*
     * STORETD v1.6.33:
     * Evita retener listas grandes de TV en vivo en memoria.
     */
    fun get(url: String): List<Channel>? = null

    fun save(url: String, channels: List<Channel>) {
        // No-op.
    }

    fun clear(url: String) {
        // No-op.
    }

    // Compatibilidad con llamadas viejas.
    fun clear() {
        clearAll()
    }

    fun clearAll() {
        // No-op.
    }

    fun cachedAtMillis(url: String): Long = 0L

    fun latestCachedAtMillis(): Long = 0L
}
