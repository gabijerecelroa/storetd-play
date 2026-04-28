package com.storetd.play.core.player

import com.storetd.play.core.storage.SavedChannel

object PlayerSession {
    private var queue: List<SavedChannel> = emptyList()
    private var currentIndex: Int = 0

    fun setQueue(channels: List<SavedChannel>, currentStreamUrl: String) {
        queue = channels.distinctBy { it.streamUrl }

        val index = queue.indexOfFirst { it.streamUrl == currentStreamUrl }
        currentIndex = if (index >= 0) index else 0
    }

    fun current(): SavedChannel? {
        return queue.getOrNull(currentIndex)
    }

    fun hasPrevious(): Boolean {
        return queue.isNotEmpty() && currentIndex > 0
    }

    fun hasNext(): Boolean {
        return queue.isNotEmpty() && currentIndex < queue.lastIndex
    }

    fun previous(): SavedChannel? {
        if (!hasPrevious()) return null
        currentIndex -= 1
        return current()
    }

    fun next(): SavedChannel? {
        if (!hasNext()) return null
        currentIndex += 1
        return current()
    }

    fun isEmpty(): Boolean {
        return queue.isEmpty()
    }
}
