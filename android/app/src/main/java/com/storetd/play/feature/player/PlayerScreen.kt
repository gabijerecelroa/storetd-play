package com.storetd.play.feature.player

import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.storetd.play.core.epg.EpgMatcher
import com.storetd.play.core.epg.EpgProgram
import com.storetd.play.core.network.ChannelReportApi
import com.storetd.play.core.network.ChannelReportPayload
import com.storetd.play.core.player.PlayerSession
import com.storetd.play.core.storage.BrokenLinkStore
import com.storetd.play.core.storage.LocalAccount
import com.storetd.play.core.storage.LocalLibrary
import com.storetd.play.core.storage.PlaybackProgressStore
import com.storetd.play.core.storage.SavedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory

@OptIn(UnstableApi::class)
private enum class VideoResizeMode(
    val label: String,
    val media3Mode: Int
) {
    Fit("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Zoom("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Fill("Llenar", AspectRatioFrameLayout.RESIZE_MODE_FILL);

    fun next(): VideoResizeMode {
        return when (this) {
            Fit -> Zoom
            Zoom -> Fill
            Fill -> Fit
        }
    }
}

private const val STREAM_UNAVAILABLE_TIMEOUT_MS = 60_000L

private fun isMagmaLiveStreamUrl(url: String): Boolean {
    val clean = url.lowercase(Locale.getDefault())

    val isMovieOrSeries =
        clean.contains("/xtream-lite/movie/") ||
            clean.contains("/magma-lite/movie/") ||
            clean.contains("/xtream-lite/series/") ||
            clean.contains("/magma-lite/series/") ||
            clean.contains("/movie/") ||
            clean.contains("/series/")

    return !isMovieOrSeries && (
        clean.contains("/xtream-lite/live/") ||
            clean.contains("/magma-lite/live/") ||
            clean.contains("/live/") ||
            clean.contains("tvcluboficial.com") ||
            clean.contains("m3uts.xyz")
        )
}

private suspend fun obtenerUrlSeguraMagma(context: android.content.Context, streamId: String): String? {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val postUrl = "http://tv.m3uts.xyz/stream/gen/$streamId"
        try {
            val connection = (java.net.URL(postUrl).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 15; moto g84 5G)")
                setRequestProperty("Accept", "*/*")
                connectTimeout = 15000 // Timeouts extendidos para IPTV
                readTimeout = 15000
            }

            val postData = "id=$streamId&cast=false&device=c0041021c5c95679&code="
            val bytes = postData.toByteArray(Charsets.UTF_8)
            connection.setRequestProperty("Content-Length", bytes.size.toString())
            connection.outputStream.use { it.write(bytes) }

            val responseCode = connection.responseCode
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val secureUrl = connection.inputStream.bufferedReader().use { it.readText().trim() }
                
                if (secureUrl.startsWith("http://") || secureUrl.startsWith("https://")) {
                    return@withContext secureUrl
                } else {
                    reportMagmaError(context, streamId, "URL Segura Inválida: $secureUrl", postUrl)
                }
            } else {
                reportMagmaError(context, streamId, "HTTP $responseCode", postUrl)
            }
        } catch (e: Exception) {
            reportMagmaError(context, streamId, "Excepción pre-flight: ${e.message}", postUrl)
        }
        null
    }
}

