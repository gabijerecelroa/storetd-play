package com.storetd.play.core.network

import com.storetd.play.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

data class RemoteAppConfig(
    val appName: String = "StoreTD Play",
    val welcomeTitle: String = "Reproductor profesional para contenido autorizado",
    val welcomeMessage: String = "Carga tus listas M3U/M3U8 autorizadas, organiza canales, usa favoritos, historial y zapping.",
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "Estamos realizando mantenimiento. Intenta nuevamente mas tarde.",
    val providerMessage: String = "Uso permitido solo con contenido autorizado.",
    val supportWhatsApp: String = BuildConfig.SUPPORT_WHATSAPP,
    val supportEmail: String = BuildConfig.SUPPORT_EMAIL,
    val renewUrl: String = "",
    val termsUrl: String = "",
    val privacyUrl: String = "",
    val allowUserPlaylistInput: Boolean = true,
    val forceAppUpdate: Boolean = false,
    val minimumAppVersion: String = "1.0.0",
    val updatedAt: String = ""
)

object AppConfigApi {
    fun load(): RemoteAppConfig {
        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

        if (baseUrl.contains("api.example.com")) {
            return RemoteAppConfig()
        }

        return runCatching {
            val url = URL("$baseUrl/app/config")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/json")

            val code = connection.responseCode
            val responseText = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            connection.disconnect()

            if (code !in 200..299) {
                RemoteAppConfig()
            } else {
                RemoteAppConfig(
                    appName = extractString(responseText, "appName") ?: "StoreTD Play",
                    welcomeTitle = extractString(responseText, "welcomeTitle")
                        ?: "Reproductor profesional para contenido autorizado",
                    welcomeMessage = extractString(responseText, "welcomeMessage")
                        ?: "Carga tus listas M3U/M3U8 autorizadas, organiza canales, usa favoritos, historial y zapping.",
                    maintenanceMode = extractBoolean(responseText, "maintenanceMode") ?: false,
                    maintenanceMessage = extractString(responseText, "maintenanceMessage")
                        ?: "Estamos realizando mantenimiento. Intenta nuevamente mas tarde.",
                    providerMessage = extractString(responseText, "providerMessage")
                        ?: "Uso permitido solo con contenido autorizado.",
                    supportWhatsApp = extractString(responseText, "supportWhatsApp")
                        ?: BuildConfig.SUPPORT_WHATSAPP,
                    supportEmail = extractString(responseText, "supportEmail")
                        ?: BuildConfig.SUPPORT_EMAIL,
                    renewUrl = extractString(responseText, "renewUrl") ?: "",
                    termsUrl = extractString(responseText, "termsUrl") ?: "",
                    privacyUrl = extractString(responseText, "privacyUrl") ?: "",
                    allowUserPlaylistInput = extractBoolean(responseText, "allowUserPlaylistInput") ?: true,
                    forceAppUpdate = extractBoolean(responseText, "forceAppUpdate") ?: false,
                    minimumAppVersion = extractString(responseText, "minimumAppVersion") ?: "1.0.0",
                    updatedAt = extractString(responseText, "updatedAt") ?: ""
                )
            }
        }.getOrElse {
            RemoteAppConfig()
        }
    }

    private fun extractString(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.let { unescape(it) }
    }

    private fun extractBoolean(json: String, key: String): Boolean? {
        val regex = Regex(""""$key"\s*:\s*(true|false)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
    }

    private fun unescape(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
    }
}
