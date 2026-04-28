package com.storetd.play.core.model

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val group: String = "Sin categoria",
    val tvgId: String? = null,
    val status: ChannelStatus = ChannelStatus.Unknown
)

enum class ChannelStatus {
    Unknown,
    Online,
    Slow,
    Offline
}