private suspend fun reportMagmaError(
    context: android.content.Context,
    streamId: String,
    errorMessage: String,
    attemptedUrl: String
) {
    try {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val activationCode = LocalAccount.getAccount(context).activationCode
            val timestamp = System.currentTimeMillis()
            
            val url = java.net.URL("http://82.39.109.213:5000/api/debug/magma-error")
            val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "StoreTD-Play-Android")
                connectTimeout = 5000
                readTimeout = 5000
            }
            
            val jsonPayload = """
                {
                    "activationCode": "${activationCode.replace("\"", "\\\"")}",
                    "streamId": "${streamId.replace("\"", "\\\"")}",
                    "errorMessage": "${errorMessage.replace("\"", "\\\"")}",
                    "attemptedUrl": "${attemptedUrl.replace("\"", "\\\"")}",
                    "timestamp": $timestamp
                }
            """.trimIndent()
            
            val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
            connection.setRequestProperty("Content-Length", bytes.size.toString())
            connection.outputStream.use { it.write(bytes) }
            
            connection.responseCode
            connection.disconnect()
        }
    } catch (e: Exception) {
        // Ignored to prevent crashes
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channelName: String,
    streamUrl: String,
    groupName: String,
    logoUrl: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playerRootView = LocalView.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()
    val playerFocusRequester = remember { FocusRequester() }

    var currentChannel by remember {
        mutableStateOf(
            PlayerSession.current() ?: SavedChannel(
                id = "${channelName.lowercase()}|$streamUrl".hashCode().toString(),
                name = channelName,
                streamUrl = streamUrl,
                logoUrl = logoUrl,
                group = groupName,
                tvgId = null
            )
        )
    }

    var videoResizeMode by remember { mutableStateOf(VideoResizeMode.Fit) }
    var errorMessage by remember(currentChannel.streamUrl) { mutableStateOf<String?>(null) }
    var shouldAutoRetryPlayback by remember(currentChannel.streamUrl) { mutableStateOf(true) }
    var isBuffering by remember(currentChannel.streamUrl) { mutableStateOf(false) }
    var isPlaying by remember(currentChannel.streamUrl) { mutableStateOf(true) }
    var currentPositionMs by remember(currentChannel.streamUrl) { mutableStateOf(0L) }
    var durationMs by remember(currentChannel.streamUrl) { mutableStateOf(0L) }
    var retryAttempt by remember(currentChannel.streamUrl) { mutableStateOf(0) }
    var autoRecoverAttempt by remember(currentChannel.streamUrl) { mutableStateOf(0) }
    var reconnectMessage by remember(currentChannel.streamUrl) { mutableStateOf<String?>(null) }
    var showControls by remember(currentChannel.streamUrl) { mutableStateOf(true) }
    var selectedControlIndex by remember(currentChannel.streamUrl) { mutableStateOf(0) }
    var selectedErrorActionIndex by remember(currentChannel.streamUrl) { mutableStateOf(0) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportMessage by remember { mutableStateOf<String?>(null) }
    var isSendingReport by remember { mutableStateOf(false) }
    var isFavorite by remember(currentChannel.streamUrl) {
        mutableStateOf(LocalLibrary.isFavorite(context, currentChannel.streamUrl))
    }

    val isVodContent = remember(currentChannel.name, currentChannel.group, currentChannel.streamUrl) {
        !isMagmaLiveStreamUrl(currentChannel.streamUrl) && isVodChannel(currentChannel)
    }

    var hasRestoredVodProgress by remember(currentChannel.streamUrl) { mutableStateOf(false) }
    var hasStreamReachedReady by remember(currentChannel.streamUrl) { mutableStateOf(false) }
    var playbackLoadAttempt by remember(currentChannel.streamUrl) { mutableStateOf(0) }

    var currentEpgProgram by remember(currentChannel.name) { mutableStateOf<EpgProgram?>(null) }
    var nextEpgProgram by remember(currentChannel.name) { mutableStateOf<EpgProgram?>(null) }

    LaunchedEffect(currentChannel.name) {
        val pair = withContext(Dispatchers.IO) {
            EpgMatcher.currentAndNext(context, currentChannel.name)
        }
        currentEpgProgram = pair.first
        nextEpgProgram = pair.second
    }

    DisposableEffect(Unit) {
        val previousFlags = view.systemUiVisibility

        view.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        onDispose {
            view.systemUiVisibility = previousFlags
        }
    }

    LaunchedEffect(showControls, currentChannel.streamUrl, selectedControlIndex) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    LaunchedEffect(reportMessage) {
        if (reportMessage != null) {
            delay(3000)
            reportMessage = null
        }
    }

    DisposableEffect(playerRootView) {
        val previousKeepScreenOn = playerRootView.keepScreenOn
        playerRootView.keepScreenOn = true

        onDispose {
            playerRootView.keepScreenOn = previousKeepScreenOn
        }
    }



    val player = remember(currentChannel.streamUrl) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        // --- INICIO MOTOR SUPERVIVENCIA LOCAL (V1.4.6) ---
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(
            context,
            androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory()
        ).apply {
            // 🛑 APAGAR TUNNELING: El causante #1 de que los Smart TV se cuelguen con IPTV
            setParameters(buildUponParameters().setTunnelingEnabled(false))
        }

        // 1. DataSource Inteligente: Cambia el User-Agent dinámicamente
        val isMagmaChannel = currentChannel.streamUrl.let { url ->
            url.contains("tv.m3uts.xyz") || url.contains("magma-lite") || url.contains("m3uts")
        }

        val finalDataSourceFactory = if (isMagmaChannel) {
            androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("Magma Player/10")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
        } else {
            androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.9 LibVLC/3.0.9")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
        }

        // 2. EL SECRETO (POLÍTICA DE ERRORES): En lugar de rendirse a los 3 errores de red, intentará reconectar silenciosamente 25 veces seguidas.
        val loadErrorPolicy = androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(25)

        // 🛡️ EL ESCUDO DEFINITIVO: Obliga al TV a ignorar los cambios bruscos de formato en el fútbol
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            )

        val finalMediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(finalDataSourceFactory)
            .setLoadErrorHandlingPolicy(loadErrorPolicy)

        // 3. HARDWARE PURO PARA TODOS (Cura de Congelamiento en TV)
        val smartRenderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        // 4. BÚFER NORMAL (15s a 30s de colchón para rapidez)
        val normalLoadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
            .build()

        androidx.media3.exoplayer.ExoPlayer.Builder(context, smartRenderersFactory)
            .setMediaSourceFactory(finalMediaSourceFactory)
            .setLoadControl(normalLoadControl)
            .build()
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> player.pause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> player.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(player, currentChannel.streamUrl) {
        val safeUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            forceHlsUrl(context, currentChannel.streamUrl)
        }
        val isMagma = currentChannel.streamUrl.contains("tv.m3uts.xyz") || currentChannel.streamUrl.contains("magma-lite")
        val mimeType = if (isMagma || safeUrl.contains(".m3u8")) {
            androidx.media3.common.MimeTypes.APPLICATION_M3U8
        } else null
        
        val mediaItemBuilder = androidx.media3.common.MediaItem.Builder().setUri(safeUrl)
        if (mimeType != null) mediaItemBuilder.setMimeType(mimeType)
        
        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(player, currentChannel.streamUrl, isVodContent) {
        var saveTick = 0

        while (true) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = if (player.duration > 0L) player.duration else 0L

            if (isVodContent && durationMs > 0L && currentPositionMs > 5000L) {
                saveTick += 1

                if (saveTick >= 5) {
                    saveTick = 0

                    val saveChannel = currentChannel
                    val savePosition = currentPositionMs
                    val saveDuration = durationMs

                    withContext(Dispatchers.IO) {
                        PlaybackProgressStore.save(
                            context = context.applicationContext,
                            channel = saveChannel,
                            positionMs = savePosition,
                            durationMs = saveDuration
                        )
                    }
                }
            }

            delay(1000)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                }

                if (playbackState == Player.STATE_READY) {
                    hasStreamReachedReady = true
                    retryAttempt = 0
                    reconnectMessage = null
                    errorMessage = null

                    if (isVodContent && !hasRestoredVodProgress) {
                        hasRestoredVodProgress = true

                        val saved = PlaybackProgressStore.get(context, currentChannel.streamUrl)
                        val duration = if (player.duration > 0L) {
                            player.duration
                        } else {
                            saved?.durationMs ?: 0L
                        }

                        val position = saved?.positionMs ?: 0L

                        if (
                            saved != null &&
                            !saved.finished &&
                            duration > 0L &&
                            position > 15000L &&
                            position < duration - 15000L
                        ) {
                            player.seekTo(position)
                            currentPositionMs = position
                            durationMs = duration
                            reconnectMessage = "Continuando desde ${formatPlaybackTime(position)}"
                            showControls = true
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                val friendlyError = friendlyPlaybackErrorMessage(error)
                val isMagma = currentChannel.streamUrl.contains("tv.m3uts.xyz")

                shouldAutoRetryPlayback = shouldAutoRetryForPlaybackError(error)
                
                errorMessage = if (isMagma && !friendlyError.contains("Magma", ignoreCase = true)) {
                    "Error de conexión Magma: $friendlyError"
                } else {
                    friendlyError
                }

                if (isMagma) {
                    val streamId = try { currentChannel.streamUrl.substringAfterLast("/").substringBefore(".m3u8") } catch (e: Exception) { "unknown" }
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        reportMagmaError(context, streamId, friendlyError, currentChannel.streamUrl)
                    }
                }

                reconnectMessage = if (shouldAutoRetryPlayback) {
                    "Detectamos un problema de reproducción."
                } else {
                    if (isMagma) {
                        "El enlace seguro de Magma expiró o no se pudo abrir."
                    } else {
                        "El contenido no respondió como video válido."
                    }
                }

                showControls = true
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)

            runCatching {
                player.playWhenReady = false
                player.stop()
                player.clearMediaItems()
            }

            player.release()
        }
    }

    fun restartPlayback() {
        errorMessage = null
        shouldAutoRetryPlayback = true
        hasStreamReachedReady = false
        playbackLoadAttempt += 1
        showControls = true

        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        player.seekTo(0) // Vaciado forzoso del chip físico
        
        scope.launch {
            val safeUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                forceHlsUrl(context, currentChannel.streamUrl)
            }
            val isMagma = currentChannel.streamUrl.contains("tv.m3uts.xyz") || currentChannel.streamUrl.contains("magma-lite")
            val mimeType = if (isMagma || safeUrl.contains(".m3u8")) {
                androidx.media3.common.MimeTypes.APPLICATION_M3U8
            } else null
            
            val mediaItemBuilder = androidx.media3.common.MediaItem.Builder().setUri(safeUrl)
            if (mimeType != null) mediaItemBuilder.setMimeType(mimeType)
            
            player.setMediaItem(mediaItemBuilder.build())
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun retryPlayback() {
        retryAttempt = 0
        autoRecoverAttempt = 0
        reconnectMessage = "Reintentando reproducción..."
        restartPlayback()
    }

    fun showPlaybackUnavailable(message: String) {
        shouldAutoRetryPlayback = false
        val isMagma = currentChannel.streamUrl.contains("tv.m3uts.xyz")
        
        errorMessage = if (isMagma) "Error Magma: $message" else message
        reconnectMessage = if (isMagma) {
            "Error Magma: No se pudo obtener o reproducir el enlace seguro.\nReintentá o reportá el canal."
        } else {
            "Contenido no disponible. Puedes reintentar, pasar al siguiente, reportar o volver."
        }
        selectedErrorActionIndex = 0
        showControls = true

        runCatching {
            player.playWhenReady = false
            player.stop()
        }
    }

    LaunchedEffect(player, currentChannel.streamUrl, playbackLoadAttempt) {
        delay(STREAM_UNAVAILABLE_TIMEOUT_MS)

        if (
            errorMessage == null &&
            !hasStreamReachedReady &&
            player.playbackState != Player.STATE_READY
        ) {
            showPlaybackUnavailable("La transmisión tardó demasiado en cargar.")
        }
    }

    LaunchedEffect(isBuffering, currentChannel.streamUrl, playbackLoadAttempt, hasStreamReachedReady) {
        if (isBuffering && hasStreamReachedReady && errorMessage == null) {
            delay(STREAM_UNAVAILABLE_TIMEOUT_MS)

            if (
                isBuffering &&
                errorMessage == null &&
                player.playbackState == Player.STATE_BUFFERING
            ) {
                showPlaybackUnavailable("La transmisión quedó cargando demasiado tiempo.")
            }
        }
    }

    LaunchedEffect(errorMessage, currentChannel.streamUrl, shouldAutoRetryPlayback) {
        if (errorMessage != null && shouldAutoRetryPlayback && retryAttempt < 9999) {
            val nextAttempt = retryAttempt + 1
            retryAttempt = nextAttempt
            reconnectMessage = "Reintentando automáticamente $nextAttempt/3..."
            delay(1800L * nextAttempt)
            restartPlayback()
        } else if (errorMessage != null && !shouldAutoRetryPlayback) {
            val isMagma = currentChannel.streamUrl.contains("tv.m3uts.xyz")
            reconnectMessage = if (isMagma) {
                "Error Magma: No se pudo obtener el enlace seguro del servidor.\nReintentá o reportá el canal."
            } else {
                "Contenido no disponible. Puedes reportarlo o volver."
            }
        } else if (errorMessage != null && retryAttempt >= 9999) {
            reconnectMessage = "No se pudo recuperar la reproducción. Prueba Reintentar o Reportar."
        }
    }

    LaunchedEffect(isBuffering, currentChannel.streamUrl) {
        if (isBuffering && errorMessage == null && autoRecoverAttempt < 9999) {
            delay(15000L)

            if (isBuffering && errorMessage == null) {
                val nextAttempt = autoRecoverAttempt + 1
                autoRecoverAttempt = nextAttempt
                reconnectMessage = "El canal tarda en responder. Reconectando $nextAttempt/3..."
                showControls = true
                restartPlayback()
            }
        } else if (isBuffering && errorMessage == null && autoRecoverAttempt >= 9999) {
            delay(5000L)

            if (isBuffering && errorMessage == null) {
                shouldAutoRetryPlayback = false
                errorMessage = "La transmisión quedó cargando demasiado tiempo."
                reconnectMessage = "No se pudo recuperar automáticamente. Prueba Reintentar o Reportar."
                showControls = true
            }
        }
    }

    LaunchedEffect(player, currentChannel.streamUrl, isVodContent) {
        if (isVodContent) {
            return@LaunchedEffect
        }

        var lastPositionMs = -1L
        var stuckSeconds = 0
        var healthyTicks = 0

        delay(6000L)

        while (true) {
            delay(3000L)

            if (
                errorMessage != null ||
                isBuffering ||
                !player.playWhenReady ||
                player.playbackState != Player.STATE_READY
            ) {
                lastPositionMs = -1L
                stuckSeconds = 0
                healthyTicks = 0
                continue
            }

            val positionMs = player.currentPosition.coerceAtLeast(0L)
            val moved = lastPositionMs < 0L || positionMs > lastPositionMs + 600L

            if (moved) {
                stuckSeconds = 0
                healthyTicks += 1

                if (healthyTicks >= 4) {
                    autoRecoverAttempt = 0
                }
            } else {
                stuckSeconds += 3
                healthyTicks = 0
            }

            lastPositionMs = positionMs

            if (stuckSeconds >= 15) {
                if (autoRecoverAttempt < 9999) {
                    val nextAttempt = autoRecoverAttempt + 1
                    autoRecoverAttempt = nextAttempt
                    reconnectMessage = "La transmisión quedó congelada. Reconectando $nextAttempt/3..."
                    showControls = true
                    restartPlayback()

                    lastPositionMs = -1L
                    stuckSeconds = 0
                    healthyTicks = 0
                    delay(5000L)
                } else {
                    shouldAutoRetryPlayback = false
                    errorMessage = "La transmisión quedó congelada."
                    reconnectMessage = "No se pudo recuperar automáticamente. Prueba Reintentar o Reportar."
                    showControls = true

                    lastPositionMs = -1L
                    stuckSeconds = 0
                    healthyTicks = 0
                }
            }
        }
    }

    fun seekBy(offsetMs: Long) {
        showControls = true

        val duration = if (player.duration > 0L) player.duration else 0L
        val current = player.currentPosition.coerceAtLeast(0L)
        val target = if (duration > 0L) {
            (current + offsetMs).coerceIn(0L, duration)
        } else {
            (current + offsetMs).coerceAtLeast(0L)
        }

        player.seekTo(target)
        currentPositionMs = target
        durationMs = duration
    }

    fun togglePlayPause() {
        showControls = true

        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun sendReport(
        problemType: String,
        afterSend: (() -> Unit)? = null
    ) {
        if (isSendingReport) return

        val isBrokenLinkReport =
            problemType.contains("enlace caído", ignoreCase = true) ||
                problemType.contains("contenido no disponible", ignoreCase = true)

        if (
            isBrokenLinkReport &&
            BrokenLinkStore.isReported(context, currentChannel.streamUrl)
        ) {
            reportMessage = "Este enlace ya estaba reportado."
            showReportDialog = false
            showControls = true
            afterSend?.invoke()
            return
        }

        isSendingReport = true
        reportMessage = null

        if (isBrokenLinkReport) {
            BrokenLinkStore.markReported(context, currentChannel.streamUrl)
        }

        val account = LocalAccount.getAccount(context)

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ChannelReportApi.send(
                    ChannelReportPayload(
                        channelName = currentChannel.name,
                        category = currentChannel.group,
                        streamUrl = currentChannel.streamUrl,
                        problemType = problemType,
                        playerError = errorMessage ?: "Sin error capturado",
                        androidVersion = Build.VERSION.RELEASE,
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                        account = account
                    )
                )
            }

            isSendingReport = false
            showReportDialog = false
            reportMessage = result.message
            showControls = true

            afterSend?.invoke()
        }
    }

    fun toggleFavorite() {
        showControls = true

        if (isFavorite) {
            LocalLibrary.removeFavorite(context, currentChannel.streamUrl)
            isFavorite = false
        } else {
            LocalLibrary.addFavorite(context, currentChannel)
            isFavorite = true
        }
    }

    fun zapPrevious() {
        val previous = PlayerSession.previous() ?: return
        currentChannel = previous
        LocalLibrary.addHistory(context, previous)
        showControls = true
        selectedControlIndex = 1
    }

    fun zapNext() {
        val next = PlayerSession.next() ?: return
        currentChannel = next
        LocalLibrary.addHistory(context, next)
        showControls = true
        selectedControlIndex = 2
    }

    fun errorActionCount(): Int {
        return if (PlayerSession.hasNext()) 5 else 3
    }

    fun activateSelectedErrorAction() {
        showControls = true

        val hasNext = PlayerSession.hasNext()

        when (selectedErrorActionIndex.coerceIn(0, errorActionCount() - 1)) {
            0 -> retryPlayback()

            1 -> {
                if (hasNext) {
                    selectedControlIndex = 2
                    zapNext()
                } else {
                    selectedControlIndex = 5
                    sendReport("Enlace caído / contenido no disponible")
                }
            }

            2 -> {
                if (hasNext) {
                    selectedControlIndex = 5
                    sendReport("Enlace caído / contenido no disponible")
                } else {
                    onBack()
                }
            }

            3 -> {
                if (hasNext) {
                    selectedControlIndex = 2
                    sendReport("Enlace caído / contenido no disponible") {
                        zapNext()
                    }
                } else {
                    onBack()
                }
            }

            else -> onBack()
        }
    }

    fun activateSelectedControl() {
        showControls = true

        when (selectedControlIndex) {
            0 -> togglePlayPause()
            1 -> {
                if (isVodContent) {
                    seekBy(-10000L)
                } else {
                    zapPrevious()
                }
            }
            2 -> {
                if (isVodContent) {
                    seekBy(30000L)
                } else {
                    zapNext()
                }
            }
            3 -> {
                videoResizeMode = videoResizeMode.next()
                showControls = true
            }
            4 -> toggleFavorite()
            5 -> {
                showControls = true
                showReportDialog = true
            }
            6 -> retryPlayback()
            7 -> onBack()
        }
    }

    LaunchedEffect(currentChannel.streamUrl) {
        selectedErrorActionIndex = 0
        runCatching {
            playerFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            selectedErrorActionIndex = 0
            showControls = true
        }
    }

    BackHandler {
        if (isVodContent && durationMs > 0L && currentPositionMs > 3000L) {
            val saveChannel = currentChannel
            val savePosition = currentPositionMs
            val saveDuration = durationMs

            scope.launch(Dispatchers.IO) {
                PlaybackProgressStore.save(
                    context = context.applicationContext,
                    channel = saveChannel,
                    positionMs = savePosition,
                    durationMs = saveDuration
                )
            }
        }

        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(playerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                if (errorMessage != null) {
                    val count = errorActionCount()

                    when (event.key) {
                        Key.DirectionDown -> {
                            selectedErrorActionIndex = (selectedErrorActionIndex + 1) % count
                            showControls = true
                            true
                        }

                        Key.DirectionUp -> {
                            selectedErrorActionIndex = if (selectedErrorActionIndex <= 0) {
                                count - 1
                            } else {
                                selectedErrorActionIndex - 1
                            }
                            showControls = true
                            true
                        }

                        Key.DirectionRight -> {
                            if (isVodContent) {
                                seekBy(30000L)
                            } else {
                                zapNext()
                            }
                            true
                        }

                        Key.DirectionLeft -> {
                            if (isVodContent) {
                                seekBy(-10000L)
                            } else {
                                zapPrevious()
                            }
                            true
                        }

                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter -> {
                            activateSelectedErrorAction()
                            true
                        }

                        else -> false
                    }
                } else {
                    when (event.key) {
                        Key.DirectionRight -> {
                            if (isVodContent) {
                                seekBy(30000L)
                            } else {
                                zapNext()
                            }
                            true
                        }

                        Key.DirectionLeft -> {
                            if (isVodContent) {
                                seekBy(-10000L)
                            } else {
                                zapPrevious()
                            }
                            true
                        }

                        Key.DirectionUp -> {
                            showControls = true
                            selectedControlIndex = if (selectedControlIndex <= 0) {
                                7
                            } else {
                                selectedControlIndex - 1
                            }
                            true
                        }

                        Key.DirectionDown -> {
                            showControls = true
                            selectedControlIndex = (selectedControlIndex + 1) % 8
                            true
                        }

                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter -> {
                            if (showControls) {
                                activateSelectedControl()
                            } else {
                                showControls = true
                            }
                            true
                        }

                        else -> false
                    }
                }
            }
            .background(MaterialTheme.colorScheme.background)
            .clickable {
                showControls = !showControls
            }
    ) {
        key(currentChannel.streamUrl) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PlayerView(it).apply {
                    /* PANTALLA LIMPIA: Error de Google evitado */
                        keepScreenOn = true
                        useController = false
                        resizeMode = videoResizeMode.media3Mode
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = {
                    it.keepScreenOn = true
                    it.player = player
                    it.useController = false
                    it.resizeMode = videoResizeMode.media3Mode
                }
            )
        }

        if (showControls) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = currentChannel.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${currentChannel.group} · Vista: ${videoResizeMode.label}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (showControls && errorMessage == null) {
            PlayerCenterControl(
                selected = selectedControlIndex == 0,
                isPlaying = isPlaying,
                onClick = {
                    selectedControlIndex = 0
                    togglePlayPause()
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (isBuffering) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 96.dp)
            ) {
                Text(
                    text = "Cargando...",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (errorMessage == null) {
            reconnectMessage?.let { message ->
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 154.dp)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        errorMessage?.let { message ->
            PlaybackErrorCard(
                message = message,
                isLandscape = isLandscape,
                isSendingReport = isSendingReport,
                canNext = PlayerSession.hasNext(),
                selectedActionIndex = selectedErrorActionIndex,
                onRetry = {
                    selectedControlIndex = 6
                    retryPlayback()
                },
                onNext = {
                    selectedControlIndex = 2
                    zapNext()
                },
                onReport = {
                    selectedControlIndex = 5
                    sendReport("Enlace caído / contenido no disponible")
                },
                onReportAndNext = {
                    selectedControlIndex = 2
                    sendReport("Enlace caído / contenido no disponible") {
                        zapNext()
                    }
                },
                onBack = onBack,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        reportMessage?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        if (showControls && errorMessage == null){
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
            ) {
                PlayerBottomOverlay(
                    channel = currentChannel,
                    isFavorite = isFavorite,
                    canPrevious = PlayerSession.hasPrevious(),
                    canNext = PlayerSession.hasNext(),
                    resizeModeLabel = videoResizeMode.label,
                    currentProgram = currentEpgProgram,
                    nextProgram = nextEpgProgram,
                    isLandscape = isLandscape,
                    isVodContent = isVodContent,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    selectedControlIndex = selectedControlIndex,
                    onPrevious = {
                        selectedControlIndex = 1
                        if (isVodContent) {
                            seekBy(-10000L)
                        } else {
                            zapPrevious()
                        }
                    },
                    onNext = {
                        selectedControlIndex = 2
                        if (isVodContent) {
                            seekBy(30000L)
                        } else {
                            zapNext()
                        }
                    },
                    onFavorite = {
                        selectedControlIndex = 4
                        toggleFavorite()
                    },
                    onReport = {
                        selectedControlIndex = 5
                        showControls = true
                        showReportDialog = true
                    },
                    onRetry = {
                        selectedControlIndex = 6
                        retryPlayback()
                    },
                    onChangeResizeMode = {
                        selectedControlIndex = 3
                        videoResizeMode = videoResizeMode.next()
                        showControls = true
                    },
                    onBack = {
                        selectedControlIndex = 7
                        onBack()
                    }
                )
            }
        }

        if (showReportDialog) {
            ReportDialog(
                isSending = isSendingReport,
                onDismiss = {
                    if (!isSendingReport) {
                        showReportDialog = false
                    }
                },
                onSend = ::sendReport
            )
        }
    
        if (showControls && !isLandscape) {
            PlayerPortraitBackButton(
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 12.dp)
            )
        }
}
}

@Composable
private fun PlaybackErrorCard(
    message: String,
    isLandscape: Boolean,
    isSendingReport: Boolean,
    canNext: Boolean,
    selectedActionIndex: Int,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onReport: () -> Unit,
    onReportAndNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeSelectedIndex = selectedActionIndex.coerceIn(0, if (canNext) 4 else 2)

    Card(
        modifier = modifier
            .navigationBarsPadding()
            .padding(
                start = 14.dp,
                end = 14.dp,
                bottom = if (isLandscape) 78.dp else 112.dp
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (message.contains("Magma", ignoreCase = true)) "Error de Magma" else "Contenido no disponible",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isLandscape) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ErrorActionButton(
                        text = "Reintentar",
                        selected = safeSelectedIndex == 0,
                        enabled = !isSendingReport,
                        primary = true,
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    )

                    if (canNext) {
                        ErrorActionButton(
                            text = "Siguiente",
                            selected = safeSelectedIndex == 1,
                            enabled = !isSendingReport,
                            onClick = onNext,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    ErrorActionButton(
                        text = if (isSendingReport) "Enviando..." else "Reportar",
                        selected = safeSelectedIndex == if (canNext) 2 else 1,
                        enabled = !isSendingReport,
                        onClick = onReport,
                        modifier = Modifier.weight(1f)
                    )

                    if (canNext) {
                        ErrorActionButton(
                            text = if (isSendingReport) "Enviando..." else "Reportar + sig.",
                            selected = safeSelectedIndex == 3,
                            enabled = !isSendingReport,
                            onClick = onReportAndNext,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    ErrorActionButton(
                        text = "Volver",
                        selected = safeSelectedIndex == if (canNext) 4 else 2,
                        enabled = true,
                        onClick = onBack,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ErrorActionButton(
                        text = "Reintentar",
                        selected = safeSelectedIndex == 0,
                        enabled = !isSendingReport,
                        primary = true,
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (canNext) {
                        ErrorActionButton(
                            text = "Siguiente",
                            selected = safeSelectedIndex == 1,
                            enabled = !isSendingReport,
                            onClick = onNext,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ErrorActionButton(
                        text = if (isSendingReport) "Enviando reporte..." else "Reportar enlace",
                        selected = safeSelectedIndex == if (canNext) 2 else 1,
                        enabled = !isSendingReport,
                        onClick = onReport,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (canNext) {
                        ErrorActionButton(
                            text = if (isSendingReport) "Enviando reporte..." else "Reportar y seguir",
                            selected = safeSelectedIndex == 3,
                            enabled = !isSendingReport,
                            onClick = onReportAndNext,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ErrorActionButton(
                        text = "Volver",
                        selected = safeSelectedIndex == if (canNext) 4 else 2,
                        enabled = true,
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorActionButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    primary: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)

    Surface(
        modifier = modifier
            .border(
                width = if (selected) 4.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                },
                shape = shape
            )
            .clickable(enabled = enabled) { onClick() },
        color = when {
            selected -> MaterialTheme.colorScheme.primary
            primary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
            else -> MaterialTheme.colorScheme.surface
        },
        shape = shape,
        shadowElevation = if (selected) 12.dp else 2.dp
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = if (selected || primary) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun PlayerCenterControl(selected: Boolean, isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (selected) androidx.compose.ui.graphics.Color(0xFF69A8FF) else androidx.compose.ui.graphics.Color(0xFF07111B).copy(alpha = 0.75f)
    val textColor = if (selected) androidx.compose.ui.graphics.Color(0xFF07111B) else androidx.compose.ui.graphics.Color.White
    Surface(modifier = modifier.clickable { onClick() }, color = bgColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(2.dp, if (selected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent)) {
        androidx.compose.material3.Text(text = if (isPlaying) "⏸ Pausa" else "▶ Reproducir", color = textColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
    }
}



@Composable
private fun ReportDialog(
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    val options = listOf(
        "No reproduce",
        "Se corta",
        "Sin audio",
        "Sin video",
        "Canal incorrecto",
        "Baja calidad",
        "Audio desfasado",
        "Subtitulos incorrectos",
        "Otro problema"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reportar canal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Selecciona el problema detectado.")

                options.forEach { option ->
                    OutlinedButton(
                        onClick = { onSend(option) },
                        enabled = !isSending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(option)
                    }
                }

                if (isSending) {
                    Text("Enviando reporte...")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSending
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun PlayerBottomOverlay(
    channel: SavedChannel,
    isFavorite: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    resizeModeLabel: String,
    currentProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    isLandscape: Boolean,
    isVodContent: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    selectedControlIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onReport: () -> Unit,
    onRetry: () -> Unit,
    onChangeResizeMode: () -> Unit,
    onBack: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(colors = listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color(0xFF07111B).copy(alpha = 0.95f))))
            .padding(horizontal = 12.dp, vertical = if (isLandscape) 8.dp else 10.dp)
    ) {
        val compact = !isLandscape || maxWidth < 780.dp

        if (compact) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${channel.group} · Vista: $resizeModeLabel",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isVodContent && durationMs > 0L) {
                    PlayerProgressBar(
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                PlayerEpgInfo(
                    currentProgram = currentProgram,
                    nextProgram = nextProgram
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayerControlChip(if (isVodContent) "-10s" else "Ant.", selectedControlIndex == 1, if (isVodContent) currentPositionMs > 1000L else canPrevious, onPrevious)
                    PlayerControlChip(if (isVodContent) "+30s" else "Sig.", selectedControlIndex == 2, if (isVodContent) durationMs <= 0L || currentPositionMs < durationMs - 1000L else canNext, onNext)
                    PlayerControlChip("Vista", selectedControlIndex == 3, true, onChangeResizeMode)
                    PlayerControlChip(if (isFavorite) "Quitar" else "Fav.", selectedControlIndex == 4, true, onFavorite)
                    PlayerControlChip("Reportar", selectedControlIndex == 5, true, onReport)
                    PlayerControlChip("Reint.", selectedControlIndex == 6, true, onRetry)
                    PlayerControlChip("Volver", selectedControlIndex == 7, true, onBack)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${channel.group} · Vista: $resizeModeLabel",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isVodContent && durationMs > 0L) {
                    PlayerProgressBar(
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                PlayerEpgInfo(
                        currentProgram = currentProgram,
                        nextProgram = nextProgram
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerControlChip(if (isVodContent) "-10s" else "Ant.", selectedControlIndex == 1, if (isVodContent) currentPositionMs > 1000L else canPrevious, onPrevious)
                    PlayerControlChip(if (isVodContent) "+30s" else "Sig.", selectedControlIndex == 2, if (isVodContent) durationMs <= 0L || currentPositionMs < durationMs - 1000L else canNext, onNext)
                    PlayerControlChip("Vista", selectedControlIndex == 3, true, onChangeResizeMode)
                    PlayerControlChip(if (isFavorite) "Quitar" else "Fav.", selectedControlIndex == 4, true, onFavorite)
                    PlayerControlChip("Reportar", selectedControlIndex == 5, true, onReport)
                    PlayerControlChip("Reint.", selectedControlIndex == 6, true, onRetry)
                    PlayerControlChip("Volver", selectedControlIndex == 7, true, onBack)
                }
            }
        }
    }
}

@Composable
private fun PlayerControlChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val bgColor = if (selected) androidx.compose.ui.graphics.Color(0xFF69A8FF) else androidx.compose.ui.graphics.Color(0xFF162338).copy(alpha = 0.8f)
    val textColor = if (selected) androidx.compose.ui.graphics.Color(0xFF07111B) else androidx.compose.ui.graphics.Color(0xFFBBC6D8)
    Surface(color = if (enabled) bgColor else androidx.compose.ui.graphics.Color.Transparent, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) androidx.compose.ui.graphics.Color.Transparent else androidx.compose.ui.graphics.Color(0x264C6D95)), modifier = Modifier.clickable(enabled = enabled) { onClick() }) {
        androidx.compose.material3.Text(text = label, color = textColor, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}




@Composable
private fun PlayerProgressBar(currentPositionMs: Long, durationMs: Long) {
    val progress = (currentPositionMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(androidx.compose.ui.graphics.Color(0xFF223754), androidx.compose.foundation.shape.RoundedCornerShape(50))) {
            Box(modifier = Modifier.fillMaxWidth(progress).height(4.dp).background(androidx.compose.ui.graphics.Color(0xFF69A8FF), androidx.compose.foundation.shape.RoundedCornerShape(50)))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            androidx.compose.material3.Text(formatPlaybackTime(currentPositionMs), color = androidx.compose.ui.graphics.Color(0xFFBBC6D8), fontSize = 12.sp)
            androidx.compose.material3.Text(formatPlaybackTime(durationMs), color = androidx.compose.ui.graphics.Color(0xFFBBC6D8), fontSize = 12.sp)
        }
    }
}



@Composable
private fun PlayerEpgInfo(
    currentProgram: EpgProgram?,
    nextProgram: EpgProgram?
) {
    currentProgram?.let { program ->
        Text(
            text = "Ahora: ${program.title}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    nextProgram?.let { program ->
        Text(
            text = "Próximo ${formatPlayerEpgTime(program.startAtMillis)}: ${program.title}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


private fun isVodChannel(channel: SavedChannel): Boolean {
    val group = channel.group.lowercase(Locale.getDefault())
    val name = channel.name.lowercase(Locale.getDefault())
    val url = channel.streamUrl.lowercase(Locale.getDefault())

    val looksLiveGroup =
        group.startsWith("tv ") ||
            group.startsWith("tv |") ||
            group.startsWith("tv 0") ||
            group.contains("en vivo") ||
            group.contains("canales")

    if (looksLiveGroup) {
        return false
    }

    val looksVodGroup =
        group.contains("pelicula") ||
            group.contains("película") ||
            group.contains("movie") ||
            group.contains("vod") ||
            group.contains("cine") ||
            group.contains("serie") ||
            group.contains("series") ||
            group.contains("temporada") ||
            group.contains("capitulo") ||
            group.contains("capítulo") ||
            group.contains("anime")

    val looksEpisode =
        Regex("\\bs[0-9]{1,2}\\s*e[0-9]{1,3}\\b").containsMatchIn(name) ||
            Regex("\\b[0-9]{1,2}x[0-9]{1,3}\\b").containsMatchIn(name)

    val looksVodUrl =
        url.contains(".mp4") ||
            url.contains(".mkv") ||
            url.contains(".avi") ||
            url.contains(".mov") ||
            url.contains(".webm")

    return looksVodGroup || looksEpisode || looksVodUrl
}

private fun formatPlaybackTime(value: Long): String {
    val totalSeconds = (value / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}


private fun formatPlayerEpgTime(value: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value))
}


@Composable
private fun PlayerPortraitBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.clickable { onBack() }.padding(16.dp), color = androidx.compose.ui.graphics.Color(0xFF162338).copy(alpha = 0.9f), shape = androidx.compose.foundation.shape.RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x264C6D95))) {
        androidx.compose.material3.Text("⬅ Volver", color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}




private fun friendlyPlaybackErrorMessage(error: PlaybackException): String {
    val raw = listOfNotNull(
        error.message,
        error.cause?.message,
        runCatching { error.errorCodeName }.getOrNull()
    ).joinToString(" ").lowercase(Locale.getDefault())

    return when {
        raw.contains("source") ||
            raw.contains("404") ||
            raw.contains("403") ||
            raw.contains("file not found") ||
            raw.contains("not found") ||
            raw.contains("response code") ||
            raw.contains("invalid response") -> {
            "El enlace no respondió, está caído o el servidor rechazó la reproducción."
        }

        raw.contains("timeout") ||
            raw.contains("timed out") ||
            raw.contains("unable to connect") ||
            raw.contains("failed to connect") -> {
            "El servidor tardó demasiado en responder. Probá de nuevo más tarde."
        }

        raw.contains("behind live window") -> {
            "La transmisión en vivo cambió de posición. Tocá Reintentar para reconectar."
        }

        raw.contains("decoder") ||
            raw.contains("format") ||
            raw.contains("codec") -> {
            "El formato de video no es compatible con este dispositivo."
        }

        raw.isBlank() -> {
            "No se pudo reproducir este contenido."
        }

        else -> {
            error.message ?: "No se pudo reproducir este contenido."
        }
    }
}

private fun shouldAutoRetryForPlaybackError(error: PlaybackException): Boolean {
    val raw = listOfNotNull(
        error.message,
        error.cause?.message,
        runCatching { error.errorCodeName }.getOrNull()
    ).joinToString(" ").lowercase(Locale.getDefault())

    if (
        raw.contains("404") ||
        raw.contains("403") ||
        raw.contains("file not found") ||
        raw.contains("not found") ||
        raw.contains("invalid response")
    ) {
        return false
    }

    if (raw.contains("source")) {
        return false
    }

    return true
}


// 🔥 MUTADOR HLS ANDROID 🔥
suspend fun forceHlsUrl(context: android.content.Context, originalUrl: String): String {
    
    if (originalUrl.contains("tv.m3uts.xyz")) {
        try {
            val clean = originalUrl.substringBefore("?").replace(".ts", "").replace(".m3u8", "")
            val streamId = clean.split("/").last()
            
            val urlSegura = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                obtenerUrlSeguraMagma(context, streamId)
            }
            if (urlSegura != null) return urlSegura
        } catch (e: Exception) {
            android.util.Log.e("MagmaFix", "Error forceHlsUrl Magma", e)
        }
        
        return originalUrl
    }

    // 2. Comportamiento original para otras URLs (el mutador normal)
    var finalUrl = originalUrl
    try {
        if (!finalUrl.contains(".m3u8") && !finalUrl.contains("movie") && !finalUrl.contains("series")) {
            val clean = finalUrl.substringBefore("?").replace(".ts", "")
            val parts = clean.split("/")
            if (parts.size >= 4 && !finalUrl.contains("magma-lite") && !finalUrl.contains("xtream-lite")) {
                val id = parts.last()
                val pass = parts[parts.size - 2]
                val user = parts[parts.size - 3]
                var base = parts.dropLast(3).joinToString("/")
                if (base.endsWith("/live")) {
                    base = base.substring(0, base.length - 5)
                }
                finalUrl = "$base/live/$user/$pass/$id.m3u8"
            }
        }
    } catch (e: Exception) {}
    
    return finalUrl
}
