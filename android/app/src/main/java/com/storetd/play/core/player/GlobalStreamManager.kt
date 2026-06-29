package com.storetd.play.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.storetd.play.feature.player.forceHlsUrl

@OptIn(UnstableApi::class)
object GlobalStreamManager {
    private var exoPlayer: ExoPlayer? = null
    var currentUrl: String? = null
        private set
    private var isCurrentlyMagma: Boolean? = null

    fun getPlayer(context: Context, url: String): ExoPlayer {
        val isMagma = url.contains("tv.m3uts.xyz") || url.contains("magma-lite") || url.contains("m3uts")
        
        if (exoPlayer != null && isCurrentlyMagma != isMagma) {
            exoPlayer?.release()
            exoPlayer = null
        }
        
        if (exoPlayer == null) {
            exoPlayer = createExoPlayer(context.applicationContext, isMagma)
            isCurrentlyMagma = isMagma
        }
        return exoPlayer!!
    }
    
    fun getExistingPlayer(): ExoPlayer? = exoPlayer

    suspend fun playUrl(context: Context, url: String) {
        val player = getPlayer(context, url)
        
        // If the URL is exactly the same and the player is not idle (e.g. buffering or playing)
        if (currentUrl == url && player.playbackState != ExoPlayer.STATE_IDLE && player.playbackState != ExoPlayer.STATE_ENDED) {
            player.playWhenReady = true
            return
        }

        currentUrl = url
        
        val safeUrl = withContext(Dispatchers.IO) {
            forceHlsUrl(context, url)
        }
        val isMagma = url.contains("tv.m3uts.xyz") || url.contains("magma-lite")
        val mimeType = if (isMagma || safeUrl.contains(".m3u8")) {
            MimeTypes.APPLICATION_M3U8
        } else null
        
        val mediaItemBuilder = MediaItem.Builder().setUri(safeUrl)
        if (mimeType != null) mediaItemBuilder.setMimeType(mimeType)
        
        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        player.playWhenReady = true
    }
    
    suspend fun restartCurrentUrl(context: Context) {
        val url = currentUrl ?: return
        val player = getPlayer(context, url)
        
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        player.seekTo(0)
        
        val safeUrl = withContext(Dispatchers.IO) {
            forceHlsUrl(context, url)
        }
        val isMagma = url.contains("tv.m3uts.xyz") || url.contains("magma-lite")
        val mimeType = if (isMagma || safeUrl.contains(".m3u8")) {
            MimeTypes.APPLICATION_M3U8
        } else null
        
        val mediaItemBuilder = MediaItem.Builder().setUri(safeUrl)
        if (mimeType != null) mediaItemBuilder.setMimeType(mimeType)
        
        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        player.playWhenReady = true
    }

    fun stopAndRelease() {
        exoPlayer?.release()
        exoPlayer = null
        currentUrl = null
        isCurrentlyMagma = null
    }
    
    fun pause() {
        exoPlayer?.pause()
    }
    
    fun resume() {
        exoPlayer?.play()
    }

    private fun createExoPlayer(context: Context, isMagmaChannel: Boolean): ExoPlayer {
        val trackSelector = DefaultTrackSelector(
            context,
            AdaptiveTrackSelection.Factory()
        ).apply {
            setParameters(buildUponParameters()
                .setTunnelingEnabled(false)
                .setViewportSizeToPhysicalDisplaySize(context, true)
                .setForceHighestSupportedBitrate(true)
            )
        }

        val dataSourceFactory = if (isMagmaChannel) {
            androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("Magma Player/10")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setDefaultRequestProperties(
                    mapOf(
                        "X-App" to "di",
                        "X-Version" to "10/1.0.9",
                        "X-Did" to "c0041021c5c95679",
                        "X-Hash" to "MVRUcQA5ddQ6Q7uvtD3Ms8ucj_Sj0SSzBNyfWBAeDrWPiwDugKt5m7OlmmsvMbJ4Gqc7qoaTbR47HgkHQ0kyHjk2Q20f5TMexj3o9gNRhmprUJmWXWpDQYyx-xAOEx1MV9R0m9Q-GYH2CqzKS_rIlpb0hge4Moy7FRomMTQpPK047WahnRTpbycnW517aYWIdb20KEZy9RVbHoVZ4gIwY19ZxfLB-QRXubBGyTPFkxLfrZh2cnh-AsdaNbkQKuBqbu0F1Ya-VaQb4tb1C2O3Er14lNrP-R9MnXbltt_yahHYND94F90kqLRacnURZP76e6r6d9xTzl3940FLneH-UpYdPxnuNc9C7S-cwYrs2DMHdNE5WWZ3s-FuesB9Mz25tZd0rIRGGb7dnjZY_FjAx08R3hzsywOLpGWUBT_4OCH051l21jTc29hXwiwj-1vo3eRbjUkgzXJlwvTBS2RAAld5NzPs6kFVjxSr729niVrf9j4WtOJnVQfAESCIFbNnDudLB4VdBeb2w58rTAGo-Q"
                    )
                )
        } else {
            androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.9 LibVLC/3.0.9")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
        }

        val loadErrorPolicy = DefaultLoadErrorHandlingPolicy(3)

        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(loadErrorPolicy)

        val smartRenderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        val normalLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000,
                120000,
                1500,
                2000
            )
            .setBackBuffer(10000, true)
            .setTargetBufferBytes(-1)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context, smartRenderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(normalLoadControl)
            .setTrackSelector(trackSelector)
            .build()
    }
}

