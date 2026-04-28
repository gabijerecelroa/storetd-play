package com.storetd.play.core.storage

import android.content.Context
import android.util.Base64
import com.storetd.play.core.model.Channel

data class SavedChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val group: String,
    val tvgId: String?
) {
    companion object {
        fun from(channel: Channel): SavedChannel {
            return SavedChannel(
                id = channel.id,
                name = channel.name,
                streamUrl = channel.streamUrl,
                logoUrl = channel.logoUrl,
                group = channel.group,
                tvgId = channel.tvgId
            )
        }
    }
}

object LocalLibrary {
    private const val PREFS = "storetd_play_library"
    private const val FAVORITES = "favorites"
    private const val HISTORY = "history"
    private const val MAX_HISTORY = 50

    fun addFavorite(context: Context, channel: Channel) {
        addFavorite(context, SavedChannel.from(channel))
    }

    fun addFavorite(context: Context, channel: SavedChannel) {
        val updated = listOf(channel) + favorites(context).filterNot { it.streamUrl == channel.streamUrl }
        writeList(context, FAVORITES, updated)
    }

    fun removeFavorite(context: Context, streamUrl: String) {
        writeList(context, FAVORITES, favorites(context).filterNot { it.streamUrl == streamUrl })
    }

    fun isFavorite(context: Context, streamUrl: String): Boolean {
        return favorites(context).any { it.streamUrl == streamUrl }
    }

    fun favorites(context: Context): List<SavedChannel> {
        return readList(context, FAVORITES)
    }

    fun addHistory(context: Context, channel: Channel) {
        addHistory(context, SavedChannel.from(channel))
    }

    fun addHistory(context: Context, channel: SavedChannel) {
        val updated = (listOf(channel) + history(context).filterNot { it.streamUrl == channel.streamUrl })
            .take(MAX_HISTORY)

        writeList(context, HISTORY, updated)
    }

    fun history(context: Context): List<SavedChannel> {
        return readList(context, HISTORY)
    }

    fun clearHistory(context: Context) {
        writeList(context, HISTORY, emptyList())
    }

    fun maskUrl(url: String): String {
        if (url.length <= 24) return "***"
        return url.take(12) + "..." + url.takeLast(8)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readList(context: Context, key: String): List<SavedChannel> {
        return prefs(context)
            .getStringSet(key, emptySet())
            .orEmpty()
            .mapNotNull { deserialize(it) }
            .sortedBy { it.name.lowercase() }
    }

    private fun writeList(context: Context, key: String, channels: List<SavedChannel>) {
        prefs(context)
            .edit()
            .putStringSet(key, channels.map { serialize(it) }.toSet())
            .apply()
    }

    private fun serialize(channel: SavedChannel): String {
        return listOf(
            channel.id,
            channel.name,
            channel.streamUrl,
            channel.logoUrl.orEmpty(),
            channel.group,
            channel.tvgId.orEmpty()
        ).joinToString("|") { encode(it) }
    }

    private fun deserialize(value: String): SavedChannel? {
        val parts = value.split("|")
        if (parts.size < 6) return null

        return runCatching {
            SavedChannel(
                id = decode(parts[0]),
                name = decode(parts[1]),
                streamUrl = decode(parts[2]),
                logoUrl = decode(parts[3]).ifBlank { null },
                group = decode(parts[4]),
                tvgId = decode(parts[5]).ifBlank { null }
            )
        }.getOrNull()
    }

    private fun encode(value: String): String {
        return Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun decode(value: String): String {
        return String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
    }
}
