package com.storetd.play.feature.player

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.storetd.play.BuildConfig
import com.storetd.play.core.player.PlayerSession
import com.storetd.play.core.storage.LocalLibrary
import com.storetd.play.core.storage.SavedChannel
import kotlinx.coroutines.delay

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
    var showControls by remember(currentChannel.streamUrl) { mutableStateOf(true) }
    var isFavorite by remember(currentChannel.streamUrl) {
        mutableStateOf(LocalLibrary.isFavorite(context, currentChannel.streamUrl))
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

    LaunchedEffect(showControls, currentChannel.streamUrl) {
        if (showControls) {
            delay(4500)
            showControls = false
        }
    }

    val player = remember(currentChannel.streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(currentChannel.streamUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.message ?: "No se pudo reproducir este canal."
                showControls = true
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    fun retryPlayback() {
        errorMessage = null
        showControls = true
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(currentChannel.streamUrl))
        player.prepare()
        player.playWhenReady = true
    }

    fun togglePlayPause() {
        showControls = true

        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun reportChannel() {
        showControls = true

        val body = """
            Reporte de canal

            Canal: ${currentChannel.name}
            Categoria: ${currentChannel.group}
            URL oculta: ${LocalLibrary.maskUrl(currentChannel.streamUrl)}

            Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE}
            Version app: ${BuildConfig.VERSION_NAME}
            Modo vista: ${videoResizeMode.label}
            Error tecnico: ${errorMessage ?: "Sin error capturado"}

            Describe el problema:
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${BuildConfig.SUPPORT_EMAIL}")
            putExtra(Intent.EXTRA_SUBJECT, "Reporte de canal - ${currentChannel.name}")
            putExtra(Intent.EXTRA_TEXT, body)
        }

        runCatching { context.startActivity(intent) }
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
    }

    fun zapNext() {
        val next = PlayerSession.next() ?: return
        currentChannel = next
        LocalLibrary.addHistory(context, next)
        showControls = true
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
            Button(
                modifier = Modifier.align(Alignment.Center),
                onClick = {
                    togglePlayPause()
                }
            ) {
                Text(if (isPlaying) "Pausa" else "Play")
            }
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
                    isLandscape = isLandscape,
                    onPrevious = ::zapPrevious,
                    onNext = ::zapNext,
                    onFavorite = ::toggleFavorite,
                    onReport = ::reportChannel,
                    onRetry = ::retryPlayback,
                    onChangeResizeMode = {
                        videoResizeMode = videoResizeMode.next()
                        showControls = true
                    },
                    onBack = onBack
                )
            }
        }
    }
}

@Composable
private fun PlayerBottomOverlay(
    channel: SavedChannel,
    isFavorite: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    resizeModeLabel: String,
    isLandscape: Boolean,
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
                    text = "${channel.group} · $resizeModeLabel",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onPrevious, enabled = canPrevious) {
                        Text("Ant.")
                    }

                    OutlinedButton(onClick = onNext, enabled = canNext) {
                        Text("Sig.")
                    }

                    OutlinedButton(onClick = onChangeResizeMode) {
                        Text("Vista")
                    }

                    Button(onClick = onFavorite) {
                        Text(if (isFavorite) "Quitar" else "Fav.")
                    }

                    OutlinedButton(onClick = onReport) {
                        Text("Reportar")
                    }

                    OutlinedButton(onClick = onRetry) {
                        Text("Reint.")
                    }

                    Button(onClick = onBack) {
                        Text("Volver")
                    }
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
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPrevious, enabled = canPrevious) {
                        Text("Ant.")
                    }

                    OutlinedButton(onClick = onNext, enabled = canNext) {
                        Text("Sig.")
                    }

                    OutlinedButton(onClick = onChangeResizeMode) {
                        Text("Vista")
                    }

                    Button(onClick = onFavorite) {
                        Text(if (isFavorite) "Quitar" else "Fav.")
                    }

                    OutlinedButton(onClick = onReport) {
                        Text("Reportar")
                    }

                    OutlinedButton(onClick = onRetry) {
                        Text("Reint.")
                    }

                    Button(onClick = onBack) {
                        Text("Volver")
                    }
                }
            }
        }
    }
}
