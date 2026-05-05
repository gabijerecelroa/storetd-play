package com.storetd.play.feature.player

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
        isVodChannel(currentChannel)
    }

    var hasRestoredVodProgress by remember(currentChannel.streamUrl) { mutableStateOf(false) }

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

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                8_000,
                45_000,
                1_200,
                3_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(currentChannel.streamUrl))
                prepare()
                playWhenReady = true
            }
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

                if (playbackState == Player.STATE_READY) {
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

                shouldAutoRetryPlayback = shouldAutoRetryForPlaybackError(error)
                errorMessage = friendlyError

                reconnectMessage = if (shouldAutoRetryPlayback) {
                    "Detectamos un problema de reproducción."
                } else {
                    "El contenido no respondió como video válido."
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
        showControls = true

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(currentChannel.streamUrl))
        player.prepare()
        player.playWhenReady = true
    }

    fun retryPlayback() {
        retryAttempt = 0
        autoRecoverAttempt = 0
        reconnectMessage = "Reintentando reproducción..."
        restartPlayback()
    }

    LaunchedEffect(errorMessage, currentChannel.streamUrl, shouldAutoRetryPlayback) {
        if (errorMessage != null && shouldAutoRetryPlayback && retryAttempt < 3) {
            val nextAttempt = retryAttempt + 1
            retryAttempt = nextAttempt
            reconnectMessage = "Reintentando automáticamente $nextAttempt/3..."
            delay(1800L * nextAttempt)
            restartPlayback()
        } else if (errorMessage != null && !shouldAutoRetryPlayback) {
            reconnectMessage = "Contenido no disponible. Puedes reportarlo o volver."
        } else if (errorMessage != null && retryAttempt >= 3) {
            reconnectMessage = "No se pudo recuperar la reproducción. Prueba Reintentar o Reportar."
        }
    }

    LaunchedEffect(isBuffering, currentChannel.streamUrl) {
        if (isBuffering && errorMessage == null && autoRecoverAttempt < 3) {
            delay(12000L)

            if (isBuffering && errorMessage == null) {
                val nextAttempt = autoRecoverAttempt + 1
                autoRecoverAttempt = nextAttempt
                reconnectMessage = "El canal tarda en responder. Reconectando $nextAttempt/3..."
                showControls = true
                restartPlayback()
            }
        } else if (isBuffering && errorMessage == null && autoRecoverAttempt >= 3) {
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

            if (stuckSeconds >= 12) {
                if (autoRecoverAttempt < 3) {
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
                text = "Contenido no disponible",
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
private fun PlayerCenterControl(
    selected: Boolean,
    isPlaying: Boolean,
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
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
                },
                shape = shape
            )
            .clickable { onClick() },
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
        },
        shape = shape,
        shadowElevation = if (selected) 14.dp else 6.dp
    ) {
        Text(
            text = if (isPlaying) "Pausa" else "Play",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 14.dp)
        )
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
        modifier = Modifier
            .fillMaxWidth()
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
private fun PlayerControlChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)

    Surface(
        modifier = Modifier
            .border(
                width = if (selected) 4.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                },
                shape = shape
            )
            .clickable(enabled = enabled) { onClick() },
        color = when {
            !enabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
        },
        shape = shape,
        shadowElevation = if (selected) 12.dp else 3.dp,
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            }
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f)
            },
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            maxLines = 1
        )
    }
}


@Composable
private fun PlayerProgressBar(
    currentPositionMs: Long,
    durationMs: Long
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(999.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                    shape
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(7.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape
                    )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatPlaybackTime(currentPositionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
            )

            Text(
                text = formatPlaybackTime(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
            )
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
private fun PlayerPortraitBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable { onBack() },
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.96f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
        ),
        shadowElevation = 10.dp
    ) {
        Text(
            text = "Volver",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
        )
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


