package com.storetd.play.feature.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.storetd.play.core.model.Channel
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    onBack: () -> Unit,
    onPlay: (Channel) -> Unit,
    viewModel: LiveTvViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.width(300.dp).fillMaxHeight()) {
            Text("TV en vivo", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.playlistUrl,
                onValueChange = viewModel::setPlaylistUrl,
                label = { Text("URL M3U/M3U8 autorizada") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = viewModel::loadPlaylist,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cargar lista")
            }

            Spacer(Modifier.height(12.dp))

            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Volver")
            }

            Spacer(Modifier.height(24.dp))
            Text("Categorias", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.groups) { group ->
                    FilterChip(
                        selected = group == state.selectedGroup,
                        onClick = { viewModel.selectGroup(group) },
                        label = { Text(group) }
                    )
                }
            }
        }

        Spacer(Modifier.width(24.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (state.isLoading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
            }

            state.errorMessage?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = "${state.visibleChannels.size} canales",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.visibleChannels) { channel ->
                    ChannelRow(channel = channel, onPlay = { onPlay(channel) })
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, onPlay: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusable()
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            if (!channel.logoUrl.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(channel.logoUrl),
                    contentDescription = channel.name,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { AssistChip(onClick = {}, label = { Text(channel.group) }) }
                    item { AssistChip(onClick = {}, label = { Text("Estado: sin verificar") }) }
                }
            }

            Button(onClick = onPlay) {
                Text("Ver")
            }
        }
    }
}
