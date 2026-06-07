package com.storetd.play.core.cache

import android.content.Context
import com.storetd.play.core.model.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object PlaylistDiskCache {
    private const val DIR_NAME = "playlist_cache"
    private const val CACHE_VERSION = 2
    private const val MAX_CACHE_FILE_BYTES = 8L * 1024L * 1024L
    private const val TTL_MS = 12L * 60L * 60L * 1000L

    private fun cacheDir(context: Context): File {
        return File(context.applicationContext.cacheDir, DIR_NAME).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    private fun safeKey(url: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(url.trim().toByteArray(Charsets.UTF_8))

        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun cacheFile(context: Context, url: String): File {
        return File(cacheDir(context), "${safeKey(url)}.json")
    }

    fun load(context: Context, url: String): List<Channel> {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return emptyList()

        return runCatching {
            val file = cacheFile(context, cleanUrl)
            if (!file.exists()) return@runCatching emptyList<Channel>()
            if (file.length() <= 0L || file.length() > MAX_CACHE_FILE_BYTES) {
                file.delete()
                return@runCatching emptyList<Channel>()
            }

            val raw = file.readText(Charsets.UTF_8)
            val root = JSONObject(raw)

            if (root.optInt("version", 0) != CACHE_VERSION) {
                file.delete()
                return@runCatching emptyList<Channel>()
            }

            val cachedAt = root.optLong("cachedAt", 0L)
            if (cachedAt <= 0L || System.currentTimeMillis() - cachedAt > TTL_MS) {
                file.delete()
                return@runCatching emptyList<Channel>()
            }

            val array = root.optJSONArray("channels") ?: return@runCatching emptyList<Channel>()
            val result = ArrayList<Channel>(array.length())

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue

                val name = obj.optString("name", "").trim()
                val streamUrl = obj.optString("streamUrl", "").trim()

                if (name.isBlank() || streamUrl.isBlank()) continue

                result.add(
                    Channel(
                        name = name,
                        streamUrl = streamUrl,
                        logoUrl = obj.optString("logoUrl", "").takeIf { it.isNotBlank() && it != "null" },
                        group = obj.optString("group", "Sin categoría"),
                        tvgId = obj.optString("tvgId", "").takeIf { it.isNotBlank() && it != "null" },
                        type = obj.optString("type", "live")
                    )
                )
            }

            result
        }.getOrElse {
            runCatching { cacheFile(context, cleanUrl).delete() }
            emptyList()
        }
    }

    fun save(context: Context, url: String, channels: List<Channel>) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || channels.isEmpty()) return

        runCatching {
            val file = cacheFile(context, cleanUrl)
            val tmp = File(file.parentFile, "${file.name}.tmp")

            val array = JSONArray()

            channels.forEach { channel ->
                val obj = JSONObject()
                obj.put("name", channel.name)
                obj.put("streamUrl", channel.streamUrl)
                obj.put("logoUrl", channel.logoUrl ?: "")
                obj.put("group", channel.group)
                obj.put("tvgId", channel.tvgId ?: "")
                obj.put("type", channel.type)
                array.put(obj)
            }

            val root = JSONObject()
            root.put("version", CACHE_VERSION)
            root.put("cachedAt", System.currentTimeMillis())
            root.put("channels", array)

            tmp.writeText(root.toString(), Charsets.UTF_8)

            if (tmp.length() > MAX_CACHE_FILE_BYTES) {
                tmp.delete()
                return@runCatching
            }

            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    fun clear(context: Context, url: String) {
        runCatching {
            cacheFile(context, url.trim()).delete()
        }
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
