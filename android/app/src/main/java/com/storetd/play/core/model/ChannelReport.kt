package com.storetd.play.core.model

data class ChannelReport(
    val channelName: String,
    val channelGroup: String,
    val streamUrlMasked: String,
    val reportType: String,
    val technicalMessage: String?,
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String
)
