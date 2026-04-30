package com.storetd.play.core.network

import com.storetd.play.BuildConfig
import com.storetd.play.core.model.Channel
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OptimizedContentApi {
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 15000

    fun refreshContent(activationCode: String): Boolean {
        val code = activationCode.trim()
        val base = BuildConfig.API_BASE_URL
            .trim()
            .trimEnd('/')

        if (code.isBlank() || base.isBlank()) return false

        val encodedCode = URLEncoder.encode(code, "UTF-8")
        val requestUrl = "$base/api/content/refresh-app?code=$encodedCode&async=1"

        val raw = postUrl(requestUrl)
        val json = JSONObject(raw)

        return json.optBoolean("success", false)
    }

    fun loadAllSections(activationCode: String): Map<String, List<Channel>> {
        val code = activationCode.trim()

        if (code.isBlank()) return emptyMap()

        val result = linkedMapOf<String, List<Channel>>()

        val liveItems = runCatching {
            loadSection(
                activationCode = code,
                section = "live"
            )
        }.getOrDefault(emptyList())

        if (liveItems.isNotEmpty()) {
            result["live"] = liveItems
        }

        val movieItems = runCatching {
            loadSection(
                activationCode = code,
                section = "movies"
            )
        }.getOrDefault(emptyList())

        if (movieItems.isNotEmpty()) {
            result["movies"] = movieItems
        }

        // Series usa primero el cache optimizado de carpetas del backend.
        // Si falla, cae al endpoint plano /series como respaldo.
        val seriesItems = runCatching {
            loadSeriesFoldersAsChannels(code)
        }.getOrDefault(emptyList()).ifEmpty {
            runCatching {
                loadSection(
                    activationCode = code,
                    section = "series"
                )
            }.getOrDefault(emptyList())
        }

        if (seriesItems.isNotEmpty()) {
            result["series"] = seriesItems
        }

        return result
    }

    fun loadSection(
        activationCode: String,
        section: String
    ): List<Channel> {
        val base = BuildConfig.API_BASE_URL
            .trim()
            .trimEnd('/')

        if (base.isBlank()) return emptyList()

        val safeSection = section.trim().lowercase()

        if (safeSection !in setOf("live", "movies", "series")) {
            return emptyList()
        }

        val encodedCode = URLEncoder.encode(activationCode.trim(), "UTF-8")
        val requestUrl = "$base/api/content/$safeSection?code=$encodedCode&autoRefresh=0"

        val raw = readUrl(requestUrl)
        val json = JSONObject(raw)

        if (!json.optBoolean("success", true)) {
            return emptyList()
        }

        val array = json.optJSONArray("items") ?: return emptyList()
        val items = mutableListOf<Channel>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val channel = parseChannel(obj)

            if (channel.streamUrl.isNotBlank()) {
                items.add(channel)
            }
        }

        return items
    }


    private fun postUrl(requestUrl: String): String {
        val connection = URL(requestUrl).openConnection() as HttpURLConnection

        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "StoreTD-Play-Android")

        return try {
            connection.outputStream.use { it.write(ByteArray(0)) }

            val code = connection.responseCode

            if (code !in 200..299) {
                val errorText = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                throw IllegalStateException("HTTP $code $errorText")
            }

            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun loadSeriesFoldersAsChannels(activationCode: String): List<Channel> {
        val base = BuildConfig.API_BASE_URL
            .trim()
            .trimEnd('/')

        val code = activationCode.trim()

        if (base.isBlank() || code.isBlank()) return emptyList()

        val encodedCode = URLEncoder.encode(code, "UTF-8")
        val requestUrl = "$base/api/content/series-folders?code=$encodedCode&autoRefresh=0"

        val raw = readUrl(requestUrl)
        val json = JSONObject(raw)

        if (!json.optBoolean("success", true)) {
            return emptyList()
        }

        val folders = json.optJSONArray("folders") ?: return emptyList()
        val channels = mutableListOf<Channel>()

        for (folderIndex in 0 until folders.length()) {
            val folder = folders.optJSONObject(folderIndex) ?: continue

            val folderTitle = readString(folder, "title", "name").ifBlank {
                "Serie sin título"
            }

            val folderPoster = readNullableString(folder, "posterUrl", "poster_url", "logoUrl", "logo")
            val episodes = folder.optJSONArray("episodes") ?: continue

            for (episodeIndex in 0 until episodes.length()) {
                val episode = episodes.optJSONObject(episodeIndex) ?: continue
                val channel = parseSeriesEpisode(
                    obj = episode,
                    folderTitle = folderTitle,
                    folderPoster = folderPoster
                )

                if (channel.streamUrl.isNotBlank()) {
                    channels.add(channel)
                }
            }
        }

        return channels
    }

    private fun parseSeriesEpisode(
        obj: JSONObject,
        folderTitle: String,
        folderPoster: String?
    ): Channel {
        val name = readString(obj, "name", "title").ifBlank { folderTitle }
        val streamUrl = readString(obj, "streamUrl", "stream_url", "url")
        val id = readString(obj, "id").ifBlank {
            "$folderTitle|$name|$streamUrl".hashCode().toString()
        }

        return Channel(
            id = id,
            name = name,
            streamUrl = streamUrl,
            logoUrl = readNullableString(obj, "logoUrl", "logo_url", "logo") ?: folderPoster,
            group = folderTitle,
            tvgId = readNullableString(obj, "tvgId", "tvg_id")
        )
    }

    private fun readUrl(requestUrl: String): String {
        val connection = URL(requestUrl).openConnection() as HttpURLConnection

        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "StoreTD-Play-Android")

        return try {
            val code = connection.responseCode

            if (code !in 200..299) {
                val errorText = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                throw IllegalStateException("HTTP $code $errorText")
            }

            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseChannel(obj: JSONObject): Channel {
        val name = readString(obj, "name", "title").ifBlank { "Sin nombre" }
        val streamUrl = readString(obj, "streamUrl", "stream_url", "url")
        val group = readString(obj, "group", "category").ifBlank { "Sin categoría" }
        val id = readString(obj, "id").ifBlank {
            "$name|$streamUrl".hashCode().toString()
        }

        return Channel(
            id = id,
            name = name,
            streamUrl = streamUrl,
            logoUrl = readNullableString(obj, "logoUrl", "logo_url", "logo"),
            group = group,
            tvgId = readNullableString(obj, "tvgId", "tvg_id")
        )
    }

    private fun readString(obj: JSONObject, vararg names: String): String {
        for (name in names) {
            val value = obj.optString(name, "").trim()

            if (value.isNotBlank() && value != "null") {
                return value
            }
        }

        return ""
    }

    private fun readNullableString(obj: JSONObject, vararg names: String): String? {
        val value = readString(obj, *names)
        return value.ifBlank { null }
    }
}
