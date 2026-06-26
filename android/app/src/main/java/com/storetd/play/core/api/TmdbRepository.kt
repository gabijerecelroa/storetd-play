package com.storetd.play.core.api

import com.storetd.play.core.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import com.storetd.play.core.network.NetworkModule

data class TmdbResult(
    val title: String, val overview: String, val posterPath: String?,
    val backdropPath: String?, val voteAverage: Double, val releaseYear: String
)

class TmdbRepository {
    private val client = NetworkModule.okHttpClient

    suspend fun searchContent(name: String, isSeries: Boolean): TmdbResult? = withContext(Dispatchers.IO) {
        val apiKey = Secrets.TMDB_API_KEY
        if (apiKey == "REPLACE_ME_IN_ACTIONS" || apiKey.isBlank()) return@withContext null
        
        try {
            val yearRegex = "\\((\\d{4})\\)".toRegex()
            val match = yearRegex.find(name)
            val year = match?.groupValues?.get(1) ?: ""
            var cleanName = name.replace(yearRegex, "").trim()
            
            // FILTRO PRTV: Borrar prefijos numericos de las listas (ej: "09 Sniper" -> "Sniper")
            val prefixRegex = Regex("^\\d+\\s*[\\.\\-\\|]?\\s*")
            cleanName = cleanName.replace(prefixRegex, "").trim()

            // STORETD_TMDB_CLEAN_START
            cleanName = cleanName
                .replace("_", " ")
                .replace(".", " ")
                .replace(Regex("(?i)^\\s*(cine|pel[ií]culas?|movies?|vod|series?|tv|latino|estrenos?)\\s*[|:/-]\\s*"), "")
                .replace(Regex("(?i)^\\s*(ar|arg|mx|co|pe|py|us|usa|br|cl|ec|uy)\\s*[|:/-]\\s*"), "")
                .replace(Regex("(?i)\\b(FHD|HD|SD|UHD|4K|1080P|720P|CAM|TS|LATINO|LAT|CASTELLANO|SUBTITULADO|SUB|DUAL AUDIO|DUAL|A COLOR|BLANCO Y NEGRO|OP\\s*\\d+|OPC\\s*\\d+)\\b"), " ")
                .replace(Regex("(?i)\\b(ONLINE|VIP|PREMIUM|FULL|NUEVO|NEW)\\b"), " ")
                .replace(Regex("(?i)\\([^)]*\\b(D|C|LAT|LATINO|CASTELLANO|SUB|DUAL|CAM|TS|HD|FHD|SD|UHD|4K|1080P|720P|OP\\s*\\d*)\\b[^)]*\\)"), " ")
                .replace(Regex("[^A-Za-z0-9ÁÉÍÓÚÜÑáéíóúüñ:,'’\\- ]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim(' ', '-', '|', ':', ',')
            // STORETD_TMDB_CLEAN_END
            if (isSeries) {
                // Filtro mágico: Borra S01E01, 1x01, Temporada 1, etc solo para buscar en TMDB
                val epRegex = Regex("(?i)(\\s*[-_]?\\s*(S\\d+\\s*E\\d+|T\\d+\\s*E\\d+|\\d+x\\d+|Temporada\\s*\\d+|Capitulo\\s*\\d+|Capítulo\\s*\\d+|Episodio\\s*\\d+|Cap\\s*\\d+|Ep\\s*\\d+|T\\s*\\d+|S\\s*\\d+).*)")
                cleanName = cleanName.replace(epRegex, "").trim()
            }

            val type = if (isSeries) "tv" else "movie"
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
