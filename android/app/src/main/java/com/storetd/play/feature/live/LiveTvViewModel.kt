package com.storetd.play.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storetd.play.core.cache.PlaylistMemoryCache
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
        subtitle = "Cine, estrenos y contenido VOD.",
        emptyMessage = "No se encontraron películas en la lista asignada."
    ),
    Series(
        title = "Series",
        subtitle = "Temporadas, episodios y colecciones.",
        emptyMessage = "No se encontraron series en la lista asignada."
    )
}

data class LiveTvUiState(
    val playlistUrl: String = "",
    val channels: List<Channel> = emptyList(),
    val visibleChannels: List<Channel> = emptyList(),
    val groups: List<String> = listOf("Todos"),
    val totalVisibleCount: Int = 0,
    val contentMode: ContentMode = ContentMode.LiveTv,
    val selectedGroup: String = "Todos",
    val searchQuery: String = "",
    val hideAdultContent: Boolean = true,
    val isLoading: Boolean = false,
    val isFiltering: Boolean = false,
    val loadedFromCache: Boolean = false,
    val errorMessage: String? = null
)

class LiveTvViewModel(
    private val repository: IptvRepository = IptvRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState

    private var filterVersion = 0

    fun setContentMode(mode: ContentMode) {
        val current = _uiState.value

        _uiState.value = current.copy(
            contentMode = mode,
            selectedGroup = "Todos",
            searchQuery = "",
            isFiltering = current.channels.isNotEmpty()
        )

        if (current.channels.isNotEmpty()) {
            refreshVisibleContent()
        }
    }

    fun setPlaylistUrl(value: String) {
        _uiState.value = _uiState.value.copy(
            playlistUrl = value,
            errorMessage = null
        )
    }

    fun setSearchQuery(value: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = value,
            isFiltering = true
        )
        refreshVisibleContent()
    }

    fun setHideAdultContent(value: Boolean) {
        _uiState.value = _uiState.value.copy(
            hideAdultContent = value,
            selectedGroup = "Todos",
            isFiltering = true
        )
        refreshVisibleContent()
    }

    fun selectGroup(group: String) {
        _uiState.value = _uiState.value.copy(
            selectedGroup = group,
            isFiltering = true
        )
        refreshVisibleContent()
    }

    fun loadAssignedPlaylist(url: String) {
        val cleanUrl = url.trim()
        val current = _uiState.value

        if (current.channels.isNotEmpty() && current.playlistUrl == cleanUrl) {
            refreshVisibleContent()
            return
        }

        val cached = PlaylistMemoryCache.get(cleanUrl)

        if (cached != null) {
            _uiState.value = current.copy(
                playlistUrl = cleanUrl,
                channels = cached,
                isLoading = false,
                isFiltering = true,
                loadedFromCache = true,
                errorMessage = null
            )
            refreshVisibleContent()
            return
        }

        loadPlaylistFrom(cleanUrl, forceRefresh = false)
    }

    fun refreshPlaylist() {
        PlaylistMemoryCache.clear()
        loadPlaylistFrom(_uiState.value.playlistUrl, forceRefresh = true)
    }

    private fun loadPlaylistFrom(urlValue: String, forceRefresh: Boolean) {
        val url = urlValue.trim()

        if (!M3uValidator.validateUrl(url)) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isFiltering = false,
                errorMessage = "No hay una lista M3U/M3U8 válida asignada a esta cuenta."
            )
            return
        }

        if (!forceRefresh) {
            val cached = PlaylistMemoryCache.get(url)
            if (cached != null) {
                _uiState.value = _uiState.value.copy(
                    playlistUrl = url,
                    channels = cached,
                    isLoading = false,
                    isFiltering = true,
                    loadedFromCache = true,
                    errorMessage = null
                )
                refreshVisibleContent()
                return
            }
        }

        _uiState.value = _uiState.value.copy(
            playlistUrl = url,
            isLoading = true,
            isFiltering = true,
            loadedFromCache = false,
            errorMessage = null,
            visibleChannels = emptyList(),
            totalVisibleCount = 0
        )

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.loadPlaylistFromUrl(url)
            }.onSuccess { channels ->
                PlaylistMemoryCache.save(url, channels)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    channels = channels,
                    selectedGroup = "Todos",
                    errorMessage = if (channels.isEmpty()) {
                        "La lista asignada no contiene contenido."
                    } else {
                        null
                    }
                )

                refreshVisibleContent()
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isFiltering = false,
                    channels = emptyList(),
                    visibleChannels = emptyList(),
                    totalVisibleCount = 0,
                    errorMessage = throwable.message ?: "No se pudo cargar el contenido asignado."
                )
            }
        }
    }

    private fun refreshVisibleContent() {
        val snapshot = _uiState.value
        val version = ++filterVersion

        viewModelScope.launch(Dispatchers.Default) {
            val adultFiltered = if (snapshot.hideAdultContent) {
                snapshot.channels.filterNot { isAdult(it) }
            } else {
                snapshot.channels
            }

            val modeFiltered = adultFiltered.filter {
                matchesContentMode(it, snapshot.contentMode)
            }

            val groups = listOf("Todos") + modeFiltered
                .asSequence()
                .map { it.group.ifBlank { "Sin categoría" } }
                .distinct()
                .sorted()
                .toList()

            val safeSelectedGroup = if (snapshot.selectedGroup in groups) {
                snapshot.selectedGroup
            } else {
                "Todos"
            }

            val groupFiltered = if (safeSelectedGroup == "Todos") {
                modeFiltered
            } else {
                modeFiltered.filter { it.group == safeSelectedGroup }
            }

            val query = snapshot.searchQuery.trim().lowercase(Locale.getDefault())

            val visible = if (query.isBlank()) {
                groupFiltered
            } else {
                groupFiltered.filter {
                    it.name.lowercase(Locale.getDefault()).contains(query) ||
                        it.group.lowercase(Locale.getDefault()).contains(query) ||
                        it.tvgId.orEmpty().lowercase(Locale.getDefault()).contains(query)
                }
            }

            if (version == filterVersion) {
                _uiState.value = _uiState.value.copy(
                    groups = groups,
                    selectedGroup = safeSelectedGroup,
                    visibleChannels = visible,
                    totalVisibleCount = visible.size,
                    isFiltering = false
                )
            }
        }
    }

    companion object {
        private val adultWords = listOf(
            "adult", "adulto", "xxx", "+18", "18+", "hot", "erotic", "erotico",
            "erótico", "porn", "playboy"
        )

        private val movieWords = listOf(
            "pelicula", "peliculas", "película", "películas", "movie", "movies",
            "cine", "cinema", "film", "films", "estreno", "estrenos", "vod",
            "accion", "acción", "terror", "comedia", "drama", "suspenso"
        )

        private val seriesWords = listOf(
            "serie", "series", "temporada", "season", "episode", "episodio",
            "capitulo", "capítulo", "novela", "novelas", "anime", "tv show", "shows"
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
