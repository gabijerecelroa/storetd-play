package com.storetd.play.core.network

import com.storetd.play.BuildConfig
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

object BrokenLinksApi {
    fun loadHashes(activationCode: String): List<String> {
        val code = activationCode.trim()
        if (code.isBlank()) return emptyList()

        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
        if (baseUrl.contains("api.example.com")) return emptyList()

        return runCatching {
            val encodedCode = URLEncoder.encode(code, "UTF-8")
            val url = URL("$baseUrl/api/broken-links?code=$encodedCode")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/json")

            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            connection.disconnect()

            parseHashes(responseText)
        }.getOrElse {
            emptyList()
        }
    }

    private fun parseHashes(json: String): List<String> {
        val hashesBlock = Regex(""""hashes"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

        if (hashesBlock.isBlank()) return emptyList()

        return Regex(""""([a-fA-F0-9]{64})"""")
            .findAll(hashesBlock)
            .map { it.groupValues[1].lowercase() }
            .toList()
    }
}
