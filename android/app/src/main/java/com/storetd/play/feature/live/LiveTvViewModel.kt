package com.storetd.play.feature.live

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storetd.play.core.cache.PlaylistMemoryCache
import com.storetd.play.core.cache.PlaylistDiskCache
import com.storetd.play.core.model.Channel
import com.storetd.play.core.parser.M3uValidator
import com.storetd.play.core.repository.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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
    private var loadInProgress = false

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

    fun loadAssignedPlaylist(context: Context, url: String) {
        val cleanUrl = url.trim()
        val current = _uiState.value

        if (current.channels.isNotEmpty() && current.playlistUrl == cleanUrl) {
            refreshVisibleContent()
            return
        }

        if (loadInProgress) {
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

        loadPlaylistFrom(context, cleanUrl, forceRefresh = false)
    }

    fun refreshPlaylist(context: Context) {
        PlaylistMemoryCache.clear()
        PlaylistDiskCache.clear(context, _uiState.value.playlistUrl)
        loadPlaylistFrom(context, _uiState.value.playlistUrl, forceRefresh = true)
    }

    private fun loadPlaylistFrom(context: Context, urlValue: String, forceRefresh: Boolean) {
        val url = urlValue.trim()

        if (!M3uValidator.validateUrl(url)) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isFiltering = false,
                errorMessage = "No hay una lista M3U/M3U8 válida asignada a esta cuenta."
            )
            return
        }

        if (loadInProgress) {
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

            val diskCached = PlaylistDiskCache.load(context, url)
            if (diskCached.isNotEmpty()) {
                PlaylistMemoryCache.save(url, diskCached)

                _uiState.value = _uiState.value.copy(
                    playlistUrl = url,
                    channels = diskCached,
                    isLoading = false,
                    isFiltering = true,
                    loadedFromCache = true,
                    errorMessage = null
                )
                refreshVisibleContent()
                return
            }
        }

        loadInProgress = true

        _uiState.value = _uiState.value.copy(
            playlistUrl = url,
            isLoading = true,
            isFiltering = false,
            loadedFromCache = false,
            errorMessage = null,
            visibleChannels = emptyList(),
            totalVisibleCount = 0
        )

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                withTimeout(60000L) {
                    repository.loadPlaylistFromUrl(url)
                }
            }.onSuccess { channels ->
                loadInProgress = false

                PlaylistMemoryCache.save(url, channels)
                PlaylistDiskCache.save(context, url, channels)

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
                loadInProgress = false

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isFiltering = false,
                    channels = emptyList(),
                    visibleChannels = emptyList(),
                    totalVisibleCount = 0,
                    errorMessage = "No se pudo sincronizar la lista. Toca Actualizar contenido o revisa la lista asignada."
                )
            }
        }
    }

    private fun refreshVisibleContent() {
        val snapshot = _uiState.value
        val version = ++filterVersion

        if (snapshot.channels.isEmpty()) {
            _uiState.value = snapshot.copy(
                visibleChannels = emptyList(),
                groups = listOf("Todos"),
                totalVisibleCount = 0,
                isFiltering = false
            )
            return
        }

        _uiState.value = snapshot.copy(isFiltering = true)

        viewModelScope.launch(Dispatchers.Default) {
            val adultFiltered = if (snapshot.hideAdultContent) {
                snapshot.channels.filterNot { isAdult(it) }
            } else {
                snapshot.channels
            }

            val isProxySection = snapshot.playlistUrl.contains("/playlist/proxy", ignoreCase = true)

            val rawModeFiltered = if (isProxySection) {
                adultFiltered
            } else {
                adultFiltered.filter {
                    matchesContentMode(it, snapshot.contentMode)
                }
            }

            val modeFiltered = if (
                snapshot.contentMode == ContentMode.LiveTv &&
                rawModeFiltered.isEmpty() &&
                adultFiltered.isNotEmpty()
            ) {
                adultFiltered
            } else {
                rawModeFiltered
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
                    visibleChannels = visible.take(600),
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
            val nameText = normalize(channel.name)
            val groupText = normalize(channel.group)

            if (
                groupText.startsWith("tv ") ||
                groupText.startsWith("tv |") ||
                groupText.startsWith("tv 0") ||
                groupText.startsWith("canales") ||
                groupText.contains("en vivo")
            ) {
                return mode == ContentMode.LiveTv
            }

            if (
                groupText.startsWith("pelicula") ||
                groupText.startsWith("peliculas") ||
                groupText.startsWith("movie") ||
                groupText.startsWith("movies") ||
                groupText.startsWith("vod") ||
                groupText.startsWith("cine ")
            ) {
                return mode == ContentMode.Movies
            }

            if (
                groupText.startsWith("serie") ||
                groupText.startsWith("series") ||
                groupText.startsWith("temporada") ||
                groupText.startsWith("novela") ||
                groupText.startsWith("anime")
            ) {
                return mode == ContentMode.Series
            }

            val looksLikeEpisode =
                Regex("\\bs[0-9]{1,2}\\s*e[0-9]{1,3}\\b").containsMatchIn(nameText) ||
                    Regex("\\b[0-9]{1,2}x[0-9]{1,3}\\b").containsMatchIn(nameText)

            if (looksLikeEpisode) {
                return mode == ContentMode.Series
            }

            val isSeries = seriesWords.any { groupText.contains(normalize(it)) }
            val isMovie = !isSeries && movieWords.any { groupText.contains(normalize(it)) }

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
