package com.storetd.play.core.network

import com.storetd.play.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

data class AccountStatusResult(
    val allowed: Boolean,
    val message: String
)

object AccountStatusApi {
    fun check(
        activationCode: String,
        deviceCode: String
    ): AccountStatusResult {
        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

        if (baseUrl.contains("api.example.com")) {
            return AccountStatusResult(
                allowed = true,
                message = "Backend no configurado."
            )
        }

        return runCatching {
            val url = URL("$baseUrl/auth/status")
            val connection = url.openConnection() as HttpURLConnection
            val body = """
                {
                  "activationCode": "${escapeJson(activationCode)}",
                  "deviceCode": "${escapeJson(deviceCode)}"
                }
            """.trimIndent()

            connection.requestMethod = "POST"
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            connection.disconnect()

            AccountStatusResult(
                allowed = extractBoolean(responseText, "allowed") ?: false,
                message = extractString(responseText, "message")
                    ?: "No se pudo validar la cuenta."
            )
        }.getOrElse {
            AccountStatusResult(
                allowed = true,
                message = "No se pudo conectar para validar. Se permite continuar temporalmente."
            )
        }
    }

    private fun extractBoolean(json: String, key: String): Boolean? {
        val regex = Regex(""""$key"\s*:\s*(true|false)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
    }

    private fun extractString(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.let { unescape(it) }
    }

    private fun unescape(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }
}
