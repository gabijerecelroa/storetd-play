package com.storetd.play.core.cache

import android.content.Context
import com.storetd.play.core.model.Channel
import java.io.File

object PlaylistDiskCache {
    private const val DIR_NAME = "playlist_cache"

    private fun cacheDir(context: Context): File {
        return File(context.applicationContext.cacheDir, DIR_NAME)
    }

    /*
     * STORETD V1.6.33:
     * En TV Android, el cache local de listas grandes puede provocar cierres
     * al reabrir TV en vivo. El backend ya responde cacheado y rápido.
     * Por eso la app ya no reutiliza listas pesadas desde disco.
     */
    fun load(context: Context, url: String): List<Channel> {
        return emptyList()
    }

    fun save(context: Context, url: String, channels: List<Channel>) {
        // No-op: el cache real queda del lado backend.
    }

    fun clear(context: Context, url: String) {
        runCatching {
            cacheDir(context).listFiles()?.forEach { file ->
                if (file.name.contains(url.hashCode().toString())) file.delete()
            }
        }
    }

    fun clearAll(context: Context) {
        runCatching {
            cacheDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}
