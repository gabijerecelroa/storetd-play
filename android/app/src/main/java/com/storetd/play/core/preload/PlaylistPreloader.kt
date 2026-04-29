package com.storetd.play.core.preload

import android.content.Context
import com.storetd.play.BuildConfig
import com.storetd.play.core.cache.PlaylistDiskCache
import com.storetd.play.core.cache.PlaylistMemoryCache
import com.storetd.play.core.model.Channel
import com.storetd.play.core.repository.IptvRepository
import com.storetd.play.core.storage.LocalAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object PlaylistPreloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = IptvRepository()
    private val runningJobs = ConcurrentHashMap<String, Deferred<List<Channel>>>()

    fun preloadAccount(context: Context) {
        val appContext = context.applicationContext
        val account = LocalAccount.getAccount(appContext)
        val urls = buildAccountUrls(
            activationCode = account.activationCode,
            fallbackUrl = account.playlistUrl
        )

        urls.forEach { url ->
            preload(appContext, url, forceRefresh = false)
        }
    }

    fun preload(
        context: Context,
        url: String,
        forceRefresh: Boolean = false
    ): Deferred<List<Channel>> {
        val appContext = context.applicationContext
        val cleanUrl = url.trim()

        if (cleanUrl.isBlank()) {
            return CompletableDeferred(emptyList())
        }

        if (!forceRefresh) {
            PlaylistMemoryCache.get(cleanUrl)?.let { cached ->
                if (cached.isNotEmpty()) {
                    return CompletableDeferred(cached)
                }
            }

            val diskCached = PlaylistDiskCache.load(appContext, cleanUrl)
            if (diskCached.isNotEmpty()) {
                PlaylistMemoryCache.save(cleanUrl, diskCached)
                return CompletableDeferred(diskCached)
            }
        }

        runningJobs[cleanUrl]?.let { return it }

        val job = scope.async {
            try {
                val channels = withTimeout(60000L) {
                    repository.loadPlaylistFromUrl(cleanUrl)
                }

                if (channels.isNotEmpty()) {
                    PlaylistMemoryCache.save(cleanUrl, channels)
                    PlaylistDiskCache.save(appContext, cleanUrl, channels)
                }

                channels
            } finally {
                runningJobs.remove(cleanUrl)
            }
        }

        val existing = runningJobs.putIfAbsent(cleanUrl, job)
        return existing ?: job
    }

    suspend fun awaitIfRunning(
        context: Context,
        url: String
    ): List<Channel>? {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return null

        runningJobs[cleanUrl]?.let { job ->
            return withTimeoutOrNull(65000L) {
                job.await()
            }?.takeIf { it.isNotEmpty() }
        }

        PlaylistMemoryCache.get(cleanUrl)?.let { cached ->
            if (cached.isNotEmpty()) return cached
        }

        val diskCached = PlaylistDiskCache.load(context.applicationContext, cleanUrl)
        if (diskCached.isNotEmpty()) {
            PlaylistMemoryCache.save(cleanUrl, diskCached)
            return diskCached
        }

        return null
    }

    fun clear(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return

        runningJobs.remove(cleanUrl)
        PlaylistMemoryCache.clear(cleanUrl)
    }

    private fun buildAccountUrls(
        activationCode: String,
        fallbackUrl: String
    ): List<String> {
        val code = activationCode.trim()

        if (code.isBlank()) {
            return listOfNotNull(fallbackUrl.trim().takeIf { it.isNotBlank() })
        }

        val baseUrl = BuildConfig.API_BASE_URL
            .trim()
            .trimEnd('/')

        val encodedCode = URLEncoder.encode(code, "UTF-8")

        return listOf(
            "$baseUrl/playlist/proxy?code=$encodedCode&type=live",
            "$baseUrl/playlist/proxy?code=$encodedCode&type=movies",
            "$baseUrl/playlist/proxy?code=$encodedCode&type=series"
        )
    }
}
