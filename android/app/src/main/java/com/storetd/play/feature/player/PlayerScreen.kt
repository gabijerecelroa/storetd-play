package com.storetd.play.feature.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalContext

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channelName: String,
    streamUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }

    val player = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
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
                errorMessage = error.message ?: "No se pudo reproducir este canal"
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )

        if (isBuffering) {
            Text(
                text = "Cargando stream...",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        errorMessage?.let {
            Card(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Error de reproduccion: $it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Row(modifier = Modifier.padding(16.dp)) {
            Text(
                text = channelName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )

            Button(onClick = onBack) {
                Text("Volver")
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    errorMessage = null
                    player.stop()
                    player.clearMediaItems()
                    player.setMediaItem(MediaItem.fromUri(streamUrl))
                    player.prepare()
                    player.playWhenReady = true
                }
            ) {
                Text("Reintentar")
            }
        }
    }
}
