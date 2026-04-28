package com.storetd.play.core.network

import com.storetd.play.BuildConfig
import com.storetd.play.core.storage.LocalCustomerAccount
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ChannelReportPayload(
    val channelName: String,
    val category: String,
    val streamUrl: String,
    val problemType: String,
    val playerError: String,
    val androidVersion: String,
    val deviceModel: String,
    val account: LocalCustomerAccount
)

data class ChannelReportResult(
    val success: Boolean,
    val message: String
)

object ChannelReportApi {
    fun send(payload: ChannelReportPayload): ChannelReportResult {
        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

        if (baseUrl.contains("api.example.com")) {
            return ChannelReportResult(
                success = false,
                message = "Backend no configurado."
            )
        }

        return runCatching {
            val url = URL("$baseUrl/reports/channel")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            val body = """
                {
                  "channelName": "${escape(payload.channelName)}",
                  "category": "${escape(payload.category)}",
                  "streamUrl": "${escape(payload.streamUrl)}",
                  "problemType": "${escape(payload.problemType)}",
                  "playerError": "${escape(payload.playerError)}",
                  "androidVersion": "${escape(payload.androidVersion)}",
                  "deviceModel": "${escape(payload.deviceModel)}",
                  "customerName": "${escape(payload.account.customerName)}",
                  "activationCode": "${escape(payload.account.activationCode)}",
                  "deviceCode": "${escape(payload.account.deviceCode)}",
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

            ChannelReportResult(
                success = code in 200..299,
                message = extractString(responseText, "message")
                    ?: if (code in 200..299) "Reporte enviado." else "No se pudo enviar el reporte."
            )
        }.getOrElse { throwable ->
            ChannelReportResult(
                success = false,
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
}
