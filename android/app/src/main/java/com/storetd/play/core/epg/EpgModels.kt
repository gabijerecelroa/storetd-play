package com.storetd.play.core.epg

data class EpgChannel(
    val id: String,
    val name: String,
    val iconUrl: String = ""
)

data class EpgProgram(
    val channelId: String,
    val channelName: String,
    val title: String,
    val description: String,
    val startAtMillis: Long,
    val stopAtMillis: Long
) {
    fun isNow(nowMillis: Long): Boolean {
        return startAtMillis <= nowMillis && stopAtMillis >= nowMillis
    }

    fun isUpcoming(nowMillis: Long): Boolean {
        return startAtMillis > nowMillis
    }
}
