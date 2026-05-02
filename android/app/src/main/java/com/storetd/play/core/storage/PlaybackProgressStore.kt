package com.storetd.play.core.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackProgress(
    val streamUrl: String,
    val title: String,
    val group: String,
    val logoUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
    val finished: Boolean
) {
    val percent: Int
        get() {
            if (durationMs <= 0L) return 0

            return ((positionMs.toDouble() / durationMs.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 100)
        }
}

object PlaybackProgressStore {
    private const val PREFS = "storetd_playback_progress"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 120

    fun get(context: Context, streamUrl: String): PlaybackProgress? {
        return all(context).firstOrNull { it.streamUrl == streamUrl }
    }

    fun save(
        context: Context,
        channel: SavedChannel,
        positionMs: Long,
        durationMs: Long
    ) {
        if (durationMs <= 0L) return
        if (positionMs <= 0L) return

        val safePosition = positionMs.coerceIn(0L, durationMs)
        val finished = safePosition >= (durationMs * 0.90).toLong()

        val item = PlaybackProgress(
            streamUrl = channel.streamUrl,
            title = channel.name,
            group = channel.group,
            logoUrl = channel.logoUrl,
            positionMs = safePosition,
            durationMs = durationMs,
            updatedAtMs = System.currentTimeMillis(),
            finished = finished
        )

        val next = all(context)
            .filterNot { it.streamUrl == channel.streamUrl }
            .toMutableList()

        next.add(0, item)

        write(context, next.take(MAX_ITEMS))
    }

    fun clear(context: Context, streamUrl: String) {
        write(context, all(context).filterNot { it.streamUrl == streamUrl })
    }

    fun cleanupMissingUrls(
        context: Context,
        validUrls: Set<String>
    ): Int {
        if (validUrls.isEmpty()) return 0

        val before = all(context)
        val next = before.filter { it.streamUrl in validUrls }

        write(context, next)

        return before.size - next.size
    }

    fun unfinished(context: Context): List<PlaybackProgress> {
        return all(context)
            .filter { !it.finished && it.positionMs > 15000L && it.durationMs > 0L }
    }

    fun all(context: Context): List<PlaybackProgress> {
        val raw = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]")
            ?: "[]"

        return runCatching {
            val array = JSONArray(raw)

            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)

                    add(
                        PlaybackProgress(
                            streamUrl = obj.optString("streamUrl"),
                            title = obj.optString("title"),
                            group = obj.optString("group"),
                            logoUrl = obj.optString("logoUrl").takeIf { it.isNotBlank() },
                            positionMs = obj.optLong("positionMs"),
                            durationMs = obj.optLong("durationMs"),
                            updatedAtMs = obj.optLong("updatedAtMs"),
                            finished = obj.optBoolean("finished")
                        )
                    )
                }
            }.filter { it.streamUrl.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun write(context: Context, items: List<PlaybackProgress>) {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("streamUrl", item.streamUrl)
                    .put("title", item.title)
                    .put("group", item.group)
                    .put("logoUrl", item.logoUrl ?: "")
                    .put("positionMs", item.positionMs)
                    .put("durationMs", item.durationMs)
                    .put("updatedAtMs", item.updatedAtMs)
                    .put("finished", item.finished)
            )
        }

        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }
}
