package com.storetd.play.core.cache

import android.content.Context
import com.storetd.play.core.epg.EpgProgram
import com.storetd.play.core.epg.LocalEpgCache
import com.storetd.play.core.epg.XmlTvParser

object EpgMemoryCache {
    private var programs: List<EpgProgram> = emptyList()
    private var loadedAt: Long = 0L

    fun getPrograms(context: Context): List<EpgProgram> {
        if (programs.isNotEmpty()) {
            return programs
        }

        val xml = LocalEpgCache.load(context)

        programs = if (xml.isBlank()) {
            emptyList()
        } else {
            XmlTvParser.parse(xml)
        }

        loadedAt = System.currentTimeMillis()

        return programs
    }

    fun clear() {
        programs = emptyList()
        loadedAt = 0L
    }

    fun loadedAt(): Long {
        return loadedAt
    }
}
