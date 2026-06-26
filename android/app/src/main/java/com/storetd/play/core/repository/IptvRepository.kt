package com.storetd.play.core.repository

import com.storetd.play.core.model.Channel
import com.storetd.play.core.parser.M3uParser
import okhttp3.OkHttpClient
import okhttp3.Request
import com.storetd.play.core.network.NetworkModule
import java.io.File
import java.util.concurrent.TimeUnit

class IptvRepository(
    private val parser: M3uParser = M3uParser(),
    private val client: OkHttpClient = NetworkModule.okHttpClient
) {
    // 🧠 MAGIA: VARIABLE INMORTAL
    // Esta variable guarda la lista ya procesada. Si está llena, no vuelve a leer nada.
    companion object {
        private var cacheDeCanales: List<Channel>? = null
    }

    fun loadPlaylistFromUrl(url: String): List<Channel> {
        // 1. Si ya tenemos los canales en memoria RAM, ¡los devolvemos al instante!
        cacheDeCanales?.let { 
            println("⚡ Devolviendo lista desde la RAM al instante.")
            return it 
        }

        val cacheDir = System.getProperty("java.io.tmpdir")
        val cacheFile = File(cacheDir, "lista_secreta_cache.m3u")

        return try {
            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Código HTTP: ${response.code}")
                }
                
                val body = response.body?.string() ?: throw IllegalStateException("Cuerpo vacío")
                
                try { cacheFile.writeText(body) } catch (e: Exception) { e.printStackTrace() }
                
                // 2. Procesamos, GUARDAMOS EN LA VARIABLE INMORTAL y devolvemos
                val canales = parser.parse(body)
                cacheDeCanales = canales
                canales
            }
        } catch (e: Exception) {
            if (cacheFile.exists()) {
                println("⚠️ Falló la red, leyendo disco duro...")
                val bodyLocal = cacheFile.readText()
                val canales = parser.parse(bodyLocal)
                cacheDeCanales = canales
                canales
            } else {
                throw e
            }
        }
    }
}
