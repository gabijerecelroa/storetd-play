package com.storetd.play.core.repository

import com.storetd.play.core.model.Channel
import com.storetd.play.core.parser.M3uParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class IptvRepository(
    private val parser: M3uParser = M3uParser(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    fun loadPlaylistFromUrl(url: String): List<Channel> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "StoreTDPlay/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("No se pudo cargar la lista. Codigo HTTP: ${response.code}")
            }
            val body = response.body.string()
            return parser.parse(body)
        }
    }
}
