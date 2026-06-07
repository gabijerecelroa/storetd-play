package com.storetd.play.core.cache

import com.storetd.play.core.model.Channel

object PlaylistMemoryCache {
    data class CacheEntry(
        val channels: List<Channel>,
        val cachedAtMillis: Long
    )

    private const val MAX_ENTRIES = 6
    private val cache = LinkedHashMap<String, CacheEntry>()

    fun get(url: String): List<Channel>? {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return null

        val entry = cache[cleanUrl] ?: return null

        // Refrescar orden LRU
        cache.remove(cleanUrl)
        cache[cleanUrl] = entry

        return entry.channels.takeIf { it.isNotEmpty() }
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
        cache.remove(url.trim())
    }

    fun clear() {
        clearAll()
    }

    fun clearAll() {
        cache.clear()
    }

    fun cachedAtMillis(url: String): Long {
        return cache[url.trim()]?.cachedAtMillis ?: 0L
    }

    fun latestCachedAtMillis(): Long {
        return cache.values.lastOrNull()?.cachedAtMillis ?: 0L
    }
}
