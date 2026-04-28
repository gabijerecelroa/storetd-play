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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.storetd.play.core.model.Channel
import com.storetd.play.core.storage.LocalSettings
import com.storetd.play.core.storage.LocalAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    onBack: () -> Unit,
    onPlay: (Channel, List<Channel>) -> Unit,
    viewModel: LiveTvViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.setHideAdultContent(LocalSettings.isAdultContentHidden(context))

        val assignedPlaylist = LocalAccount.getAccount(context).playlistUrl
        if (state.playlistUrl.isBlank() && assignedPlaylist.isNotBlank()) {
            viewModel.setPlaylistUrl(assignedPlaylist)
        }
    }

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
                        state = state,
                        onPlaylistUrlChange = viewModel::setPlaylistUrl,
                        onSearchChange = viewModel::setSearchQuery,
                        onHideAdultChange = { hidden ->
                            LocalSettings.setAdultContentHidden(context, hidden)
                            viewModel.setHideAdultContent(hidden)
                        },
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
                    StatusBlock(state = state)
                }

                items(state.visibleChannels) { channel ->
                    ChannelRow(channel = channel, onPlay = { onPlay(channel, state.visibleChannels) })
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.width(340.dp).fillMaxHeight()) {
                    PlaylistControls(
                        state = state,
                        onPlaylistUrlChange = viewModel::setPlaylistUrl,
                        onSearchChange = viewModel::setSearchQuery,
                        onHideAdultChange = { hidden ->
                            LocalSettings.setAdultContentHidden(context, hidden)
                            viewModel.setHideAdultContent(hidden)
                        },
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
                    StatusBlock(state = state)
                    Spacer(Modifier.height(16.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.visibleChannels) { channel ->
                            ChannelRow(channel = channel, onPlay = { onPlay(channel, state.visibleChannels) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistControls(
    state: LiveTvUiState,
    onPlaylistUrlChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onHideAdultChange: (Boolean) -> Unit,
    onLoadPlaylist: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }
    var pinValue by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Column {
        Text("TV en vivo", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.playlistUrl,
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

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Buscar canal o categoria") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (state.hideAdultContent) {
                    pinValue = ""
                    pinError = null
                    showPinDialog = true
                } else {
                    onHideAdultChange(true)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (state.hideAdultContent) {
                    "Adultos ocultos: Si"
                } else {
                    "Adultos visibles: No ocultar"
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver")
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pinValue = ""
                pinError = null
            },
            title = { Text("Contenido adulto") },
            text = {
                Column {
                    Text("Ingresa el PIN para mostrar categorias adultas.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinValue,
                        onValueChange = { pinValue = it.take(8) },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    pinError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (LocalSettings.verifyPin(context, pinValue)) {
                            onHideAdultChange(false)
                            showPinDialog = false
                            pinValue = ""
                            pinError = null
                        } else {
                            pinError = "PIN incorrecto"
                        }
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                        pinValue = ""
                        pinError = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
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
private fun StatusBlock(state: LiveTvUiState) {
    Column {
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
            text = "${state.totalVisibleCount} canales visibles",
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
                    item { AssistChip(onClick = {}, label = { Text("Listo") }) }
                }
            }

            Button(onClick = onPlay) {
                Text("Ver")
            }
        }
    }
}
