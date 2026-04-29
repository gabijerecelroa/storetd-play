package com.storetd.play.feature.live

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storetd.play.core.cache.PlaylistDiskCache
import com.storetd.play.core.cache.PlaylistMemoryCache
import com.storetd.play.core.model.Channel
import com.storetd.play.core.parser.M3uValidator
import com.storetd.play.core.preload.PlaylistPreloader
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
        if (current.contentMode == mode) return

        _uiState.value = LiveTvUiState(
            contentMode = mode,
            hideAdultContent = current.hideAdultContent
        )
    }

    fun setPlaylistUrl(value: String) {
        _uiState.value = _uiState.value.copy(
            playlistUrl = value.trim(),
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
        val mode = _uiState.value.contentMode
        val key = cacheKey(cleanUrl, mode)

        screenStateCache[key]?.let { cachedState ->
            if (cachedState.channels.isNotEmpty()) {
                _uiState.value = cachedState.copy(
                    isLoading = false,
                    isFiltering = false,
                    loadedFromCache = true,
                    errorMessage = null
                )
                return
            }
        }

        val memoryCached = PlaylistMemoryCache.get(cleanUrl)
        if (memoryCached != null && memoryCached.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                playlistUrl = cleanUrl,
                channels = memoryCached,
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
        val url = _uiState.value.playlistUrl
        screenStateCache.remove(cacheKey(url, _uiState.value.contentMode))
        PlaylistPreloader.clear(url)
        PlaylistMemoryCache.clear(url)
        PlaylistDiskCache.clear(context, url)
        loadPlaylistFrom(context, url, forceRefresh = true)
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

        if (loadInProgress) return

        if (!forceRefresh) {
            PlaylistMemoryCache.get(url)?.let { cached ->
                if (cached.isNotEmpty()) {
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
            val preloadedChannels = if (!forceRefresh) {
                PlaylistPreloader.awaitIfRunning(context, url)
            } else {
                null
            }

            runCatching {
                preloadedChannels ?: withTimeout(60000L) {
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
            }.onFailure {
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

            val modeFiltered = if (isProxySection) {
                adultFiltered
            } else {
                adultFiltered.filter { matchesContentMode(it, snapshot.contentMode) }
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
                val nextState = _uiState.value.copy(
                    groups = groups,
                    selectedGroup = safeSelectedGroup,
                    visibleChannels = visible.take(600),
                    totalVisibleCount = visible.size,
                    isFiltering = false
                )

                _uiState.value = nextState
                saveScreenState(nextState)
            }
        }
    }

    private fun saveScreenState(state: LiveTvUiState) {
        val url = state.playlistUrl.trim()
        if (url.isBlank() || state.channels.isEmpty()) return

        val key = cacheKey(url, state.contentMode)
        screenStateCache[key] = state.copy(
            isLoading = false,
            isFiltering = false,
            errorMessage = null
        )

        while (screenStateCache.size > MAX_SCREEN_CACHE) {
            val firstKey = screenStateCache.keys.firstOrNull() ?: break
            screenStateCache.remove(firstKey)
        }
    }

    companion object {
        private const val MAX_SCREEN_CACHE = 12
        private val screenStateCache = LinkedHashMap<String, LiveTvUiState>()

        private fun cacheKey(url: String, mode: ContentMode): String {
            return "${mode.name}|${url.trim()}"
        }

        private val adultWords = listOf(
            "adult", "adulto", "adultos", "xxx", "+18", "18+", "hot",
            "erotic", "erotico", "erótica", "erotica", "porno", "porn",
            "playboy", "venus", "private", "sexy", "sex", "sex tv",
            "para adultos", "adults only", "redlight", "brazzers"
        )

        private val movieWords = listOf(
            "pelicula", "peliculas", "película", "películas", "movie",
            "movies", "cine", "cinema", "film", "films", "estreno",
            "estrenos", "vod", "accion", "acción", "terror", "comedia",
            "drama", "suspenso"
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
