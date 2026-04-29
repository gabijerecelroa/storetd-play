package com.storetd.play.core.cache

import android.content.Context
import com.storetd.play.core.epg.LocalEpgCache

object AppCacheManager {
    fun clearContentCache(context: Context) {
        PlaylistMemoryCache.clear()
        PlaylistDiskCache.clearAll(context)
    }

    fun clearEpgCache(context: Context) {
        EpgMemoryCache.clear()
        LocalEpgCache.clear(context)
    }

    fun clearAll(context: Context) {
        clearContentCache(context)
        clearEpgCache(context)
    }
}
