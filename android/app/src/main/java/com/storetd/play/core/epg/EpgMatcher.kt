package com.storetd.play.core.epg

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

object EpgMatcher {
    fun loadPrograms(context: Context): List<EpgProgram> {
        return runCatching {
            val xml = LocalEpgCache.load(context)

            if (xml.isBlank()) {
                emptyList()
            } else {
                XmlTvParser.parse(xml)
            }
        }.getOrDefault(emptyList())
    }

    fun currentAndNext(
        context: Context,
        channelName: String
    ): Pair<EpgProgram?, EpgProgram?> {
        val programs = loadPrograms(context)

        return currentProgram(programs, channelName) to nextProgram(programs, channelName)
    }

    fun currentProgram(
        programs: List<EpgProgram>,
        channelName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): EpgProgram? {
        return programs
            .asSequence()
            .filter { it.isNow(nowMillis) }
            .mapNotNull { program ->
                val score = matchScore(channelName, program.channelName)
                if (score > 0) score to program else null
            }
            .sortedByDescending { it.first }
            .map { it.second }
            .firstOrNull()
    }

    fun nextProgram(
        programs: List<EpgProgram>,
        channelName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): EpgProgram? {
        return programs
            .asSequence()
            .filter { it.isUpcoming(nowMillis) }
            .mapNotNull { program ->
                val score = matchScore(channelName, program.channelName)
                if (score > 0) score to program else null
            }
            .sortedWith(
                compareByDescending<Pair<Int, EpgProgram>> { it.first }
                    .thenBy { it.second.startAtMillis }
            )
            .map { it.second }
            .firstOrNull()
    }

    private fun matchScore(appChannelName: String, epgChannelName: String): Int {
        val appNames = nameCandidates(appChannelName)
        val epgNames = nameCandidates(epgChannelName)

        var bestScore = 0

        for (app in appNames) {
            for (epg in epgNames) {
                if (app.isBlank() || epg.isBlank()) continue

                if (app == epg) {
                    bestScore = max(bestScore, 100)
                    continue
                }

                if (app.length >= 3 && epg.contains(app)) {
                    bestScore = max(bestScore, 85)
                }

                if (epg.length >= 3 && app.contains(epg)) {
                    bestScore = max(bestScore, 80)
                }

                val appTokens = app.split(" ").filter { it.length >= 2 }.toSet()
                val epgTokens = epg.split(" ").filter { it.length >= 2 }.toSet()

                if (appTokens.isNotEmpty() && epgTokens.isNotEmpty()) {
                    val common = appTokens.intersect(epgTokens).size
                    val required = max(1, minOf(appTokens.size, epgTokens.size))

                    if (common >= required) {
                        bestScore = max(bestScore, 70 + common)
                    }
                }
            }
        }

        return if (bestScore >= 70) bestScore else 0
    }

    private fun nameCandidates(value: String): List<String> {
        val base = normalizeName(value)

        val withoutSeparators = listOf(
            base,
            base.substringBefore("|").trim(),
            base.substringBefore("-").trim(),
            base.substringBefore(":").trim()
        )

        return withoutSeparators
            .map { removeNoiseWords(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun normalizeName(value: String): String {
        val noAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

        return noAccents
            .lowercase(Locale.getDefault())
            .replace("&", " y ")
            .replace("+", " plus ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun removeNoiseWords(value: String): String {
        return value
            .replace(
                Regex("\\b(hd|fhd|uhd|sd|ar|arg|argentina|latam|live|en vivo|canal)\\b"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
