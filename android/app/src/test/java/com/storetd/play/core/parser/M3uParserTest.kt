package com.storetd.play.core.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uParserTest {

    @Test
    fun parsesBasicM3uPlaylist() {
        val content = listOf(
            "#EXTM3U",
            "#EXTINF:-1 tvg-id=\"canal1\" tvg-logo=\"https://example.com/logo.png\" group-title=\"Noticias\",Canal 1",
            "https://example.com/canal1.m3u8"
        ).joinToString("\n")

        val channels = M3uParser().parse(content)

        assertEquals(1, channels.size)
        assertEquals("Canal 1", channels.first().name)
        assertEquals("Noticias", channels.first().group)
    }
}
