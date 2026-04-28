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
    val searchQuery: String = "",
    val hideAdultContent: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    private val filteredByAdult: List<Channel>
        get() = if (hideAdultContent) channels.filterNot { isAdult(it) } else channels

    val groups: List<String>
        get() = listOf("Todos") + filteredByAdult.map { it.group }.distinct().sorted()

    val visibleChannels: List<Channel>
        get() {
            val byGroup = if (selectedGroup == "Todos") {
                filteredByAdult
            } else {
                filteredByAdult.filter { it.group == selectedGroup }
            }

            val query = searchQuery.trim().lowercase()

            return if (query.isBlank()) {
                byGroup
            } else {
                byGroup.filter {
                    it.name.lowercase().contains(query) ||
                        it.group.lowercase().contains(query) ||
                        it.tvgId.orEmpty().lowercase().contains(query)
                }
            }
        }

    val totalVisibleCount: Int
        get() = visibleChannels.size

    companion object {
        private val adultWords = listOf(
            "adult",
            "adulto",
            "xxx",
            "+18",
            "18+",
            "hot",
            "erotic",
            "erotico",
            "erótico",
            "porn"
        )

        fun isAdult(channel: Channel): Boolean {
            val text = "${channel.name} ${channel.group}".lowercase()
            return adultWords.any { text.contains(it) }
        }
    }
}

class LiveTvViewModel(
    private val repository: IptvRepository = IptvRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState

    fun setPlaylistUrl(value: String) {
        _uiState.value = _uiState.value.copy(playlistUrl = value, errorMessage = null)
    }

    fun setSearchQuery(value: String) {
        _uiState.value = _uiState.value.copy(searchQuery = value)
    }

    fun setHideAdultContent(value: Boolean) {
        _uiState.value = _uiState.value.copy(
            hideAdultContent = value,
            selectedGroup = "Todos"
        )
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
