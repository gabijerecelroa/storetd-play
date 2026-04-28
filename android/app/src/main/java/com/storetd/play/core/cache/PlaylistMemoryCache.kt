package com.storetd.play.core.cache

import com.storetd.play.core.model.Channel

object PlaylistMemoryCache {
    private var cachedUrl: String = ""
    private var cachedChannels: List<Channel> = emptyList()
    private var cachedAtMillis: Long = 0L

    fun get(url: String): List<Channel>? {
        val cleanUrl = url.trim()

        if (cleanUrl.isBlank()) return null
        if (cachedUrl != cleanUrl) return null
        if (cachedChannels.isEmpty()) return null

        return cachedChannels
    }

    fun save(url: String, channels: List<Channel>) {
        cachedUrl = url.trim()
        cachedChannels = channels
        cachedAtMillis = System.currentTimeMillis()
    }

    fun clear() {
        cachedUrl = ""
        cachedChannels = emptyList()
        cachedAtMillis = 0L
    }

    fun loadedAt(): Long {
        return cachedAtMillis
    }
}
