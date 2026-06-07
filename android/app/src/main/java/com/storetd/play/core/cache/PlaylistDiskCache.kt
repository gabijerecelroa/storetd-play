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
     * STORETD v1.6.36:
     * Android TV se cierra al reconstruir TV en vivo desde cache local de disco.
     * Se desactiva el cache de disco para listas pesadas.
     * La velocidad queda apoyada en:
     * 1) cache de memoria mientras la app está abierta;
     * 2) cache rápido del backend Xtream.
     */
    fun load(context: Context, url: String): List<Channel> {
        return emptyList()
    }

    fun save(context: Context, url: String, channels: List<Channel>) {
        // No-op. No guardar listas pesadas en disco.
    }

    fun clear(context: Context, url: String) {
        clearAll(context)
    }

    fun clear(context: Context) {
        clearAll(context)
    }

    fun clearAll(context: Context) {
        runCatching {
            cacheDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}
