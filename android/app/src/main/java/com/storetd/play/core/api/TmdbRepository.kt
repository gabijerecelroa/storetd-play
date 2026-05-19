package com.storetd.play.core.api

import com.storetd.play.core.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class TmdbResult(
    val title: String, val overview: String, val posterPath: String?,
    val backdropPath: String?, val voteAverage: Double, val releaseYear: String
)

class TmdbRepository {
    private val client = OkHttpClient()

    suspend fun searchContent(name: String, isSeries: Boolean): TmdbResult? = withContext(Dispatchers.IO) {
        val apiKey = Secrets.TMDB_API_KEY
        if (apiKey == "REPLACE_ME_IN_ACTIONS" || apiKey.isBlank()) return@withContext null
        
        try {
            val yearRegex = "\\((\\d{4})\\)".toRegex()
            val match = yearRegex.find(name)
            val year = match?.groupValues?.get(1) ?: ""
            val cleanName = name.replace(yearRegex, "").trim()

            val type = if (isSeries) "tv" else "multi"
            var url = "https://api.themoviedb.org/3/search/$type?api_key=$apiKey&language=es-ES&query=${URLEncoder.encode(cleanName, "UTF-8")}"
            if (year.isNotEmpty()) {
                url += if (isSeries) "&first_air_date_year=$year" else "&primary_release_year=$year"
            }

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val results = json.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val first = results.getJSONObject(0)
                        return@withContext TmdbResult(
                            title = first.optString(if (isSeries) "name" else "title", cleanName),
                            overview = first.optString("overview", "Sin descripción disponible en español."),
                            posterPath = first.optString("poster_path").takeIf { it.isNotEmpty() && it != "null" }?.let { "https://image.tmdb.org/t/p/w500$it" },
                            backdropPath = first.optString("backdrop_path").takeIf { it.isNotEmpty() && it != "null" }?.let { "https://image.tmdb.org/t/p/w1280$it" },
                            voteAverage = first.optDouble("vote_average", 0.0),
                            releaseYear = year.ifEmpty { first.optString(if (isSeries) "first_air_date" else "release_date", "").take(4) }
                        )
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }
}
