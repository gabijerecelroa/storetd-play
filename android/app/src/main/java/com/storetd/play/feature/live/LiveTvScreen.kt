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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.storetd.play.core.cache.EpgMemoryCache
import com.storetd.play.core.epg.EpgMatcher
import com.storetd.play.core.epg.EpgProgram
import com.storetd.play.core.model.Channel
import com.storetd.play.core.storage.LocalAccount
import com.storetd.play.core.storage.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    onBack: () -> Unit,
    onPlay: (Channel, List<Channel>) -> Unit,
    contentMode: ContentMode = ContentMode.LiveTv,
    viewModel: LiveTvViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var epgPrograms by remember { mutableStateOf(emptyList<EpgProgram>()) }

    LaunchedEffect(contentMode) {
        viewModel.setContentMode(contentMode)
        viewModel.setHideAdultContent(LocalSettings.isAdultContentHidden(context))

        val assignedPlaylist = LocalAccount.getAccount(context).playlistUrl
        if (assignedPlaylist.isNotBlank()) {
            viewModel.loadAssignedPlaylist(assignedPlaylist)
        }

        epgPrograms = withContext(Dispatchers.IO) {
            EpgMemoryCache.getPrograms(context)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        val isCompact = maxWidth < 700.dp

        if (isCompact) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    ContentControls(
                        state = state,
                        mode = contentMode,
                        onSearchChange = viewModel::setSearchQuery,
                        onHideAdultChange = { hidden ->
                            LocalSettings.setAdultContentHidden(context, hidden)
                            viewModel.setHideAdultContent(hidden)
                        },
                        onRefresh = viewModel::refreshPlaylist,
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
                    StatusBlock(state = state, mode = contentMode)
                }

                items(state.visibleChannels) { channel ->
                    ChannelRow(
                        channel = channel,
                        currentProgram = null,
                        nextProgram = null,
                        onPlay = { onPlay(channel, state.visibleChannels) }
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                ) {
                    ContentControls(
                        state = state,
                        mode = contentMode,
                        onSearchChange = viewModel::setSearchQuery,
                        onHideAdultChange = { hidden ->
                            LocalSettings.setAdultContentHidden(context, hidden)
                            viewModel.setHideAdultContent(hidden)
                        },
                        onRefresh = viewModel::refreshPlaylist,
                        onBack = onBack
                    )

                    Spacer(Modifier.height(24.dp))

                    CategoryRow(
                        groups = state.groups,
                        selectedGroup = state.selectedGroup,
                        onSelectGroup = viewModel::selectGroup
                    )
                }

                Spacer(Modifier.width(24.dp))

                Column(modifier = Modifier.weight(1f)) {
                    StatusBlock(state = state, mode = contentMode)

                    Spacer(Modifier.height(16.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.visibleChannels) { channel ->
                            ChannelRow(
                                channel = channel,
                                currentProgram = null,
                                nextProgram = null,
                                onPlay = { onPlay(channel, state.visibleChannels) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentControls(
    state: LiveTvUiState,
    mode: ContentMode,
    onSearchChange: (String) -> Unit,
    onHideAdultChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }
    var pinValue by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = mode.title,
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = mode.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && !state.isFiltering
            ) {
                Text(if (state.isLoading || state.isFiltering) "Sincronizando..." else "Actualizar contenido")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                label = { Text("Buscar contenido o categoría") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
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
                        "Adultos ocultos"
                    } else {
                        "Adultos visibles"
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Volver")
            }
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
                    Text("Ingresa el PIN para mostrar categorías adultas.")
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
        Text("Categorías", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups) { group ->
                FilterChip(
                    selected = group == selectedGroup,
                    onClick = { onSelectGroup(group) },
                    label = {
                        Text(
                            text = group,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusBlock(
    state: LiveTvUiState,
    mode: ContentMode
) {
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

        if (state.loadedFromCache && !state.isLoading && !state.isFiltering) {
            Text(
                text = "Contenido cargado desde memoria. Usa Actualizar contenido si hubo cambios.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
            )
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = "${state.totalVisibleCount} elementos encontrados",
            style = MaterialTheme.typography.titleLarge
        )

        if (!state.isLoading && !state.isFiltering && state.totalVisibleCount == 0 && state.errorMessage == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = mode.emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    currentProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    onPlay: () -> Unit
) {
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
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                currentProgram?.let { program ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Ahora: ${program.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                nextProgram?.let { program ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Próximo ${formatLiveEpgTime(program.startAtMillis)}: ${program.title}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AssistChip(onClick = {}, label = { Text(channel.group) })
                    }
                    item {
                        AssistChip(onClick = {}, label = { Text("Listo") })
                    }
                }
            }

            Button(onClick = onPlay) {
                Text("Ver")
            }
        }
    }
}

private fun formatLiveEpgTime(value: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value))
}
