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
import java.text.Normalizer
import java.util.Locale

enum class ContentMode(
    val title: String,
    val subtitle: String,
    val emptyMessage: String
) {
    LiveTv(
        title = "TV en vivo",
        subtitle = "Canales en vivo, categorías y zapping.",
        emptyMessage = "No se encontraron canales de TV en vivo."
    ),
    Movies(
        title = "Películas",
        subtitle = "Contenido tipo cine, estrenos y categorías VOD.",
        emptyMessage = "No se encontraron películas en la lista asignada."
    ),
    Series(
        title = "Series",
        subtitle = "Temporadas, episodios y contenido seriado.",
        emptyMessage = "No se encontraron series en la lista asignada."
    )
}

data class LiveTvUiState(
    val playlistUrl: String = "",
    val channels: List<Channel> = emptyList(),
    val contentMode: ContentMode = ContentMode.LiveTv,
    val selectedGroup: String = "Todos",
    val searchQuery: String = "",
    val hideAdultContent: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    private val filteredByAdult: List<Channel>
        get() = if (hideAdultContent) {
            channels.filterNot { isAdult(it) }
        } else {
            channels
        }

    private val filteredByMode: List<Channel>
        get() = filteredByAdult.filter { matchesContentMode(it, contentMode) }

    val groups: List<String>
        get() = listOf("Todos") + filteredByMode
            .map { it.group.ifBlank { "Sin categoria" } }
            .distinct()
            .sorted()

    val visibleChannels: List<Channel>
        get() {
            val byGroup = if (selectedGroup == "Todos") {
                filteredByMode
            } else {
                filteredByMode.filter { it.group == selectedGroup }
            }

            val query = searchQuery.trim().lowercase(Locale.getDefault())

            return if (query.isBlank()) {
                byGroup
            } else {
                byGroup.filter {
                    it.name.lowercase(Locale.getDefault()).contains(query) ||
                        it.group.lowercase(Locale.getDefault()).contains(query) ||
                        it.tvgId.orEmpty().lowercase(Locale.getDefault()).contains(query)
                }
            }
        }

    val totalVisibleCount: Int
        get() = visibleChannels.size

    companion object {
        private val adultWords = listOf(
            "adult", "adulto", "xxx", "+18", "18+", "hot", "erotic", "erotico",
            "erótico", "porn", "playboy"
        )

        private val movieWords = listOf(
            "pelicula", "peliculas", "película", "películas", "movie", "movies",
            "cine", "cinema", "film", "films", "estreno", "estrenos", "vod",
            "accion", "acción", "terror", "comedia", "drama", "suspenso",
            "4k", "60 fps"
        )

        private val seriesWords = listOf(
            "serie", "series", "temporada", "season", "episode", "episodio",
            "capitulo", "capítulo", "novela", "novelas", "anime", "show",
            "tv show", "shows"
        )

        fun isAdult(channel: Channel): Boolean {
            val text = normalize("${channel.name} ${channel.group}")
            return adultWords.any { text.contains(normalize(it)) }
        }

        fun matchesContentMode(channel: Channel, mode: ContentMode): Boolean {
            val text = normalize("${channel.name} ${channel.group}")

            val isSeries = seriesWords.any { text.contains(normalize(it)) }
            val isMovie = movieWords.any { text.contains(normalize(it)) } && !isSeries

            return when (mode) {
                ContentMode.LiveTv -> !isMovie && !isSeries
                ContentMode.Movies -> isMovie
                ContentMode.Series -> isSeries
            }
        }

        private fun normalize(value: String): String {
            return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .lowercase(Locale.getDefault())
                .replace("&", " y ")
                .replace(Regex("[^a-z0-9+ ]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}

class LiveTvViewModel(
    private val repository: IptvRepository = IptvRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState

    fun setContentMode(mode: ContentMode) {
        _uiState.value = _uiState.value.copy(
            contentMode = mode,
            selectedGroup = "Todos",
            searchQuery = ""
        )
    }

    fun setPlaylistUrl(value: String) {
        _uiState.value = _uiState.value.copy(
            playlistUrl = value,
            errorMessage = null
        )
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

    fun loadAssignedPlaylist(url: String) {
        _uiState.value = _uiState.value.copy(
            playlistUrl = url,
            errorMessage = null
        )
        loadPlaylistFrom(url)
    }

    fun loadPlaylist() {
        loadPlaylistFrom(_uiState.value.playlistUrl)
    }

    private fun loadPlaylistFrom(urlValue: String) {
        val url = urlValue.trim()

        if (!M3uValidator.validateUrl(url)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "No hay una lista M3U/M3U8 válida asignada a esta cuenta."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.loadPlaylistFromUrl(url)
            }.onSuccess { channels ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    channels = channels,
                    selectedGroup = "Todos",
                    errorMessage = if (channels.isEmpty()) {
                        "La lista asignada no contiene canales."
                    } else {
                        null
                    }
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = throwable.message ?: "No se pudo cargar el contenido asignado."
                )
            }
        }
    }
}
