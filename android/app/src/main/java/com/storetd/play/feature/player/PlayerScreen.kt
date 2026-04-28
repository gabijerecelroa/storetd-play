package com.storetd.play.feature.player

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
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

    var errorMessage by remember(currentChannel.streamUrl) { mutableStateOf<String?>(null) }
    var isBuffering by remember(currentChannel.streamUrl) { mutableStateOf(false) }
    var showTopOverlay by remember(currentChannel.streamUrl) { mutableStateOf(true) }
    var isFavorite by remember(currentChannel.streamUrl) {
        mutableStateOf(LocalLibrary.isFavorite(context, currentChannel.streamUrl))
    }

    LaunchedEffect(currentChannel.streamUrl) {
        showTopOverlay = true
        delay(2200)
        showTopOverlay = false
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

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.message ?: "No se pudo reproducir este canal."
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
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(currentChannel.streamUrl))
        player.prepare()
        player.playWhenReady = true
    }

    fun reportChannel() {
        val body = """
            Reporte de canal

            Canal: ${currentChannel.name}
            Categoria: ${currentChannel.group}
            URL oculta: ${LocalLibrary.maskUrl(currentChannel.streamUrl)}

            Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE}
            Version app: ${BuildConfig.VERSION_NAME}
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
    }

    fun zapNext() {
        val next = PlayerSession.next() ?: return
        currentChannel = next
        LocalLibrary.addHistory(context, next)
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        key(currentChannel.streamUrl) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PlayerView(it).apply {
                        useController = true
                        resizeMode = if (isLandscape) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = {
                    it.player = player
                    it.resizeMode = if (isLandscape) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            )
        }

        if (showTopOverlay) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = currentChannel.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentChannel.group,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (isBuffering) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Cargando stream...",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        errorMessage?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = if (isLandscape) 92.dp else 132.dp)
            ) {
                Text(
                    text = "Error de reproducción: $message",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            PlayerBottomOverlay(
                channel = currentChannel,
                isFavorite = isFavorite,
                canPrevious = PlayerSession.hasPrevious(),
                canNext = PlayerSession.hasNext(),
                isLandscape = isLandscape,
                onPrevious = ::zapPrevious,
                onNext = ::zapNext,
                onFavorite = ::toggleFavorite,
                onReport = ::reportChannel,
                onRetry = ::retryPlayback,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun PlayerBottomOverlay(
    channel: SavedChannel,
    isFavorite: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    isLandscape: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onReport: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        val compact = !isLandscape || maxWidth < 760.dp

        if (compact) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = channel.group,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = canPrevious
                    ) {
                        Text("Anterior")
                    }

                    OutlinedButton(
                        onClick = onNext,
                        enabled = canNext
                    ) {
                        Text("Siguiente")
                    }

                    Button(onClick = onFavorite) {
                        Text(if (isFavorite) "Quitar favorito" else "Favorito")
                    }

                    OutlinedButton(onClick = onReport) {
                        Text("Reportar")
                    }

                    OutlinedButton(onClick = onRetry) {
                        Text("Reintentar")
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
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = channel.group,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = canPrevious
                    ) {
                        Text("Anterior")
                    }

                    OutlinedButton(
                        onClick = onNext,
                        enabled = canNext
                    ) {
                        Text("Siguiente")
                    }

                    Button(onClick = onFavorite) {
                        Text(if (isFavorite) "Quitar favorito" else "Favorito")
                    }

                    OutlinedButton(onClick = onReport) {
                        Text("Reportar")
                    }

                    OutlinedButton(onClick = onRetry) {
                        Text("Reintentar")
                    }

                    Button(onClick = onBack) {
                        Text("Volver")
                    }
                }
            }
        }
    }
}
