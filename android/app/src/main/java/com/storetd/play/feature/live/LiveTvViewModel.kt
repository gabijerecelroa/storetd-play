package com.storetd.play.feature.live

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storetd.play.core.cache.PlaylistDiskCache
import com.storetd.play.core.cache.PlaylistMemoryCache
import com.storetd.play.core.model.Channel
import com.storetd.play.core.network.OptimizedContentApi
import com.storetd.play.core.parser.M3uValidator
import com.storetd.play.core.preload.PlaylistPreloader
import com.storetd.play.core.repository.IptvRepository
import com.storetd.play.core.storage.LocalAccount
import com.storetd.play.core.storage.LocalSettings
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



// STORETD_LIVE_GROUP_ORDER_START
private fun storeTdNormalizeLiveGroup(value: String): String {
    return value
        .lowercase(Locale.getDefault())
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("∆", "a")
        .replace("ę", "e")
        .replace("lna", "ina")
        .trim()
}

private fun storeTdLiveGroupPriority(group: String): Int {
    val text = storeTdNormalizeLiveGroup(group)

    return when {
        text.contains("paraguay") || text.contains("py |") -> 0

        text.contains("argentina") ||
            text.contains("argentlna") ||
            text.contains("gran hermano argentina") ||
            text.contains("ar |") -> 1

        text.contains("deporte") ||
            text.contains("sports") ||
            text.contains("futbol") ||
            text.contains("fútbol") ||
            text.contains("ppv") ||
            text.contains("evento") ||
            text.contains("espn") ||
            text.contains("e\$pn") ||
            text.contains("fox sports") ||
            text.contains("tyc") ||
            text.contains("tigo sports") ||
            text.contains("dazn") ||
            text.contains("formula 1") ||
            text.contains("motogp") ||
            text.contains("libertadores") ||
            text.contains("sudamericana") -> 10

        text.contains("cine") ||
            text.contains("pelicula") ||
            text.contains("peliculas") ||
            text.contains("películas") ||
            text.contains("cinema") ||
            text.contains("movie") ||
            text.contains("hbo") ||
            text.contains("tnt") ||
            text.contains("warner") ||
            text.contains("space") ||
            text.contains("paramount") ||
            text.contains("universal") ||
            text.contains("star channel") ||
            text.contains("sony") ||
            text.contains("fx") ||
            text.contains("amc") ||
            text.contains("axn") -> 20

        text.contains("24/7") ||
            text.contains("24-7") ||
            text.contains("24 7") -> 30

        else -> 100
    }
}

private val storeTdLiveGroupComparator = Comparator<String> { first, second ->
    val firstPriority = storeTdLiveGroupPriority(first)
    val secondPriority = storeTdLiveGroupPriority(second)

    if (firstPriority != secondPriority) {
        firstPriority - secondPriority
    } else {
        first.lowercase(Locale.getDefault())
            .compareTo(second.lowercase(Locale.getDefault()))
    }
}
// STORETD_LIVE_GROUP_ORDER_END

