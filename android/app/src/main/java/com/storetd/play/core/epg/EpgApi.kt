package com.storetd.play.core.epg

import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object EpgApi {
    fun downloadXml(epgUrl: String): String {
        val connection = URL(epgUrl).openConnection() as HttpURLConnection

        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 25000
        connection.setRequestProperty("Accept", "application/xml,text/xml,*/*")
        connection.setRequestProperty("User-Agent", "StoreTD-Play-EPG")

        val code = connection.responseCode

        val text = if (code in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }

        connection.disconnect()

        if (code !in 200..299) {
            throw IllegalStateException("No se pudo descargar EPG. HTTP $code")
        }

        if (!text.contains("<tv", ignoreCase = true)) {
            throw IllegalStateException("El archivo EPG no parece XMLTV valido.")
        }

        return text
    }
}

object XmlTvParser {
    fun parse(xml: String): List<EpgProgram> {
        val channels = parseChannels(xml)
        val now = System.currentTimeMillis()
        val maxFuture = now + 1000L * 60L * 60L * 24L

        val programmes = mutableListOf<EpgProgram>()

        val programmeRegex = Regex(
            """<programme\s+([^>]*)>(.*?)</programme>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        for (match in programmeRegex.findAll(xml)) {
            val attrs = parseAttributes(match.groupValues[1])
            val body = match.groupValues[2]

            val channelId = attrs["channel"].orEmpty()
            val startAt = parseXmlTvTime(attrs["start"].orEmpty())
            val parsedStopAt = parseXmlTvTime(attrs["stop"].orEmpty())
            val stopAt = if (parsedStopAt > 0) parsedStopAt else startAt + 30L * 60L * 1000L

            if (channelId.isBlank() || startAt <= 0) continue
            if (stopAt < now) continue
            if (startAt > maxFuture) continue

            val title = tagText(body, "title").ifBlank { "Programa sin titulo" }
            val description = tagText(body, "desc")
            val channelName = channels[channelId]?.name ?: channelId

            programmes.add(
                EpgProgram(
                    channelId = channelId,
                    channelName = channelName,
                    title = title,
                    description = description,
                    startAtMillis = startAt,
                    stopAtMillis = stopAt
                )
            )

            if (programmes.size >= 2500) break
        }

        return programmes.sortedWith(
            compareBy<EpgProgram> { it.startAtMillis }.thenBy { it.channelName }
        )
    }

    private fun parseChannels(xml: String): Map<String, EpgChannel> {
        val result = mutableMapOf<String, EpgChannel>()

        val channelRegex = Regex(
            """<channel\s+([^>]*)>(.*?)</channel>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val iconRegex = Regex(
            """<icon\s+([^>]*)/?>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        for (match in channelRegex.findAll(xml)) {
            val attrs = parseAttributes(match.groupValues[1])
            val body = match.groupValues[2]
            val id = attrs["id"].orEmpty()

            if (id.isBlank()) continue

            val displayName = tagText(body, "display-name").ifBlank { id }
            val iconAttrs = iconRegex.find(body)?.groupValues?.getOrNull(1)?.let { parseAttributes(it) }
            val iconUrl = iconAttrs?.get("src").orEmpty()

            result[id] = EpgChannel(
                id = id,
                name = displayName,
                iconUrl = iconUrl
            )
        }

        return result
    }

    private fun parseAttributes(value: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        val attrRegex = Regex(
            """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*"([^"]*)"""",
            RegexOption.IGNORE_CASE
        )

        for (match in attrRegex.findAll(value)) {
            result[match.groupValues[1]] = decodeXml(match.groupValues[2])
        }

        return result
    }

    private fun tagText(body: String, tag: String): String {
        val regex = Regex(
            """<$tag(?:\s+[^>]*)?>(.*?)</$tag>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        return regex.find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("<[^>]+>"), "")
            ?.let { decodeXml(it) }
            ?.trim()
            .orEmpty()
    }

    private fun parseXmlTvTime(value: String): Long {
        val clean = value.trim().replace(Regex("""\s+"""), " ")

        if (clean.isBlank()) return 0L

        val patterns = listOf(
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmm Z",
            "yyyyMMddHHmmss",
            "yyyyMMddHHmm"
        )

        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)

                if (!pattern.contains("Z")) {
                    format.timeZone = TimeZone.getDefault()
                }

                val date = format.parse(clean)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }

        return 0L
    }

    private fun decodeXml(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#039;", "'")
    }
}
