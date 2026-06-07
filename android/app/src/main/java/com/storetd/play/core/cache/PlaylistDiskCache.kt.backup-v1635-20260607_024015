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
     * STORETD v1.6.33:
     * Se desactiva la reutilización de listas pesadas desde disco.
     * El backend ya entrega contenido cacheado y rápido.
     */
    fun load(context: Context, url: String): List<Channel> {
        return emptyList()
    }

    fun save(context: Context, url: String, channels: List<Channel>) {
        // No-op.
    }

    fun clear(context: Context, url: String) {
        clearAll(context)
    }

    // Compatibilidad con AppCacheManager viejo.
    fun clear(context: Context) {
        clearAll(context)
    }

    fun clearAll(context: Context) {
        runCatching {
            cacheDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}
