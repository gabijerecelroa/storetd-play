package com.storetd.play.core.parser

object M3uValidator {
    fun validateUrl(url: String): Boolean {
        val normalized = url.trim()
        return normalized.startsWith("https://") || normalized.startsWith("http://")
    }
}
