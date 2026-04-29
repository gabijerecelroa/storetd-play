package com.storetd.play.core.cache

import com.storetd.play.core.model.Channel

object PlaylistMemoryCache {
    private data class CacheEntry(
        val channels: List<Channel>,
        val cachedAtMillis: Long
    )

    private const val MAX_ENTRIES = 8

    private val cache = LinkedHashMap<String, CacheEntry>()

    fun get(url: String): List<Channel>? {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return null

        val entry = cache[cleanUrl] ?: return null
        if (entry.channels.isEmpty()) return null

        cache.remove(cleanUrl)
        cache[cleanUrl] = entry

        return entry.channels
    }

    fun save(url: String, channels: List<Channel>) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || channels.isEmpty()) return

        cache.remove(cleanUrl)
        cache[cleanUrl] = CacheEntry(
            channels = channels,
            cachedAtMillis = System.currentTimeMillis()
        )

        while (cache.size > MAX_ENTRIES) {
            val firstKey = cache.keys.firstOrNull() ?: break
            cache.remove(firstKey)
        }
    }

    fun clear(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isNotBlank()) {
            cache.remove(cleanUrl)
        }
    }

    fun clear() {
        cache.clear()
    }

    fun loadedAt(url: String): Long {
        return cache[url.trim()]?.cachedAtMillis ?: 0L
    }

    fun loadedAt(): Long {
        return cache.values.lastOrNull()?.cachedAtMillis ?: 0L
    }
}
