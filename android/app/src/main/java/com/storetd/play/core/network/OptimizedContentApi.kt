package com.storetd.play.core.network

import com.storetd.play.BuildConfig
import com.storetd.play.core.model.Channel
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OptimizedContentApi {
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 45000

    fun loadAllSections(activationCode: String): Map<String, List<Channel>> {
        val code = activationCode.trim()

        if (code.isBlank()) return emptyMap()

        val result = linkedMapOf<String, List<Channel>>()

        for (section in listOf("live", "movies", "series")) {
            val items = runCatching {
                loadSection(
                    activationCode = code,
                    section = section
                )
            }.getOrDefault(emptyList())

            if (items.isNotEmpty()) {
                result[section] = items
            }
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
        val requestUrl = "$base/api/content/$safeSection?code=$encodedCode"

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
