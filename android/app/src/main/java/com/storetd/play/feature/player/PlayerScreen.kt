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
import com.storetd.play.core.storage.LocalAccount
import com.storetd.play.core.storage.LocalLibrary
import com.storetd.play.core.storage.SavedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var isBuffering by remember(currentChannel.streamUrl) { mutableStateOf(false) }
    var isPlaying by remember(currentChannel.streamUrl) { mutableStateOf(true) }
    var currentPositionMs by remember(currentChannel.streamUrl) { mutableStateOf(0L) }
    var durationMs by remember(currentChannel.streamUrl) { mutableStateOf(0L) }
    var retryAttempt by remember(currentChannel.streamUrl) { mutableStateOf(0) }
    var reconnectMessage by remember(currentChannel.streamUrl) { mutableStateOf<String?>(null) }
    var showControls by remember(currentChannel.streamUrl) { mutableStateOf(true) }
    var selectedControlIndex by remember(currentChannel.streamUrl) { mutableStateOf(0) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportMessage by remember { mutableStateOf<String?>(null) }
    var isSendingReport by remember { mutableStateOf(false) }
    var isFavorite by remember(currentChannel.streamUrl) {
        mutableStateOf(LocalLibrary.isFavorite(context, currentChannel.streamUrl))
    }

    val isVodContent = remember(currentChannel.name, currentChannel.group, currentChannel.streamUrl) {
        isVodChannel(currentChannel)
    }

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


    val player = remember(currentChannel.streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(currentChannel.streamUrl))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(player, currentChannel.streamUrl) {
        while (true) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = if (player.duration > 0L) player.duration else 0L
            delay(500)
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
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.message ?: "No se pudo reproducir este canal."
                reconnectMessage = "Detectamos un problema de reproducción."
                showControls = true
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    fun restartPlayback() {
        errorMessage = null
        showControls = true

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(currentChannel.streamUrl))
        player.prepare()
        player.playWhenReady = true
    }

    fun retryPlayback() {
        retryAttempt = 0
        reconnectMessage = "Reintentando reproducción..."
        restartPlayback()
    }

    LaunchedEffect(errorMessage, currentChannel.streamUrl) {
        if (errorMessage != null && retryAttempt < 3) {
            val nextAttempt = retryAttempt + 1
            retryAttempt = nextAttempt
            reconnectMessage = "Reintentando automáticamente $nextAttempt/3..."
            delay(1800L * nextAttempt)
            restartPlayback()
        } else if (errorMessage != null && retryAttempt >= 3) {
            reconnectMessage = "No se pudo recuperar la reproducción. Prueba Reintentar o Siguiente."
        }
    }

    LaunchedEffect(isBuffering, currentChannel.streamUrl) {
        if (isBuffering && errorMessage == null && retryAttempt < 3) {
            delay(16000L)

            if (isBuffering && errorMessage == null) {
                val nextAttempt = retryAttempt + 1
                retryAttempt = nextAttempt
                reconnectMessage = "El canal tarda en responder. Reintentando $nextAttempt/3..."
                restartPlayback()
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

    fun sendReport(problemType: String) {
        isSendingReport = true
        reportMessage = null

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
        runCatching {
            playerFocusRequester.requestFocus()
        }
    }

    BackHandler {
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
                        useController = false
                        resizeMode = videoResizeMode.media3Mode
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = {
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

        if (showControls) {
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

        errorMessage?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        bottom = if (isLandscape) 78.dp else 112.dp
                    )
            ) {
                Text(
                    text = "Error: $message",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(14.dp)
                )
            }
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

        if (showControls) {
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
