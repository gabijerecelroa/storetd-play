package com.storetd.play.core.network

import com.storetd.play.BuildConfig
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ActivationResult(
    val success: Boolean,
    val customerName: String?,
    val activationCode: String?,
    val status: String?,
    val expiresAt: String?,
    val playlistUrl: String?,
    val epgUrl: String?,
    val maxDevices: Int?,
    val deviceCount: Int?,
    val message: String
)

object ActivationApi {

    fun activate(
        customerName: String,
        activationCode: String,
        deviceCode: String
    ): ActivationResult {
        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

        if (baseUrl.contains("api.example.com")) {
            return ActivationResult(
                success = false,
                customerName = null,
                activationCode = null,
                status = null,
                expiresAt = null,
                playlistUrl = null,
                epgUrl = null,
                maxDevices = null,
                deviceCount = null,
                message = "Backend no configurado. Usa modo demo o configura API_BASE_URL."
            )
        }

        return runCatching {
            val url = URL("$baseUrl/auth/activate")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            val body = """
                {
                  "customerName": "${escape(customerName)}",
                  "activationCode": "${escape(activationCode)}",
                  "deviceCode": "${escape(deviceCode)}",
                  "appVersion": "${escape(BuildConfig.VERSION_NAME)}"
                }
            """.trimIndent()

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val code = connection.responseCode
            val responseText = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            connection.disconnect()

            if (code !in 200..299) {
                ActivationResult(
                    success = false,
                    customerName = null,
                    activationCode = null,
                    status = null,
                    expiresAt = null,
                    playlistUrl = null,
                    epgUrl = null,
                    maxDevices = null,
                    deviceCount = null,
                    message = extractString(responseText, "message") ?: "Activacion rechazada por el servidor."
                )
            } else {
                ActivationResult(
                    success = extractBoolean(responseText, "success") ?: true,
                    customerName = extractString(responseText, "customerName") ?: customerName,
                    activationCode = extractString(responseText, "activationCode") ?: activationCode,
                    status = extractString(responseText, "status") ?: "Activa",
                    expiresAt = extractString(responseText, "expiresAt"),
                    playlistUrl = extractString(responseText, "playlistUrl"),
                    epgUrl = extractString(responseText, "epgUrl"),
                    maxDevices = extractInt(responseText, "maxDevices"),
                    deviceCount = extractInt(responseText, "deviceCount"),
                    message = extractString(responseText, "message") ?: "Dispositivo activado correctamente."
                )
            }
        }.getOrElse { throwable ->
            ActivationResult(
                success = false,
                customerName = null,
                activationCode = null,
                status = null,
                expiresAt = null,
                playlistUrl = null,
                epgUrl = null,
                maxDevices = null,
                deviceCount = null,
                message = throwable.message ?: "No se pudo conectar con el backend."
            )
        }
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    private fun extractString(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    private fun extractBoolean(json: String, key: String): Boolean? {
        val regex = Regex(""""$key"\s*:\s*(true|false)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
    }

    private fun extractInt(json: String, key: String): Int? {
        val regex = Regex(""""$key"\s*:\s*(\d+)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
