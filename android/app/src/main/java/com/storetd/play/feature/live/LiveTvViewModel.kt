package com.storetd.play.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storetd.play.core.model.Channel
import com.storetd.play.core.parser.M3uValidator
import com.storetd.play.core.repository.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LiveTvUiState(
    val playlistUrl: String = "",
    val channels: List<Channel> = emptyList(),
    val selectedGroup: String = "Todos",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val groups: List<String> = listOf("Todos") + channels.map { it.group }.distinct().sorted()
    val visibleChannels: List<Channel> =
        if (selectedGroup == "Todos") channels else channels.filter { it.group == selectedGroup }
}

class LiveTvViewModel(
    private val repository: IptvRepository = IptvRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState

    fun setPlaylistUrl(value: String) {
        _uiState.value = _uiState.value.copy(playlistUrl = value, errorMessage = null)
    }

    fun selectGroup(group: String) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
    }

    fun loadPlaylist() {
        val url = _uiState.value.playlistUrl.trim()

        if (!M3uValidator.validateUrl(url)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Ingresa una URL http o https valida.")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.loadPlaylistFromUrl(url) }
                .onSuccess { channels ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        channels = channels,
                        selectedGroup = "Todos",
                        errorMessage = if (channels.isEmpty()) "La lista no contiene canales." else null
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "No se pudo cargar la lista."
                    )
                }
        }
    }
}
