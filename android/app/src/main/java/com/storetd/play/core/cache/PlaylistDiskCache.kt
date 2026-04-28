package com.storetd.play.core.cache

import android.content.Context
import com.storetd.play.core.model.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PlaylistDiskCache {
    private const val DIR_NAME = "playlist_cache"

    private fun cacheDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeKey(url: String): String {
        return url
            .trim()
            .hashCode()
            .toString()
            .replace("-", "n")
    }

    private fun dataFile(context: Context, url: String): File {
        return File(cacheDir(context), "${safeKey(url)}.json")
    }

    private fun metaFile(context: Context, url: String): File {
        return File(cacheDir(context), "${safeKey(url)}.meta")
    }

    fun save(context: Context, url: String, channels: List<Channel>) {
        runCatching {
            val array = JSONArray()

            channels.forEach { channel ->
                val item = JSONObject()
                    .put("name", channel.name)
                    .put("streamUrl", channel.streamUrl)
                    .put("group", channel.group)
                    .put("logoUrl", channel.logoUrl ?: "")
                    .put("tvgId", channel.tvgId ?: "")

                array.put(item)
            }

            dataFile(context, url).writeText(array.toString())
            metaFile(context, url).writeText(System.currentTimeMillis().toString())
        }
    }

    fun load(context: Context, url: String): List<Channel> {
        return runCatching {
            val file = dataFile(context, url)
            if (!file.exists()) return emptyList()

            val array = JSONArray(file.readText())
            val channels = mutableListOf<Channel>()

            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)

                channels.add(
                    Channel(
                        name = item.optString("name"),
                        streamUrl = item.optString("streamUrl"),
                        group = item.optString("group"),
                        logoUrl = item.optString("logoUrl").ifBlank { null },
                        tvgId = item.optString("tvgId").ifBlank { null }
                    )
                )
            }

            channels
        }.getOrDefault(emptyList())
    }

    fun lastUpdatedAt(context: Context, url: String): Long {
        return runCatching {
            val file = metaFile(context, url)
            if (!file.exists()) return 0L
            file.readText().toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    fun clear(context: Context, url: String) {
        runCatching { dataFile(context, url).delete() }
        runCatching { metaFile(context, url).delete() }
    }

    fun clearAll(context: Context) {
        runCatching {
            cacheDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}
