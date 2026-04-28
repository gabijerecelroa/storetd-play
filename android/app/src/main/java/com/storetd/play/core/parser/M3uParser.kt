package com.storetd.play.core.parser

import com.storetd.play.core.model.Channel

class M3uParser {

    fun parse(content: String): List<Channel> {
        if (!content.lineSequence().firstOrNull().orEmpty().contains("#EXTM3U", ignoreCase = true)) {
            throw IllegalArgumentException("La lista no parece ser M3U/M3U8 valida.")
        }

        val channels = mutableListOf<Channel>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var tvgId: String? = null
        var tvgName: String? = null

        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        name = line.substringAfter(",", "").trim().ifBlank { null }
                        logo = extractAttribute(line, "tvg-logo")
                        group = extractAttribute(line, "group-title")
                        tvgId = extractAttribute(line, "tvg-id")
                        tvgName = extractAttribute(line, "tvg-name")
                    }

                    !line.startsWith("#") -> {
                        val finalName = name ?: tvgName ?: "Canal sin nombre"
                        channels.add(
                            Channel(
                                id = stableId(finalName, line),
                                name = finalName,
                                streamUrl = line,
                                logoUrl = logo,
                                group = group ?: "Sin categoria",
                                tvgId = tvgId
                            )
                        )

                        name = null
                        logo = null
                        group = null
                        tvgId = null
                        tvgName = null
                    }
                }
            }

        return channels.distinctBy { it.streamUrl }
    }

    private fun extractAttribute(line: String, key: String): String? {
        val quoted = Regex("$key=\\\"([^\\\"]*)\\\"").find(line)?.groupValues?.getOrNull(1)
        if (!quoted.isNullOrBlank()) return quoted

        val singleQuoted = Regex("$key='([^']*)'").find(line)?.groupValues?.getOrNull(1)
        return singleQuoted?.takeIf { it.isNotBlank() }
    }

    private fun stableId(name: String, url: String): String {
        return "${name.lowercase()}|$url".hashCode().toString()
    }
}
