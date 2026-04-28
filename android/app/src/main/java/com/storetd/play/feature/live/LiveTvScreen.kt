package com.storetd.play.feature.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        val isCompact = maxWidth < 700.dp

        if (isCompact) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    PlaylistControls(
                        playlistUrl = state.playlistUrl,
                        onPlaylistUrlChange = viewModel::setPlaylistUrl,
                        onLoadPlaylist = viewModel::loadPlaylist,
                        onBack = onBack
                    )
                }

                item {
                    CategoryRow(
                        groups = state.groups,
                        selectedGroup = state.selectedGroup,
                        onSelectGroup = viewModel::selectGroup
                    )
                }

                item {
                    StatusBlock(
                        isLoading = state.isLoading,
                        errorMessage = state.errorMessage,
                        count = state.visibleChannels.size
                    )
                }

                items(state.visibleChannels) { channel ->
                    ChannelRow(channel = channel, onPlay = { onPlay(channel) })
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.width(320.dp).fillMaxHeight()) {
                    PlaylistControls(
                        playlistUrl = state.playlistUrl,
                        onPlaylistUrlChange = viewModel::setPlaylistUrl,
                        onLoadPlaylist = viewModel::loadPlaylist,
                        onBack = onBack
                    )

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
                    StatusBlock(
                        isLoading = state.isLoading,
                        errorMessage = state.errorMessage,
                        count = state.visibleChannels.size
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
    }
}

@Composable
private fun PlaylistControls(
    playlistUrl: String,
    onPlaylistUrlChange: (String) -> Unit,
    onLoadPlaylist: () -> Unit,
    onBack: () -> Unit
) {
    Column {
        Text("TV en vivo", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = playlistUrl,
            onValueChange = onPlaylistUrlChange,
            label = { Text("URL M3U/M3U8 autorizada") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onLoadPlaylist,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cargar lista")
        }

        Spacer(Modifier.height(12.dp))

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver")
        }
    }
}

@Composable
private fun CategoryRow(
    groups: List<String>,
    selectedGroup: String,
    onSelectGroup: (String) -> Unit
) {
    Column {
        Text("Categorias", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups) { group ->
                FilterChip(
                    selected = group == selectedGroup,
                    onClick = { onSelectGroup(group) },
                    label = { Text(group) }
                )
            }
        }
    }
}

@Composable
private fun StatusBlock(
    isLoading: Boolean,
    errorMessage: String?,
    count: Int
) {
    Column {
        if (isLoading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }

        errorMessage?.let {
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
            text = "$count canales",
            style = MaterialTheme.typography.titleLarge
        )
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
