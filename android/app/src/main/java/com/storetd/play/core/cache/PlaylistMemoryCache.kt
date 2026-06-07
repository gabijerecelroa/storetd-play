package com.storetd.play.core.cache

import com.storetd.play.core.model.Channel

object PlaylistMemoryCache {
    /*
     * STORETD V1.6.33:
     * Evita retener listas grandes en memoria entre aperturas de TV en vivo.
     * La app consulta al backend, y el backend entrega cache rápido.
     */
    fun get(url: String): List<Channel>? = null

    fun save(url: String, channels: List<Channel>) {
        // No-op.
    }

    fun clear(url: String) {
        // No-op.
    }

    fun clearAll() {
        // No-op.
    }

    fun cachedAtMillis(url: String): Long = 0L

    fun latestCachedAtMillis(): Long = 0L
}