data class LiveTvUiState(
    val playlistUrl: String = "",
    val channels: List<Channel> = emptyList(),
    val visibleChannels: List<Channel> = emptyList(),
    val groups: List<String> = listOf("Todos"),
    val groupItems: Map<String, List<Channel>> = emptyMap(),
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
            selectedGroup = "Todos",
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
        val current = _uiState.value

        if (current.groupItems.isNotEmpty()) {
            val safeGroup = if (current.groupItems.containsKey(group)) group else "Todos"
            val visible = current.groupItems[safeGroup].orEmpty()

            _uiState.value = current.copy(
                selectedGroup = safeGroup,
                visibleChannels = visible.take(MAX_VISIBLE_ITEMS),
                totalVisibleCount = visible.size,
                isFiltering = false
            )

            saveScreenState(_uiState.value)
            return
        }

        _uiState.value = current.copy(
            selectedGroup = group,
            isFiltering = true
        )
        refreshVisibleContent()
    }

    fun loadAssignedPlaylist(context: Context, url: String) {
        val appContext = context.applicationContext
        val cleanUrl = url.trim()
        val mode = _uiState.value.contentMode
        val key = cacheKey(cleanUrl, mode)
        val account = LocalAccount.getAccount(appContext)
        val isMagmaLiteLiveAccount = mode == ContentMode.LiveTv &&
            (
                account.activationCode.trim() == "253698" ||
                    cleanUrl.contains("magma", ignoreCase = true)
            )

        if (isMagmaLiteLiveAccount) {
            val sectionKey = sectionCacheKey(cleanUrl, mode)

            screenStateCache[key]?.let { cachedState ->
                if (cachedState.channels.isNotEmpty()) {
                    _uiState.value = cachedState.copy(
                        isLoading = false,
                        isFiltering = false,
                        loadedFromCache = true,
                        errorMessage = null
                    )

                    maybeRefreshMagmaLiveCatalog(
                        context = appContext,
                        urlValue = cleanUrl
                    )
                    return
                }
            }

            val sectionDiskCached = PlaylistDiskCache.load(appContext, sectionKey)

            if (sectionDiskCached.isNotEmpty()) {
                val cachedState = buildCachedScreenState(
                    url = cleanUrl,
                    channels = sectionDiskCached,
                    mode = mode,
                    hideAdultContent = _uiState.value.hideAdultContent
                ).copy(
                    loadedFromCache = true,
                    isLoading = false,
                    isFiltering = false,
                    errorMessage = null
                )

                _uiState.value = cachedState
                saveScreenState(cachedState)

                maybeRefreshMagmaLiveCatalog(
                    context = appContext,
                    urlValue = cleanUrl
                )
                return
            }

            loadOptimizedLiveFromBackend(
                context = appContext,
                urlValue = cleanUrl,
                forceBackendRefresh = true
            )
            return
        }

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

        // 1) Sección ya filtrada desde disco: lo más rápido.
        val sectionDiskCached = PlaylistDiskCache.load(
            appContext,
            sectionCacheKey(cleanUrl, mode)
        )

        if (sectionDiskCached.isNotEmpty()) {
            val cachedState = buildCachedScreenState(
                url = cleanUrl,
                channels = sectionDiskCached,
                mode = mode,
                hideAdultContent = false
            )

            _uiState.value = cachedState
            saveScreenState(cachedState)
            return
        }

        // 2) Memoria RAM.
        PlaylistMemoryCache.get(cleanUrl)?.let { memoryCached ->
            if (memoryCached.isNotEmpty()) {
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
        }

        // 3) Lista completa guardada en disco.
        val diskCached = PlaylistDiskCache.load(appContext, cleanUrl)

        if (diskCached.isNotEmpty()) {
            PlaylistMemoryCache.save(cleanUrl, diskCached)

            saveSectionDiskCaches(
                context = appContext,
                url = cleanUrl,
                channels = diskCached,
                hideAdultContent = _uiState.value.hideAdultContent
            )

            val cachedState = buildCachedScreenState(
                url = cleanUrl,
                channels = diskCached,
                mode = mode,
                hideAdultContent = _uiState.value.hideAdultContent
            )

            _uiState.value = cachedState
            saveScreenState(cachedState)
            return
        }

        if (mode == ContentMode.LiveTv) {
            loadOptimizedLiveFromBackend(appContext, cleanUrl)
            return
        }

        // 4) Último respaldo: sistema local anterior.
        loadPlaylistFrom(appContext, cleanUrl, forceRefresh = false)
    }

    fun refreshPlaylist(context: Context) {
        val url = _uiState.value.playlistUrl
        val key = cacheKey(url, _uiState.value.contentMode)

        screenStateCache.remove(key)
        ContentMode.values().forEach { mode ->
            screenStateCache.remove(cacheKey(url, mode))
            PlaylistDiskCache.clear(context, sectionCacheKey(url, mode))
        }

        PlaylistPreloader.clear(url)
        PlaylistMemoryCache.clear(url)
        PlaylistDiskCache.clear(context, url)

        if (_uiState.value.contentMode == ContentMode.LiveTv) {
            loadOptimizedLiveFromBackend(
                context = context.applicationContext,
                urlValue = url,
                forceBackendRefresh = true
            )
            return
        }

        loadPlaylistFrom(context, url, forceRefresh = true)
    }

    private fun maybeRefreshMagmaLiveCatalog(
        context: Context,
        urlValue: String
    ) {
        val appContext = context.applicationContext
        val cleanUrl = urlValue.trim()

        viewModelScope.launch(Dispatchers.IO) {
            val account = LocalAccount.getAccount(appContext)
            val activationCode = account.activationCode.trim()

            if (activationCode.isBlank()) return@launch

            val localNeedsDailyRefresh = shouldRefreshMagmaLiveCatalog(appContext)

            val remoteVersion = OptimizedContentApi.loadMagmaLiveCatalogVersion(
                activationCode = activationCode
            )

            val localVersion = getMagmaLiveCatalogLastRefreshAt(appContext)

            val remoteNeedsRefresh = remoteVersion > 0L && remoteVersion > localVersion

            if (localNeedsDailyRefresh || remoteNeedsRefresh) {
                loadOptimizedLiveFromBackend(
                    context = appContext,
                    urlValue = cleanUrl,
                    forceBackendRefresh = true
                )
            }
        }
    }


    private fun loadOptimizedLiveFromBackend(
        context: Context,
        urlValue: String,
        forceBackendRefresh: Boolean = false
    ) {
        val appContext = context.applicationContext
        val url = urlValue.trim()
        val accountSnapshot = LocalAccount.getAccount(appContext)
        val isMagmaLiteLiveAccount =
            accountSnapshot.activationCode.trim() == "253698" ||
                url.contains("magma", ignoreCase = true)

        if (loadInProgress) return

        loadInProgress = true
        _uiState.value = _uiState.value.copy(isLoading = true)
        _uiState.value = _uiState.value.copy(isLoading = true)

        val currentBeforeLoad = _uiState.value
        val hasVisibleCache = currentBeforeLoad.channels.isNotEmpty()

        _uiState.value = currentBeforeLoad.copy(
            playlistUrl = url,
            isLoading = !hasVisibleCache,
            isFiltering = false,
            loadedFromCache = false,
            errorMessage = null,
            visibleChannels = if (hasVisibleCache) currentBeforeLoad.visibleChannels else emptyList(),
            groupItems = if (hasVisibleCache) currentBeforeLoad.groupItems else emptyMap(),
            totalVisibleCount = if (hasVisibleCache) currentBeforeLoad.totalVisibleCount else 0
        )

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val account = accountSnapshot
                val activationCode = account.activationCode.trim()

                if (activationCode.isBlank()) {
                    emptyList()
                } else {
                    if (forceBackendRefresh && !isMagmaLiteLiveAccount) {
                        runCatching {
                            OptimizedContentApi.refreshContent(
                                activationCode = activationCode,
                                async = false
                            )
                        }
                    }

                    OptimizedContentApi.loadSection(
                        activationCode = activationCode,
                        section = "live",
                        includeAdult = !_uiState.value.hideAdultContent
                    )
                }
            }.onSuccess { channels ->
                loadInProgress = false

                if (channels.isNotEmpty()) {
                    LocalSettings.markContentSyncSuccess(
                        context = appContext,
                        message = "TV en vivo sincronizada."
                    )

                    PlaylistDiskCache.save(
                        context = appContext,
                        url = sectionCacheKey(url, ContentMode.LiveTv),
                        channels = channels
                    )

                    if (isMagmaLiteLiveAccount) {
                        markMagmaLiveCatalogRefreshed(appContext)
                    }

                    _uiState.value = _uiState.value.copy(
                        playlistUrl = url,
                        isLoading = false,
                        channels = channels,
                        selectedGroup = "Todos",
                        searchQuery = "",
                        loadedFromCache = false,
                        errorMessage = null
                    )

                    refreshVisibleContent()
                    return@onSuccess
                }

                if (isMagmaLiteLiveAccount) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isFiltering = false,
                        errorMessage = if (_uiState.value.channels.isEmpty()) {
                            "No se pudo cargar TV en vivo. Intenta nuevamente."
                        } else {
                            null
                        }
                    )
                    return@onSuccess
                }

                loadPlaylistFrom(appContext, url, forceRefresh = true)
            }.onFailure {
                loadInProgress = false

                if (isMagmaLiteLiveAccount) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isFiltering = false,
                        loadedFromCache = _uiState.value.channels.isNotEmpty(),
                        errorMessage = if (_uiState.value.channels.isEmpty()) {
                            "No se pudo sincronizar TV en vivo."
                        } else {
                            null
                        }
                    )
                    return@onFailure
                }

                loadPlaylistFrom(appContext, url, forceRefresh = true)
            }
        }
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

        loadInProgress = true
        _uiState.value = _uiState.value.copy(isLoading = true)
        _uiState.value = _uiState.value.copy(isLoading = true)

        val currentBeforeLoad = _uiState.value
        val hasVisibleCache = currentBeforeLoad.channels.isNotEmpty()

        _uiState.value = currentBeforeLoad.copy(
            playlistUrl = url,
            isLoading = !hasVisibleCache,
            isFiltering = false,
            loadedFromCache = false,
            errorMessage = null,
            visibleChannels = if (hasVisibleCache) currentBeforeLoad.visibleChannels else emptyList(),
            groupItems = if (hasVisibleCache) currentBeforeLoad.groupItems else emptyMap(),
            totalVisibleCount = if (hasVisibleCache) currentBeforeLoad.totalVisibleCount else 0
        )

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (!forceRefresh) {
                    PlaylistPreloader.awaitIfRunning(context, url)?.let { preloaded ->
                        if (preloaded.isNotEmpty()) return@runCatching preloaded
                    }

                    PlaylistMemoryCache.get(url)?.let { memoryCached ->
                        if (memoryCached.isNotEmpty()) return@runCatching memoryCached
                    }

                    val diskCached = PlaylistDiskCache.load(context, url)
                    if (diskCached.isNotEmpty()) {
                        PlaylistMemoryCache.save(url, diskCached)
                        return@runCatching diskCached
                    }
                }

                withTimeout(60000L) {
                    repository.loadPlaylistFromUrl(url)
                }
            }.onSuccess { channels ->
                loadInProgress = false

                if (channels.isNotEmpty()) {
                    PlaylistMemoryCache.save(url, channels)
                    PlaylistDiskCache.save(context, url, channels)
                    saveSectionDiskCaches(
                        context = context,
                        url = url,
                        channels = channels,
                        hideAdultContent = _uiState.value.hideAdultContent
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    channels = channels,
                    selectedGroup = "Todos",
                    loadedFromCache = !forceRefresh,
                    errorMessage = if (channels.isEmpty()) {
                        "La lista asignada no contiene contenido."
                    } else {
                        null
                    }
                )

                refreshVisibleContent()
            }.onFailure {
                loadInProgress = false

                val current = _uiState.value

                if (current.channels.isNotEmpty()) {
                    _uiState.value = current.copy(
                        isLoading = false,
                        isFiltering = false,
                        loadedFromCache = true,
                        errorMessage = "No se pudo actualizar. Se muestra el contenido guardado."
                    )
                } else {
                    _uiState.value = current.copy(
                        isLoading = false,
                        isFiltering = false,
                        channels = emptyList(),
                        visibleChannels = emptyList(),
                        groupItems = emptyMap(),
                        totalVisibleCount = 0,
                        errorMessage = "No se pudo sincronizar la lista. Toca Actualizar contenido o revisa la lista asignada."
                    )
                }
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
                groupItems = mapOf("Todos" to emptyList()),
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

            val query = snapshot.searchQuery.trim().lowercase(Locale.getDefault())

            val queryFiltered = if (query.isBlank()) {
                modeFiltered
            } else {
                modeFiltered.filter {
                    it.name.lowercase(Locale.getDefault()).contains(query) ||
                        it.group.lowercase(Locale.getDefault()).contains(query) ||
                        it.tvgId.orEmpty().lowercase(Locale.getDefault()).contains(query)
                }
            }

            val cleanedContent = when (snapshot.contentMode) {
                ContentMode.Movies -> queryFiltered
                    .distinctBy { channel ->
                        channel.streamUrl.ifBlank {
                            "${channel.name}|${channel.group}"
                        }
                    }
                    .sortedBy { it.name.lowercase(Locale.getDefault()) }

                else -> queryFiltered
            }

            val grouped = cleanedContent.groupBy {
                it.group.ifBlank { "Sin categoría" }
            }

            val groupNames = listOf("Todos") + grouped.keys.sortedWith(storeTdLiveGroupComparator)

            val groupMap = LinkedHashMap<String, List<Channel>>()
            groupMap["Todos"] = cleanedContent

            grouped.keys.sortedWith(storeTdLiveGroupComparator).forEach { group ->
                groupMap[group] = grouped[group].orEmpty()
            }

            val safeSelectedGroup = if (snapshot.selectedGroup in groupNames) {
                snapshot.selectedGroup
            } else {
                "Todos"
            }

            val selectedItems = groupMap[safeSelectedGroup].orEmpty()

            if (version == filterVersion) {
                val nextState = _uiState.value.copy(
                    groups = groupNames,
                    groupItems = groupMap,
                    selectedGroup = safeSelectedGroup,
                    visibleChannels = selectedItems.take(MAX_VISIBLE_ITEMS),
                    totalVisibleCount = selectedItems.size,
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
        private const val MAX_VISIBLE_ITEMS = 500000
        private const val MAX_SCREEN_CACHE = 12

        private val screenStateCache = LinkedHashMap<String, LiveTvUiState>()

        fun optimizedSectionName(mode: ContentMode): String {
            return when (mode) {
                ContentMode.LiveTv -> "live"
                ContentMode.Movies -> "movies"
                ContentMode.Series -> "series"
            }
        }

        fun sectionCacheKey(url: String, mode: ContentMode): String {
            return "section|${mode.name}|${url.trim()}"
        }

        fun saveSectionDiskCaches(
            context: Context,
            url: String,
            channels: List<Channel>,
            hideAdultContent: Boolean = true
        ) {
            val cleanUrl = url.trim()
            if (cleanUrl.isBlank() || channels.isEmpty()) return

            ContentMode.values().forEach { mode ->
                val sectionChannels = buildSectionChannels(
                    url = cleanUrl,
                    channels = channels,
                    mode = mode,
                    hideAdultContent = hideAdultContent
                )

                if (sectionChannels.isNotEmpty()) {
                    PlaylistDiskCache.save(
                        context = context,
                        url = sectionCacheKey(cleanUrl, mode),
                        channels = sectionChannels
                    )

                    val state = buildCachedScreenState(
                        url = cleanUrl,
                        channels = sectionChannels,
                        mode = mode,
                        hideAdultContent = false
                    )

                    screenStateCache[cacheKey(cleanUrl, mode)] = state
                }
            }

            while (screenStateCache.size > MAX_SCREEN_CACHE) {
                val firstKey = screenStateCache.keys.firstOrNull() ?: break
                screenStateCache.remove(firstKey)
            }
        }

        private fun buildSectionChannels(
            url: String,
            channels: List<Channel>,
            mode: ContentMode,
            hideAdultContent: Boolean
        ): List<Channel> {
            val adultFiltered = if (hideAdultContent) {
                channels.filterNot { isAdult(it) }
            } else {
                channels
            }

            val isProxySection = url.contains("/playlist/proxy", ignoreCase = true)

            val modeFiltered = if (isProxySection) {
                adultFiltered
            } else {
                adultFiltered.filter { matchesContentMode(it, mode) }
            }

            return when (mode) {
                ContentMode.Movies -> modeFiltered
                    .distinctBy { channel ->
                        channel.streamUrl.ifBlank {
                            "${channel.name}|${channel.group}"
                        }
                    }
                    .sortedBy { it.name.lowercase(Locale.getDefault()) }

                else -> modeFiltered
            }
        }


        fun warmScreenStateCaches(
            url: String,
            channels: List<Channel>,
            hideAdultContent: Boolean = true
        ) {
            val cleanUrl = url.trim()
            if (cleanUrl.isBlank() || channels.isEmpty()) return

            ContentMode.entries.forEach { mode ->
                val cachedState = buildCachedScreenState(
                    url = cleanUrl,
                    channels = channels,
                    mode = mode,
                    hideAdultContent = hideAdultContent
                )

                synchronized(screenStateCache) {
                    screenStateCache[cacheKey(cleanUrl, mode)] = cachedState

                    while (screenStateCache.size > MAX_SCREEN_CACHE) {
                        val firstKey = screenStateCache.keys.firstOrNull() ?: break
                        screenStateCache.remove(firstKey)
                    }
                }
            }
        }

        private fun buildCachedScreenState(
            url: String,
            channels: List<Channel>,
            mode: ContentMode,
            hideAdultContent: Boolean
        ): LiveTvUiState {
            val adultFiltered = if (hideAdultContent) {
                channels.filterNot { isAdult(it) }
            } else {
                channels
            }

            val isProxySection = url.contains("/playlist/proxy", ignoreCase = true)

            val modeFiltered = if (isProxySection) {
                adultFiltered
            } else {
                adultFiltered.filter { matchesContentMode(it, mode) }
            }

            val cleanedContent = when (mode) {
                ContentMode.Movies -> modeFiltered
                    .distinctBy { channel ->
                        channel.streamUrl.ifBlank {
                            "${channel.name}|${channel.group}"
                        }
                    }
                    .sortedBy { it.name.lowercase(Locale.getDefault()) }

                else -> modeFiltered
            }

            val grouped = cleanedContent.groupBy {
                it.group.ifBlank { "Sin categoría" }
            }

            val groupNames = listOf("Todos") + grouped.keys.sortedWith(storeTdLiveGroupComparator)

            val groupMap = LinkedHashMap<String, List<Channel>>()
            groupMap["Todos"] = cleanedContent

            grouped.keys.sortedWith(storeTdLiveGroupComparator).forEach { group ->
                groupMap[group] = grouped[group].orEmpty()
            }

            val selectedItems = groupMap["Todos"].orEmpty()

            return LiveTvUiState(
                playlistUrl = url,
                channels = channels,
                visibleChannels = selectedItems.take(MAX_VISIBLE_ITEMS),
                groups = groupNames,
                groupItems = groupMap,
                totalVisibleCount = selectedItems.size,
                contentMode = mode,
                selectedGroup = "Todos",
                searchQuery = "",
                hideAdultContent = hideAdultContent,
                isLoading = false,
                isFiltering = false,
                loadedFromCache = true,
                errorMessage = null
            )
        }


        private const val MAGMA_LIVE_PREFS = "magma_live_catalog_cache"
        private const val MAGMA_LIVE_LAST_REFRESH_AT = "last_refresh_at"

        private fun markMagmaLiveCatalogRefreshed(context: Context) {
            context.applicationContext
                .getSharedPreferences(MAGMA_LIVE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(MAGMA_LIVE_LAST_REFRESH_AT, System.currentTimeMillis())
                .apply()
        }

        private fun getMagmaLiveCatalogLastRefreshAt(context: Context): Long {
            return context.applicationContext
                .getSharedPreferences(MAGMA_LIVE_PREFS, Context.MODE_PRIVATE)
                .getLong(MAGMA_LIVE_LAST_REFRESH_AT, 0L)
        }

        private fun shouldRefreshMagmaLiveCatalog(context: Context): Boolean {
            val prefs = context.applicationContext.getSharedPreferences(
                MAGMA_LIVE_PREFS,
                Context.MODE_PRIVATE
            )
            val lastRefreshAt = prefs.getLong(MAGMA_LIVE_LAST_REFRESH_AT, 0L)

            if (lastRefreshAt <= 0L) return true

            val now = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = now
                set(java.util.Calendar.HOUR_OF_DAY, 4)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val todayDawn = calendar.timeInMillis

            return now >= todayDawn && lastRefreshAt < todayDawn
        }

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

        private fun isMagmaLiveChannel(channel: Channel): Boolean {
            val cleanUrl = channel.streamUrl.lowercase(Locale.getDefault())
            return cleanUrl.contains("/magma-lite/live/") ||
                cleanUrl.contains("tvcluboficial.com") ||
                cleanUrl.contains("m3uts.xyz")
        }

        fun matchesContentMode(channel: Channel, mode: ContentMode): Boolean {
            if (isMagmaLiveChannel(channel)) {
                return mode == ContentMode.LiveTv
            }


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

            val streamUrlText = normalize(channel.streamUrl)

            if (
                streamUrlText.contains("/xtream-lite/live/") ||
                streamUrlText.contains("/magma-lite/live/") ||
                streamUrlText.contains("/live/")
            ) {
                return mode == ContentMode.LiveTv
            }

            if (
                streamUrlText.contains("/xtream-lite/movie/") ||
                streamUrlText.contains("/magma-lite/movie/")
            ) {
                return mode == ContentMode.Movies
            }

            if (
                streamUrlText.contains("/xtream-lite/series/") ||
                streamUrlText.contains("/magma-lite/series/")
            ) {
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
