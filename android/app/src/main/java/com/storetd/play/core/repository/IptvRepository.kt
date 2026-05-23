package com.storetd.play.core.repository

import com.storetd.play.core.model.Channel
import com.storetd.play.core.parser.M3uParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class IptvRepository(
    private val parser: M3uParser = M3uParser(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS) // Más tiempo para conectar
        .readTimeout(45, TimeUnit.SECONDS)    // Más tiempo para descargar listas pesadas
        .build()
) {
    fun loadPlaylistFromUrl(url: String): List<Channel> {
        // Obtenemos la carpeta oculta del celular donde no necesitamos pedir permisos
        val cacheDir = System.getProperty("java.io.tmpdir")
        val cacheFile = File(cacheDir, "lista_secreta_cache.m3u")

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "StoreTDPlay/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("No se pudo cargar la lista. Codigo HTTP: ${response.code}")
                }
                
                val body = response.body?.string() ?: throw IllegalStateException("Cuerpo vacío")
                
                // GUARDAMOS EL CACHÉ FÍSICO EN SILENCIO
                try {
                    cacheFile.writeText(body)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                parser.parse(body)
            }
        } catch (e: Exception) {
            // ¡LA MAGIA! Si falla el internet o tarda mucho, leemos del almacenamiento local
            if (cacheFile.exists()) {
                println("⚠️ Falló la red, cargando lista desde el Caché Silencioso a la velocidad de la luz...")
                val bodyLocal = cacheFile.readText()
                parser.parse(bodyLocal)
            } else {
                throw e // Si nunca se descargó nada antes, no queda otra que tirar el error
            }
        }
    }
}
