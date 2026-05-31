package com.storetd.play.feature.live

import com.storetd.play.feature.vod.SeriesTmdbHeader

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.storetd.play.core.network.BrokenLinksApi
import com.storetd.play.core.epg.EpgProgram
import com.storetd.play.core.model.Channel
import com.storetd.play.core.storage.BrokenLinkStore
import com.storetd.play.core.storage.LocalAccount
import com.storetd.play.core.storage.LocalSettings
import com.storetd.play.core.parental.ParentalControl
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.key.onKeyEvent
import com.storetd.play.core.network.OptimizedContentApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.rememberLazyListState

private data class SeriesFolder(
    val key: String,
    val title: String,
    val group: String,
    val logoUrl: String?,
    val episodes: List<Channel>
)

private object PremiumContentSessionCache {
    private val seriesFolders = mutableMapOf<String, List<OptimizedContentApi.SeriesFolderLite>>()
    private val movieCategories = mutableMapOf<String, List<OptimizedContentApi.MovieCategoryLite>>()

    fun key(
        activationCode: String,
        includeAdult: Boolean
    ): String = activationCode.trim().uppercase(Locale.getDefault()) + "|adult=" + includeAdult

    fun getSeriesFolders(key: String): List<OptimizedContentApi.SeriesFolderLite>? =
        seriesFolders[key]?.takeIf { it.isNotEmpty() }

    fun putSeriesFolders(key: String, value: List<OptimizedContentApi.SeriesFolderLite>) {
        if (value.isNotEmpty()) {
            seriesFolders[key] = value
        }
    }

    fun getMovieCategories(key: String): List<OptimizedContentApi.MovieCategoryLite>? =
        movieCategories[key]?.takeIf { it.isNotEmpty() }

    fun putMovieCategories(key: String, value: List<OptimizedContentApi.MovieCategoryLite>) {
        if (value.isNotEmpty()) {
            movieCategories[key] = value
        }
    }

    fun clearForCode(activationCode: String) {
        val prefix = activationCode.trim().uppercase(Locale.getDefault()) + "|"
        seriesFolders.keys.removeAll { it.startsWith(prefix) }
        movieCategories.keys.removeAll { it.startsWith(prefix) }
    }
}

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

    var selectedSeriesKey by remember(contentMode) { mutableStateOf<String?>(null) }
    var selectedSeriesGroup by remember(contentMode) { mutableStateOf<String?>(null) }
    var selectedMovieCategoryKey by remember(contentMode) { mutableStateOf<String?>(null) }
    var lastSeriesFocusKey by remember(contentMode) { mutableStateOf<String?>(null) }
    var lastMovieCategoryFocusKey by remember(contentMode) { mutableStateOf<String?>(null) }
    var showLazySearch by remember(contentMode) { mutableStateOf(false) }
    var lazySearchQuery by remember(contentMode) { mutableStateOf("") }
    var lazyRefreshToken by remember(contentMode) { mutableStateOf(0) }
    var refreshMessage by remember(contentMode) { mutableStateOf<String?>(null) }

    var lazySeriesFolders by remember(contentMode) {
        mutableStateOf<List<OptimizedContentApi.SeriesFolderLite>>(emptyList())
    }
    var lazySeriesEpisodes by remember(contentMode, selectedSeriesKey) {
        mutableStateOf<List<Channel>>(emptyList())
    }
    var isLazySeriesLoading by remember(contentMode, selectedSeriesKey) {
        mutableStateOf(false)
    }

    var lazyMovieCategories by remember(contentMode) {
        mutableStateOf<List<OptimizedContentApi.MovieCategoryLite>>(emptyList())
    }
    var lazyMovieItems by remember(contentMode, selectedMovieCategoryKey) {
        mutableStateOf<List<Channel>>(emptyList())
    }
    var lazyMovieSearchItems by remember(contentMode) {
        mutableStateOf<List<Channel>>(emptyList())
    }
    var isLazyMovieSearchLoading by remember(contentMode) {
        mutableStateOf(false)
    }
    var isLazyMoviesLoading by remember(contentMode, selectedMovieCategoryKey) {
        mutableStateOf(false)
    }

    val refreshScope = rememberCoroutineScope()

    fun refreshCurrentContent() {
        val account = LocalAccount.getAccount(context)
        val activationCode = account.activationCode.trim()

        refreshMessage = "Actualizando contenido..."
        PremiumContentSessionCache.clearForCode(activationCode)
        LocalSettings.markContentSyncStarted(context.applicationContext)

        if (
            activationCode.isNotBlank() &&
            (contentMode == ContentMode.Movies || contentMode == ContentMode.Series)
        ) {
            refreshScope.launch {
                if (contentMode == ContentMode.Movies) {
                    isLazyMoviesLoading = true
                } else {
                    isLazySeriesLoading = true
                }

                val refreshed = runCatching {
                    withContext(Dispatchers.IO) {
                        OptimizedContentApi.refreshContent(
                            activationCode = activationCode,
                            async = false,
                            section = when (contentMode) {
                                ContentMode.Movies -> "movies"
                                ContentMode.Series -> "series"
                                else -> "all"
                            }
                        )
                    }
                }.getOrDefault(false)

                selectedSeriesKey = null
                selectedMovieCategoryKey = null
                lastSeriesFocusKey = null
                lastMovieCategoryFocusKey = null
                showLazySearch = false
                lazySearchQuery = ""
                lazySeriesFolders = emptyList()
                lazySeriesEpisodes = emptyList()
                lazyMovieCategories = emptyList()
                lazyMovieItems = emptyList()

                lazyRefreshToken += 1
                if (refreshed) {
                    LocalSettings.markContentSyncSuccess(
                        context = context.applicationContext,
                        message = if (contentMode == ContentMode.Movies) {
                            "Películas sincronizadas."
                        } else {
                            "Series sincronizadas."
                        }
                    )
                    refreshMessage = "Contenido actualizado."
                } else {
                    LocalSettings.markContentSyncFailed(
                        context = context.applicationContext,
                        message = "No se pudo confirmar la actualización."
                    )
                    refreshMessage = "No se pudo confirmar la actualización. Reintentando carga..."
                }
            }
            return
        }

        viewModel.refreshPlaylist(context)
        refreshMessage = "Actualizando TV en vivo..."
    }

    LaunchedEffect(refreshMessage) {
        if (refreshMessage != null) {
            delay(3000)
            refreshMessage = null
        }
    }

    fun refreshCurrentContentScreen() {
        selectedSeriesKey = null
        selectedSeriesGroup = null
        selectedMovieCategoryKey = null
        lastSeriesFocusKey = null
        lastMovieCategoryFocusKey = null
        showLazySearch = false
        lazySearchQuery = ""

        lazySeriesFolders = emptyList()
        lazySeriesEpisodes = emptyList()
        lazyMovieCategories = emptyList()
        lazyMovieItems = emptyList()
        lazyMovieSearchItems = emptyList()

        isLazySeriesLoading = contentMode == ContentMode.Series
        isLazyMoviesLoading = contentMode == ContentMode.Movies
        isLazyMovieSearchLoading = false

        lazyRefreshToken += 1
        viewModel.refreshPlaylist(context)
    }

    BackHandler(enabled = true) {
        when {
            selectedSeriesKey != null -> {
                selectedSeriesKey = null
                lazySeriesEpisodes = emptyList()
                isLazySeriesLoading = false
            }

            selectedSeriesGroup != null -> {
                selectedSeriesGroup = null
                lazySeriesEpisodes = emptyList()
                isLazySeriesLoading = false
            }

            selectedMovieCategoryKey != null -> {
                selectedMovieCategoryKey = null
                lazyMovieItems = emptyList()
                isLazyMoviesLoading = false
            }

            showLazySearch -> {
                showLazySearch = false
                lazySearchQuery = ""
            }

            else -> onBack()
        }
    }

    LaunchedEffect(Unit) {
        val account = LocalAccount.getAccount(context)
        val activationCode = account.activationCode.trim()

        if (activationCode.isNotBlank()) {
            val hashes = withContext(Dispatchers.IO) {
                BrokenLinksApi.loadHashes(activationCode)
            }

            if (hashes.isNotEmpty()) {
                BrokenLinkStore.replaceGlobalHashes(context, hashes)
            }
        }
    }

    LaunchedEffect(contentMode, lazyRefreshToken) {
        selectedSeriesKey = null
        selectedMovieCategoryKey = null
        lastSeriesFocusKey = null
        lastMovieCategoryFocusKey = null
        showLazySearch = false
        lazySearchQuery = ""
        lazySeriesEpisodes = emptyList()
        lazyMovieItems = emptyList()
        isLazySeriesLoading = false
        isLazyMoviesLoading = false

        viewModel.setContentMode(contentMode)
        viewModel.setHideAdultContent(ParentalControl.isAdultContentHidden(context))

        val account = LocalAccount.getAccount(context)
        val activationCode = account.activationCode.trim()
        val includeAdult = !ParentalControl.isAdultContentHidden(context)
        val sessionCacheKey = PremiumContentSessionCache.key(activationCode, includeAdult)

        if (activationCode.isNotBlank() && contentMode == ContentMode.Series) {
            if (lazyRefreshToken == 0) {
                PremiumContentSessionCache.getSeriesFolders(sessionCacheKey)?.let { cachedFolders ->
                    lazySeriesFolders = cachedFolders
                    isLazySeriesLoading = false
                    return@LaunchedEffect
                }
            }

            isLazySeriesLoading = true
            val folders = withContext(Dispatchers.IO) {
                runCatching {
                    OptimizedContentApi.loadSeriesFoldersLite(
                        activationCode = activationCode,
                        includeAdult = includeAdult
                    )
                }.getOrDefault(emptyList())
            }
            isLazySeriesLoading = false

            if (folders.isNotEmpty()) {
                PremiumContentSessionCache.putSeriesFolders(sessionCacheKey, folders)
                lazySeriesFolders = folders
                return@LaunchedEffect
            }
        }

        if (activationCode.isNotBlank() && contentMode == ContentMode.Movies) {
            if (lazyRefreshToken == 0) {
                PremiumContentSessionCache.getMovieCategories(sessionCacheKey)?.let { cachedCategories ->
                    lazyMovieCategories = cachedCategories
                    isLazyMoviesLoading = false
                    return@LaunchedEffect
                }
            }

            isLazyMoviesLoading = true
            val categories = withContext(Dispatchers.IO) {
                runCatching {
                    OptimizedContentApi.loadMovieCategoriesLite(
                        activationCode = activationCode,
                        includeAdult = includeAdult
                    )
                }.getOrDefault(emptyList())
            }
            isLazyMoviesLoading = false

            if (categories.isNotEmpty()) {
                PremiumContentSessionCache.putMovieCategories(sessionCacheKey, categories)
                lazyMovieCategories = categories
                return@LaunchedEffect
            }
        }

        val assignedPlaylist = buildSectionPlaylistUrl(
            activationCode = account.activationCode,
            fallbackUrl = account.playlistUrl,
            contentMode = contentMode
        )

        if (assignedPlaylist.isNotBlank()) {
            viewModel.loadAssignedPlaylist(context, assignedPlaylist)
        }
    }

    LaunchedEffect(state.selectedGroup) {
        selectedSeriesKey = null
        selectedSeriesGroup = null
        selectedMovieCategoryKey = null
    }

    LaunchedEffect(contentMode, selectedSeriesKey, lazySeriesFolders) {
        val account = LocalAccount.getAccount(context)
        val activationCode = account.activationCode.trim()
        val key = selectedSeriesKey

        if (
            contentMode == ContentMode.Series &&
            key != null &&
            lazySeriesFolders.isNotEmpty() &&
            activationCode.isNotBlank()
        ) {
            isLazySeriesLoading = true
            lazySeriesEpisodes = withContext(Dispatchers.IO) {
                runCatching {
                    OptimizedContentApi.loadSeriesFolderEpisodes(
                        activationCode = activationCode,
                        key = key,
                        includeAdult = !ParentalControl.isAdultContentHidden(context)
                    )
                }.getOrDefault(emptyList())
            }
            isLazySeriesLoading = false
        }
    }

    LaunchedEffect(contentMode, selectedMovieCategoryKey, showLazySearch, lazySearchQuery) {
        val account = LocalAccount.getAccount(context)
        val activationCode = account.activationCode.trim()
        val query = lazySearchQuery.trim()

        if (
            contentMode != ContentMode.Movies ||
            selectedMovieCategoryKey != null ||
            !showLazySearch
        ) {
            return@LaunchedEffect
        }

        if (query.isBlank()) {
            lazyMovieSearchItems = emptyList()
            isLazyMovieSearchLoading = false
            return@LaunchedEffect
        }

        kotlinx.coroutines.delay(250)

        if (activationCode.isNotBlank()) {
            isLazyMovieSearchLoading = true
            lazyMovieSearchItems = withContext(Dispatchers.IO) {
                runCatching {
                    OptimizedContentApi.searchContent(
                        activationCode = activationCode,
                        section = "movies",
                        query = query,
                        includeAdult = !ParentalControl.isAdultContentHidden(context),
                        limit = 100
                    )
                }.getOrDefault(emptyList())
            }
            isLazyMovieSearchLoading = false
        }
    }

    LaunchedEffect(contentMode, selectedMovieCategoryKey, lazyMovieCategories) {
        val account = LocalAccount.getAccount(context)
        val activationCode = account.activationCode.trim()
        val key = selectedMovieCategoryKey

        if (
            contentMode == ContentMode.Movies &&
            key != null &&
            lazyMovieCategories.isNotEmpty() &&
            activationCode.isNotBlank()
        ) {
            isLazyMoviesLoading = true
            lazyMovieItems = withContext(Dispatchers.IO) {
                runCatching {
                    OptimizedContentApi.loadMovieCategoryItems(
                        activationCode = activationCode,
                        key = key,
                        includeAdult = !ParentalControl.isAdultContentHidden(context)
                    )
                }.getOrDefault(emptyList())
            }
            isLazyMoviesLoading = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        val isCompact = maxWidth < 900.dp || maxHeight < 480.dp
        val usingLazyBackendContent =
            contentMode == ContentMode.Series || contentMode == ContentMode.Movies

        val contentListState = rememberLazyListState()

        LaunchedEffect(
            contentMode,
            selectedSeriesKey,
            selectedMovieCategoryKey,
            lastSeriesFocusKey,
            lastMovieCategoryFocusKey,
            lazySeriesFolders.size,
            lazyMovieCategories.size,
            showLazySearch,
            lazySearchQuery,
            isCompact
        ) {
            if (!usingLazyBackendContent || showLazySearch) return@LaunchedEffect

            val baseIndex = if (isCompact) 1 else 0

            if (
                contentMode == ContentMode.Movies &&
                selectedMovieCategoryKey == null &&
                lazyMovieCategories.isNotEmpty()
            ) {
                val movieSearchText = lazySearchQuery.trim().lowercase(Locale.getDefault())
                val visibleMovieCategories = if (movieSearchText.isBlank()) {
                    lazyMovieCategories
                } else {
                    lazyMovieCategories.filter {
                        it.title.lowercase(Locale.getDefault()).contains(movieSearchText)
                    }
                }

                val targetIndex = visibleMovieCategories.indexOfFirst {
                    it.key == lastMovieCategoryFocusKey
                }.let { if (it >= 0) it else 0 }

                contentListState.scrollToItem(baseIndex + 1 + targetIndex)
            }

            if (
                contentMode == ContentMode.Series &&
                selectedSeriesKey == null &&
                lazySeriesFolders.isNotEmpty()
            ) {
                val seriesSearchText = lazySearchQuery.trim().lowercase(Locale.getDefault())
                val visibleSeriesFolders = if (seriesSearchText.isBlank()) {
                    lazySeriesFolders
                } else {
                    lazySeriesFolders.filter {
                        it.title.lowercase(Locale.getDefault()).contains(seriesSearchText) ||
                            it.group.lowercase(Locale.getDefault()).contains(seriesSearchText)
                    }
                }

                val targetIndex = visibleSeriesFolders.indexOfFirst {
                    it.key == lastSeriesFocusKey
                }.let { if (it >= 0) it else 0 }

                contentListState.scrollToItem(baseIndex + 1 + targetIndex)
            }
        }

        if (isCompact) {
            LazyColumn(
                state = contentListState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    PremiumSectionHeader(
                        mode = contentMode,
                        refreshMessage = refreshMessage,
                        isLoading = state.isLoading || state.isFiltering || isLazySeriesLoading || isLazyMoviesLoading
                    )

                    Spacer(Modifier.height(16.dp))

                    PremiumHeroPanel(
                        mode = contentMode,
                        titleOverride = when (contentMode) {
                            ContentMode.Movies -> lazyMovieCategories.firstOrNull { it.key == selectedMovieCategoryKey }?.title
                            ContentMode.Series -> lazySeriesFolders.firstOrNull { it.key == selectedSeriesKey }?.title ?: selectedSeriesGroup?.takeIf { it.isNotBlank() && it != "__all_series__" }
                            ContentMode.LiveTv -> null
                        },
                        subtitleOverride = when (contentMode) {
                            ContentMode.LiveTv -> "${state.visibleChannels.size} canales disponibles"
                            ContentMode.Movies -> if (selectedMovieCategoryKey == null) "${lazyMovieCategories.size} carpetas de películas" else "${lazyMovieItems.size} películas disponibles"
                            ContentMode.Series -> when {
                                selectedSeriesKey != null -> "${lazySeriesEpisodes.size} capítulos disponibles"
                                selectedSeriesGroup == null || selectedSeriesGroup == "__all_series__" -> "${lazySeriesFolders.size} series disponibles"
                                else -> "${lazySeriesFolders.count { it.group == selectedSeriesGroup }} series disponibles"
                            }
                        },
                        channel = when (contentMode) {
                            ContentMode.LiveTv -> state.visibleChannels.firstOrNull()
                            ContentMode.Movies -> lazyMovieItems.firstOrNull() ?: lazyMovieSearchItems.firstOrNull()
                            ContentMode.Series -> lazySeriesEpisodes.firstOrNull()
                        }
                    )
                }

                if (!usingLazyBackendContent) {
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
                }

                contentItems(
                    state = state,
                    contentMode = contentMode,
                    selectedSeriesKey = selectedSeriesKey,
                    selectedSeriesGroup = selectedSeriesGroup,
                    onSelectSeries = {
                        lastSeriesFocusKey = it
                        selectedSeriesKey = it
                    },
                    onClearSeries = { selectedSeriesKey = null },
                    onSelectSeriesGroup = { group ->
                        selectedSeriesKey = null
                        selectedSeriesGroup = group
                    },
                    onClearSeriesGroup = {
                        selectedSeriesKey = null
                        selectedSeriesGroup = null
                    },
                    lazySeriesFolders = lazySeriesFolders,
                    lazySeriesEpisodes = lazySeriesEpisodes,
                    isLazySeriesLoading = isLazySeriesLoading,
                    selectedMovieCategoryKey = selectedMovieCategoryKey,
                    lastSeriesFocusKey = lastSeriesFocusKey,
                    lastMovieCategoryFocusKey = lastMovieCategoryFocusKey,
                    lazyMovieCategories = lazyMovieCategories,
                    lazyMovieItems = lazyMovieItems,
                    lazyMovieSearchItems = lazyMovieSearchItems,
                    isLazyMovieSearchLoading = isLazyMovieSearchLoading,
                    isLazyMoviesLoading = isLazyMoviesLoading,
                    onSelectMovieCategory = {
                        lastMovieCategoryFocusKey = it
                        selectedMovieCategoryKey = it
                    },
                    onClearMovieCategory = { selectedMovieCategoryKey = null },
                    showLazySearch = showLazySearch,
                    lazySearchQuery = lazySearchQuery,
                    onLazySearchQueryChange = { lazySearchQuery = it },
                    onToggleLazySearch = {
                        if (showLazySearch) {
                            lazySearchQuery = ""
                            lazyMovieSearchItems = emptyList()
                            isLazyMovieSearchLoading = false
                        }
                        showLazySearch = !showLazySearch
                    },
                    onPlay = onPlay
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(430.dp)
                        .fillMaxHeight()
                ) {
                    PremiumSectionHeader(
                        mode = contentMode,
                        refreshMessage = refreshMessage,
                        isLoading = state.isLoading || state.isFiltering || isLazySeriesLoading || isLazyMoviesLoading
                    )

                    Spacer(Modifier.height(16.dp))

                    PremiumHeroPanel(
                        mode = contentMode,
                        titleOverride = when (contentMode) {
                            ContentMode.Movies -> lazyMovieCategories.firstOrNull { it.key == selectedMovieCategoryKey }?.title
                            ContentMode.Series -> lazySeriesFolders.firstOrNull { it.key == selectedSeriesKey }?.title ?: selectedSeriesGroup?.takeIf { it.isNotBlank() && it != "__all_series__" }
                            ContentMode.LiveTv -> null
                        },
                        subtitleOverride = when (contentMode) {
                            ContentMode.LiveTv -> "${state.visibleChannels.size} canales disponibles"
                            ContentMode.Movies -> if (selectedMovieCategoryKey == null) "${lazyMovieCategories.size} carpetas de películas" else "${lazyMovieItems.size} películas disponibles"
                            ContentMode.Series -> when {
                                selectedSeriesKey != null -> "${lazySeriesEpisodes.size} capítulos disponibles"
                                selectedSeriesGroup == null || selectedSeriesGroup == "__all_series__" -> "${lazySeriesFolders.size} series disponibles"
                                else -> "${lazySeriesFolders.count { it.group == selectedSeriesGroup }} series disponibles"
                            }
                        },
                        channel = when (contentMode) {
                            ContentMode.LiveTv -> state.visibleChannels.firstOrNull()
                            ContentMode.Movies -> lazyMovieItems.firstOrNull() ?: lazyMovieSearchItems.firstOrNull()
                            ContentMode.Series -> lazySeriesEpisodes.firstOrNull()
                        }
                    )

                    if (!usingLazyBackendContent) {
                        Spacer(Modifier.height(24.dp))

                        CategoryRow(
                            groups = state.groups,
                            selectedGroup = state.selectedGroup,
                            onSelectGroup = viewModel::selectGroup
                        )
                    }
                }

                Spacer(Modifier.width(32.dp))

                LazyColumn(
                    state = contentListState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (!usingLazyBackendContent) {
                        item {
                            StatusBlock(state = state, mode = contentMode)
                        }
                    }

                    contentItems(
                        state = state,
                        contentMode = contentMode,
                        selectedSeriesKey = selectedSeriesKey,
                        selectedSeriesGroup = selectedSeriesGroup,
                        onSelectSeries = {
                        lastSeriesFocusKey = it
                        selectedSeriesKey = it
                    },
                        onClearSeries = { selectedSeriesKey = null },
                        onSelectSeriesGroup = { group ->
                            selectedSeriesKey = null
                            selectedSeriesGroup = group
                        },
                        onClearSeriesGroup = {
                            selectedSeriesKey = null
                            selectedSeriesGroup = null
                        },
                        lazySeriesFolders = lazySeriesFolders,
                    lazySeriesEpisodes = lazySeriesEpisodes,
                    isLazySeriesLoading = isLazySeriesLoading,
                    selectedMovieCategoryKey = selectedMovieCategoryKey,
                    lastSeriesFocusKey = lastSeriesFocusKey,
                    lastMovieCategoryFocusKey = lastMovieCategoryFocusKey,
                    lazyMovieCategories = lazyMovieCategories,
                    lazyMovieItems = lazyMovieItems,
                    lazyMovieSearchItems = lazyMovieSearchItems,
                    isLazyMovieSearchLoading = isLazyMovieSearchLoading,
                    isLazyMoviesLoading = isLazyMoviesLoading,
                    onSelectMovieCategory = {
                        lastMovieCategoryFocusKey = it
                        selectedMovieCategoryKey = it
                    },
                    onClearMovieCategory = { selectedMovieCategoryKey = null },
                    showLazySearch = showLazySearch,
                    lazySearchQuery = lazySearchQuery,
                    onLazySearchQueryChange = { lazySearchQuery = it },
                    onToggleLazySearch = {
                        if (showLazySearch) {
                            lazySearchQuery = ""
                            lazyMovieSearchItems = emptyList()
                            isLazyMovieSearchLoading = false
                        }
                        showLazySearch = !showLazySearch
                    },
                    onPlay = onPlay
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.contentItems(
    state: LiveTvUiState,
    contentMode: ContentMode,
    selectedSeriesKey: String?,
    selectedSeriesGroup: String?,
    onSelectSeries: (String) -> Unit,
    onClearSeries: () -> Unit,
    onSelectSeriesGroup: (String) -> Unit,
    onClearSeriesGroup: () -> Unit,
    lazySeriesFolders: List<OptimizedContentApi.SeriesFolderLite>,
    lazySeriesEpisodes: List<Channel>,
    isLazySeriesLoading: Boolean,
    selectedMovieCategoryKey: String?,
    lastSeriesFocusKey: String?,
    lastMovieCategoryFocusKey: String?,
    lazyMovieCategories: List<OptimizedContentApi.MovieCategoryLite>,
    lazyMovieItems: List<Channel>,
    lazyMovieSearchItems: List<Channel>,
    isLazyMovieSearchLoading: Boolean,
    isLazyMoviesLoading: Boolean,
    onSelectMovieCategory: (String) -> Unit,
    onClearMovieCategory: () -> Unit,
    showLazySearch: Boolean,
    lazySearchQuery: String,
    onLazySearchQueryChange: (String) -> Unit,
    onToggleLazySearch: () -> Unit,
    onPlay: (Channel, List<Channel>) -> Unit
) {
    if (state.isLoading || state.isFiltering) {
        item {
            LoadingSectionCard(
                text = if (state.isLoading) {
                    "Sincronizando ${state.contentMode.title.lowercase(Locale.getDefault())}..."
                } else {
                    "Preparando ${state.contentMode.title.lowercase(Locale.getDefault())}..."
                }
            )
        }
        return
    }

    if (isLazyMoviesLoading || isLazySeriesLoading) {
        item {
            LoadingSectionCard(
                text = if (contentMode == ContentMode.Series) {
                    "Cargando series..."
                } else {
                    "Cargando películas..."
                }
            )
        }
        return
    }

    if (contentMode == ContentMode.Movies && lazyMovieCategories.isNotEmpty()) {
        val movieSearchText = lazySearchQuery.trim().lowercase(Locale.getDefault())
        val selectedCategory = lazyMovieCategories.firstOrNull { it.key == selectedMovieCategoryKey }

        if (selectedCategory == null) {
            val visibleMovieResults = if (movieSearchText.isBlank()) {
                emptyList()
            } else {
                lazyMovieSearchItems.filter {
                    it.name.lowercase(Locale.getDefault()).contains(movieSearchText) ||
                        it.group.lowercase(Locale.getDefault()).contains(movieSearchText)
                }
            }

            item {
                LazySearchHeader(
                    title = if (movieSearchText.isBlank()) {
                        "${lazyMovieCategories.size} categorías encontradas"
                    } else {
                        "${visibleMovieResults.size} películas encontradas"
                    },
                    placeholder = "Buscar película...",
                    showSearch = showLazySearch,
                    query = lazySearchQuery,
                    onQueryChange = onLazySearchQueryChange,
                    onToggleSearch = onToggleLazySearch
                )
            }

            if (movieSearchText.isBlank()) {
                itemsIndexed(lazyMovieCategories) { index, category ->
                    MovieCategoryLiteRow(
                        category = category,
                        requestInitialFocus =
                            selectedMovieCategoryKey == null &&
                                !showLazySearch &&
                                (
                                    category.key == lastMovieCategoryFocusKey ||
                                        (
                                            (
                                                lastMovieCategoryFocusKey == null ||
                                                    lazyMovieCategories.none { it.key == lastMovieCategoryFocusKey }
                                            ) && index == 0
                                        )
                                ),
                        onOpen = { onSelectMovieCategory(category.key) }
                    )
                }
            } else {
                if (isLazyMovieSearchLoading) {
                    item {
                        LoadingSectionCard(
                            text = "Buscando películas..."
                        )
                    }
                } else if (visibleMovieResults.isEmpty()) {
                    item {
                        Text(
                            text = "Sin resultados para \"$lazySearchQuery\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                        )
                    }
                }

                itemsIndexed(visibleMovieResults) { index, movie ->
                    ChannelRow(
                        channel = movie,
                        currentProgram = null,
                        nextProgram = null,
                        requestInitialFocus = false,
                        onSkipNext = visibleMovieResults.getOrNull(index + 1)?.let { nextMovie ->
                            { onPlay(nextMovie, visibleMovieResults) }
                        },
                        onPlay = { onPlay(movie, visibleMovieResults) }
                    )
                }
            }
        } else {
            item {
                MovieCategoryHeader(
                    category = selectedCategory,
                    onBack = onClearMovieCategory
                )
            }

            itemsIndexed(lazyMovieItems) { index, movie ->
                ChannelRow(
                    channel = movie,
                    currentProgram = null,
                    nextProgram = null,
                    requestInitialFocus = index == 0,
                    onSkipNext = lazyMovieItems.getOrNull(index + 1)?.let { nextMovie ->
                        { onPlay(nextMovie, lazyMovieItems) }
                    },
                    onPlay = { onPlay(movie, lazyMovieItems) }
                )
            }
        }

        return
    }

    if (contentMode == ContentMode.Series && lazySeriesFolders.isNotEmpty()) {
        val allSeriesGroupKey = "__all_series__"
        val seriesSearchText = lazySearchQuery.trim().lowercase(Locale.getDefault())
        val selectedGroupKey = selectedSeriesGroup

        val sourceGroups = listOf(
            Triple(allSeriesGroupKey, "Todo", lazySeriesFolders.size)
        ) + lazySeriesFolders
            .groupBy { it.group.ifBlank { "Series" } }
            .map { (group, folders) -> Triple(group, group, folders.size) }
            .sortedBy { it.second.lowercase(Locale.getDefault()) }

        val baseSeriesFolders = when {
            selectedGroupKey.isNullOrBlank() -> lazySeriesFolders
            selectedGroupKey == allSeriesGroupKey -> lazySeriesFolders
            else -> lazySeriesFolders.filter { it.group == selectedGroupKey }
        }

        val selectedGroupTitle = when {
            selectedGroupKey.isNullOrBlank() -> null
            selectedGroupKey == allSeriesGroupKey -> "Todo"
            else -> selectedGroupKey
        }

        val visibleSeriesFolders = if (seriesSearchText.isBlank()) {
            baseSeriesFolders
        } else {
            baseSeriesFolders.filter {
                it.title.lowercase(Locale.getDefault()).contains(seriesSearchText) ||
                    it.group.lowercase(Locale.getDefault()).contains(seriesSearchText)
            }
        }

        val selectedFolder = lazySeriesFolders.firstOrNull { it.key == selectedSeriesKey }

        if (selectedFolder == null) {
            if (!selectedGroupTitle.isNullOrBlank()) {
                item {
                    SeriesSourceGroupHeader(
                        groupName = selectedGroupTitle,
                        seriesCount = visibleSeriesFolders.size,
                        onBack = onClearSeriesGroup
                    )
                }
            }

            item {
                LazySearchHeader(
                    title = when {
                        !selectedGroupTitle.isNullOrBlank() -> "${visibleSeriesFolders.size} series encontradas"
                        seriesSearchText.isBlank() -> "${sourceGroups.size} grupos de series"
                        else -> "${visibleSeriesFolders.size} series encontradas"
                    },
                    placeholder = "Buscar serie o grupo...",
                    showSearch = showLazySearch,
                    query = lazySearchQuery,
                    onQueryChange = onLazySearchQueryChange,
                    onToggleSearch = onToggleLazySearch
                )
            }

            if (selectedGroupKey.isNullOrBlank() && seriesSearchText.isBlank()) {
                itemsIndexed(sourceGroups) { index, groupInfo ->
                    SeriesSourceGroupRow(
                        groupName = groupInfo.second,
                        seriesCount = groupInfo.third,
                        requestInitialFocus = !showLazySearch && index == 0,
                        focusToken = "series-source-groups|${selectedGroupKey ?: ""}|$seriesSearchText|${sourceGroups.size}",
                        onOpen = { onSelectSeriesGroup(groupInfo.first) }
                    )
                }
            } else {
                if (visibleSeriesFolders.isEmpty()) {
                    item {
                        Text(
                            text = "Sin resultados para \"$lazySearchQuery\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                        )
                    }
                }

                itemsIndexed(visibleSeriesFolders) { index, folder ->
                    SeriesFolderLiteRow(
                        folder = folder,
                        requestInitialFocus =
                            selectedSeriesKey == null &&
                                !showLazySearch &&
                                (
                                    folder.key == lastSeriesFocusKey ||
                                        (
                                            (
                                                lastSeriesFocusKey == null ||
                                                    visibleSeriesFolders.none { it.key == lastSeriesFocusKey }
                                            ) && index == 0
                                        )
                                ),
                        focusToken = "series-folders|${selectedGroupKey ?: ""}|$seriesSearchText|${visibleSeriesFolders.size}|${lastSeriesFocusKey ?: ""}",
                        onOpen = { onSelectSeries(folder.key) }
                    )
                }
            }
        } else {
            item {
                SeriesFolderLiteHeader(
                    folder = selectedFolder,
                    episodeCount = lazySeriesEpisodes.size.takeIf { it > 0 },
                    onBack = onClearSeries
                )
            }

            itemsIndexed(lazySeriesEpisodes) { index, episode ->
                ChannelRow(
                    channel = episode,
                    currentProgram = null,
                    nextProgram = null,
                    requestInitialFocus = index == 0,
                    focusToken = "series-episodes|${selectedFolder.key}|${lazySeriesEpisodes.size}",
                    onSkipNext = lazySeriesEpisodes.getOrNull(index + 1)?.let { nextEpisode ->
                        { onPlay(nextEpisode, lazySeriesEpisodes) }
                    },
                    onPlay = { onPlay(episode, lazySeriesEpisodes) }
                )
            }
        }

        return
    }

    if (contentMode != ContentMode.Series) {
        itemsIndexed(state.visibleChannels) { index, channel ->
            ChannelRow(
                channel = channel,
                currentProgram = null,
                nextProgram = null,
                onSkipNext = state.visibleChannels.getOrNull(index + 1)?.let { nextChannel ->
                    { onPlay(nextChannel, state.visibleChannels) }
                },
                onPlay = { onPlay(channel, state.visibleChannels) }
            )
        }
        return
    }

    val folders = buildSeriesFolders(state.visibleChannels)
    val selectedFolder = folders.firstOrNull { it.key == selectedSeriesKey }

    if (selectedFolder == null) {
        item {
            Text(
                text = "${folders.size} series encontradas",
                style = MaterialTheme.typography.titleMedium
            )
        }


        items(folders) { folder ->
            SeriesFolderRow(
                folder = folder,
                onOpen = { onSelectSeries(folder.key) }
            )
        }
    } else {
        item {
            SeriesFolderHeader(
                folder = selectedFolder,
                onBack = onClearSeries
            )
        }

        itemsIndexed(selectedFolder.episodes) { index, episode ->
            ChannelRow(
                channel = episode,
                currentProgram = null,
                nextProgram = null,
                onSkipNext = selectedFolder.episodes.getOrNull(index + 1)?.let { nextEpisode ->
                    { onPlay(nextEpisode, selectedFolder.episodes) }
                },
                onPlay = { onPlay(episode, selectedFolder.episodes) }
            )
        }
    }
}

@Composable
private fun LoadingSectionCard(
    text: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}



@Composable
private fun PremiumHeroPanel(
    mode: ContentMode,
    titleOverride: String? = null,
    subtitleOverride: String? = null,
    channel: Channel? = null
) {
    val title = titleOverride?.takeIf { it.isNotBlank() }
        ?: channel?.name?.takeIf { it.isNotBlank() }
        ?: when (mode) {
            ContentMode.LiveTv -> "TV en vivo"
            ContentMode.Movies -> "Películas"
            ContentMode.Series -> "Series"
        }

    val subtitle = subtitleOverride?.takeIf { it.isNotBlank() }
        ?: channel?.group?.takeIf { it.isNotBlank() }
        ?: when (mode) {
            ContentMode.LiveTv -> "Canales organizados para reproducción inmediata"
            ContentMode.Movies -> "Exploración visual de contenido VOD"
            ContentMode.Series -> "Temporadas, carpetas y capítulos"
        }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 184.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        shape = RoundedCornerShape(36.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        ),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(104.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(30.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                )
            ) {
                if (!channel?.logoUrl.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(channel!!.logoUrl),
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (mode) {
                                ContentMode.LiveTv -> "TV"
                                ContentMode.Movies -> "🎬"
                                ContentMode.Series -> "S"
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = when (mode) {
                            ContentMode.LiveTv -> "EN VIVO"
                            ContentMode.Movies -> "VOD"
                            ContentMode.Series -> "SERIES"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PremiumSectionHeader(
    mode: ContentMode,
    refreshMessage: String? = null,
    isLoading: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
        shape = RoundedCornerShape(34.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        ),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (mode) {
                            ContentMode.LiveTv -> "TV"
                            ContentMode.Movies -> "🎬"
                            ContentMode.Series -> "S"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = when (mode) {
                        ContentMode.LiveTv -> "Canales en vivo con navegación optimizada para TV"
                        ContentMode.Movies -> "Explorá películas por carpetas con una vista más inmersiva"
                        ContentMode.Series -> "Series, temporadas y capítulos con selector optimizado"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                refreshMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )
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
    refreshMessage: String? = null,
    isRefreshing: Boolean = false,
    disableRefreshFocus: Boolean = false,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f)
        ),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Text(
                    text = mode.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f),
                    maxLines = 1
                )

                refreshMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

                        val refreshButtonModifier = if (disableRefreshFocus) {
                Modifier
            } else {
                Modifier
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }

                        when (event.key) {
                            Key.DirectionCenter,
                            Key.Enter,
                            Key.NumPadEnter -> {
                                onRefresh()
                                true
                            }

                            else -> false
                        }
                    }
                    .focusable()
                    .clickable { onRefresh() }
            }

            Surface(
                modifier = refreshButtonModifier,
                color = if (disableRefreshFocus) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                },
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)
                )
            ) {
                Text(
                    text = "Actualizar",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                )
            }

Surface(
                modifier = Modifier.clickable { onBack() },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.30f)
                )
            ) {
                Text(
                    text = "Volver",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    groups: List<String>,
    selectedGroup: String,
    onSelectGroup: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleGroups = groups
        .filter { it.isNotBlank() }
        .distinct()

    if (visibleGroups.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Categorías",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Elegí una carpeta con el control remoto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(visibleGroups) { group ->
                    var focused by remember(group, selectedGroup) {
                        mutableStateOf(false)
                    }

                    val selected = group == selectedGroup
                    val active = selected || focused

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                focused = focusState.isFocused
                            }
                            .onKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    (
                                        event.key == Key.DirectionCenter ||
                                            event.key == Key.Enter ||
                                            event.key == Key.NumPadEnter
                                    )
                                ) {
                                    onSelectGroup(group)
                                    true
                                } else {
                                    false
                                }
                            }
                            .focusable()
                            .clickable { onSelectGroup(group) },
                        color = if (active) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        border = BorderStroke(
                            if (active) 2.dp else 1.dp,
                            if (active) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                            }
                        ),
                        shape = RoundedCornerShape(999.dp),
                        shadowElevation = if (focused) 8.dp else 0.dp
                    ) {
                        Text(
                            text = group,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f)
                            },
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun TvCategoryChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val active = selected || focused
    val shape = RoundedCornerShape(999.dp)

    Surface(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                when (event.key) {
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter -> {
                        if (enabled) {
                            onClick()
                        }
                        true
                    }

                    else -> false
                }
            }
            .border(
                width = if (active) 4.dp else 1.dp,
                color = if (focused) {
                    MaterialTheme.colorScheme.primary
                } else if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                },
                shape = shape
            )
            .focusable()
            .clickable(enabled = enabled) { onClick() },
        color = if (focused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        } else if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
        },
        shape = shape,
        border = BorderStroke(
            1.dp,
            if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
            }
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            maxLines = 1
        )
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
                text = "Contenido cargado desde caché.",
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
private fun LazySearchHeader(
    title: String,
    placeholder: String,
    showSearch: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit
) {
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(showSearch) {
        if (showSearch) {
            kotlinx.coroutines.delay(120)
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        ),
        shadowElevation = 3.dp
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = if (showSearch) {
                            "Escribí para filtrar resultados."
                        } else {
                            "Usá buscar para encontrar contenido más rápido."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    modifier = Modifier.clickable { onToggleSearch() },
                    color = if (showSearch) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
                    },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        1.dp,
                        if (showSearch) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                        }
                    )
                ) {
                    Text(
                        text = if (showSearch) "Cerrar búsqueda" else "Buscar",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (showSearch) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }

            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                )
            }
        }
    }
}

@Composable
private fun MovieCategoryLiteRow(
    category: OptimizedContentApi.MovieCategoryLite,
    requestInitialFocus: Boolean = false,
    onOpen: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestInitialFocus, category.key) {
        if (requestInitialFocus) {
            repeat(4) {
                delay(150)
                runCatching { focusRequester.requestFocus() }
            }
        }
    }

    val active = isFocused

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onOpen()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable { onOpen() },
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
        },
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(
            width = if (active) 4.dp else 1.dp,
            color = if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            }
        ),
        shadowElevation = if (active) 14.dp else 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                color = if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
                },
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.55f else 0.20f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎬",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${category.itemCount} películas disponibles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Surface(
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                },
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "Abrir",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}


@Composable
private fun MovieCategoryHeader(
    category: OptimizedContentApi.MovieCategoryLite,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        ),
        shadowElevation = 5.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "CARPETA ABIERTA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${category.itemCount} películas disponibles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                modifier = Modifier.clickable { onBack() },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                )
            ) {
                Text(
                    text = "Volver",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}




@Composable
private fun SeriesSourceGroupRow(
    groupName: String,
    seriesCount: Int,
    requestInitialFocus: Boolean = false,
    focusToken: String = "",
    onOpen: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestInitialFocus, groupName, focusToken) {
        if (requestInitialFocus) {
            repeat(4) {
                delay(150)
                runCatching { focusRequester.requestFocus() }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onOpen()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable { onOpen() },
        color = if (isFocused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
        },
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            }
        ),
        shadowElevation = if (isFocused) 14.dp else 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isFocused) 0.28f else 0.14f),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isFocused) 0.55f else 0.20f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "$seriesCount series disponibles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                color = if (isFocused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                },
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "Abrir",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isFocused) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}


@Composable
private fun SeriesSourceGroupHeader(
    groupName: String,
    seriesCount: Int,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)
        ),
        shadowElevation = 5.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "GRUPO ABIERTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "$seriesCount series disponibles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                modifier = Modifier.clickable { onBack() },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                )
            ) {
                Text(
                    text = "Volver",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}


@Composable
private fun SeriesFolderLiteRow(
    folder: OptimizedContentApi.SeriesFolderLite,
    requestInitialFocus: Boolean = false,
    focusToken: String = "",
    onOpen: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestInitialFocus, folder.key, focusToken) {
        if (requestInitialFocus) {
            repeat(5) {
                delay(160)
                runCatching { focusRequester.requestFocus() }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onOpen()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable { onOpen() },
        color = if (isFocused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
        },
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            }
        ),
        shadowElevation = if (isFocused) 14.dp else 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isFocused) 0.28f else 0.14f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isFocused) 0.55f else 0.20f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▶",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = folder.group.ifBlank { "Serie" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = if (isFocused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                },
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "Ver",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isFocused) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}


@Composable
private fun SeriesFolderLiteHeader(
    folder: OptimizedContentApi.SeriesFolderLite,
    episodeCount: Int? = null,
    onBack: () -> Unit
) {
    androidx.compose.foundation.layout.Column {
        com.storetd.play.feature.vod.SeriesTmdbHeader(groupName = folder.title, isSeriesMode = true)
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
        OriginalSeriesFolderLiteHeader(folder = folder, episodeCount = episodeCount, onBack = onBack)
    }
}

@Composable
private fun OriginalSeriesFolderLiteHeader(
    folder: OptimizedContentApi.SeriesFolderLite,
    episodeCount: Int? = null,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)
        ),
        shadowElevation = 5.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = folder.group.ifBlank { "SERIE ABIERTA" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${episodeCount ?: folder.episodeCount} capítulos disponibles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                modifier = Modifier.clickable { onBack() },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f)
                )
            ) {
                Text(
                    text = "Volver",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}



@Composable
private fun SeriesFolderRow(
    folder: SeriesFolder,
    onOpen: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)

    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                when (event.key) {
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter -> {
                        onOpen()
                        true
                    }

                    else -> false
                }
            }
            .border(
                width = if (focused) 4.dp else 1.dp,
                color = if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                },
                shape = shape
            )
            .clip(shape)
            .focusable()
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(
            containerColor = if (focused) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (focused) 12.dp else 4.dp
        ),
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (!folder.logoUrl.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(folder.logoUrl),
                    contentDescription = folder.title,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AssistChip(
                            onClick = {},
                            label = { Text("${folder.episodes.size} capítulos") }
                        )
                    }

                    item {
                        AssistChip(
                            onClick = {},
                            label = { Text(folder.group) }
                        )
                    }

                    item {
                        AssistChip(
                            onClick = {},
                            label = { Text(if (focused) "OK para abrir" else "Carpeta") }
                        )
                    }
                }
            }

            Surface(
                color = if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                ),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "Abrir",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (focused) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}


@Composable
private fun SeriesFolderHeader(
    folder: SeriesFolder,
    onBack: () -> Unit
) {
    androidx.compose.foundation.layout.Column {
        com.storetd.play.feature.vod.SeriesTmdbHeader(groupName = folder.title, isSeriesMode = true)
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
        OriginalSeriesFolderHeader(folder = folder, onBack = onBack)
    }
}

@Composable
private fun OriginalSeriesFolderHeader(
    folder: SeriesFolder,
    onBack: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = folder.title,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "${folder.episodes.size} capítulos disponibles",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Volver a series")
            }
        }
    }
}


@Composable
private fun ReportedBrokenChip() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.90f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        )
    ) {
        Text(
            text = "Reportado",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelRow(
    channel: Channel,
    currentProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    requestInitialFocus: Boolean = false,
    focusToken: String = "",
    onSkipNext: (() -> Unit)? = null,
    onPlay: () -> Unit
) {
    val rowFocusRequester = remember { FocusRequester() }

    LaunchedEffect(requestInitialFocus, channel.streamUrl, channel.name, focusToken) {
        if (requestInitialFocus) {
            repeat(5) {
                delay(160)
                runCatching { rowFocusRequester.requestFocus() }
            }
        }
    }

    var focused by remember { mutableStateOf(false) }
    var showReportedDialog by remember(channel.streamUrl) { mutableStateOf(false) }
    val context = LocalContext.current
    var isReportedBroken by remember(channel.streamUrl) {
        mutableStateOf(BrokenLinkStore.isReported(context, channel.streamUrl))
    }

    LaunchedEffect(channel.streamUrl) {
        isReportedBroken = BrokenLinkStore.isReported(context, channel.streamUrl)
    }

    fun handlePlayRequest() {
        if (isReportedBroken) {
            showReportedDialog = true
        } else {
            onPlay()
        }
    }

    val active = focused
    val shape = RoundedCornerShape(36.dp)

    Card(
        onClick = { handlePlayRequest() },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(rowFocusRequester)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                when (event.key) {
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter -> {
                        handlePlayRequest()
                        true
                    }

                    Key.DirectionRight -> {
                        onSkipNext?.invoke()
                        onSkipNext != null
                    }

                    else -> false
                }
            }
            .border(
                width = if (active) 4.dp else 1.dp,
                color = if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                },
                shape = shape
            )
            .focusable(),
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.64f)
            }
        ),
        shape = shape,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (active) 18.dp else 5.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                color = if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
                },
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(
                    1.dp,
                    if (active) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                    }
                )
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(channel.logoUrl),
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channel.name.take(2).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isReportedBroken) {
                        ReportedBrokenChip()
                    }

                    Surface(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f)
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = channel.group.ifBlank { "Sin categoría" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val programText = when {
                    currentProgram != null -> "Ahora: ${currentProgram.title}"
                    nextProgram != null -> "Luego: ${nextProgram.title}"
                    else -> "Listo para reproducir"
                }

                Text(
                    text = programText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f)
                },
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "Ver",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }
    }

    if (showReportedDialog) {
        AlertDialog(
            onDismissRequest = { showReportedDialog = false },
            title = { Text("Canal reportado") },
            text = {
                Text("Este contenido fue marcado como caído. Revisalo desde el panel de enlaces reportados antes de reproducirlo.")
            },
            confirmButton = {
                TextButton(onClick = { showReportedDialog = false }) {
                    Text("Entendido")
                }
            }
        )
    }
}

private fun buildSeriesFolders(channels: List<Channel>): List<SeriesFolder> {
    if (channels.isEmpty()) return emptyList()

    val unique = channels.distinctBy {
        it.streamUrl.ifBlank { it.name + "|" + it.group }
    }

    return unique
        .groupBy { fastSeriesFolderKey(it) }
        .values
        .mapNotNull { groupedEpisodes ->
            val first = groupedEpisodes.firstOrNull() ?: return@mapNotNull null

            val folderKey = fastSeriesFolderKey(first)
            val title = fastSeriesTitle(first)

            if (title.isBlank()) {
                return@mapNotNull null
            }

            val posterUrl = groupedEpisodes
                .firstOrNull { !it.logoUrl.isNullOrBlank() }
                ?.logoUrl
                ?: first.logoUrl

            val episodes = groupedEpisodes
                .distinctBy {
                    it.streamUrl.ifBlank { fastEpisodeKey(it.name) }
                }
                .sortedWith(
                    compareBy<Channel> { fastEpisodeSeason(it.name) }
                        .thenBy { fastEpisodeNumber(it.name) }
                        .thenBy { it.name.lowercase(Locale.getDefault()) }
                )

            SeriesFolder(
                key = folderKey,
                title = title,
                group = first.group.ifBlank { title },
                logoUrl = posterUrl,
                episodes = episodes
            )
        }
        .sortedBy { it.title.lowercase(Locale.getDefault()) }
}

private fun seriesFolderKey(channel: Channel): String {
    val cleanName = cleanSeriesTitle(channel.name)
    val cleanGroup = cleanSeriesTitle(channel.group)

    val source = when {
        cleanName.length >= 3 -> cleanName
        cleanGroup.length >= 3 -> cleanGroup
        channel.name.isNotBlank() -> channel.name
        else -> channel.group
    }

    return normalizeSeriesKey(source)
}

private fun cleanSeriesTitle(value: String): String {
    var text = value.trim()

    if (text.isBlank()) return ""

    text = text
        .replace(Regex("(?i)\\bS\\s*\\d{1,2}\\s*E\\s*\\d{1,3}\\b.*"), "")
        .replace(Regex("(?i)\\bT\\s*\\d{1,2}\\s*E\\s*\\d{1,3}\\b.*"), "")
        .replace(Regex("(?i)\\b\\d{1,2}\\s*x\\s*\\d{1,3}\\b.*"), "")
        .replace(Regex("(?i)\\btemporada\\s*\\d{1,2}\\b.*"), "")
        .replace(Regex("(?i)\\bseason\\s*\\d{1,2}\\b.*"), "")
        .replace(Regex("(?i)\\bcap[ií]tulo\\s*\\d{1,3}\\b.*"), "")
        .replace(Regex("(?i)\\bepisodio\\s*\\d{1,3}\\b.*"), "")
        .replace(Regex("(?i)\\bepisode\\s*\\d{1,3}\\b.*"), "")

    text = text
        .replace(Regex("(?i)\\s+-\\s+cap.*$"), "")
        .replace(Regex("(?i)\\s+-\\s+ep.*$"), "")
        .replace(Regex("(?i)\\s+\\[.*?\\]"), "")
        .replace(Regex("(?i)\\s+\\(.*?\\)"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '|', '.', ':', '_')

    return text
}

private fun extractSeasonEpisode(value: String): Pair<Int, Int>? {
    val normalized = normalizeSeriesKey(value)

    val patterns = listOf(
        Regex("\\bs\\s*(\\d{1,2})\\s*e\\s*(\\d{1,3})\\b"),
        Regex("\\bt\\s*(\\d{1,2})\\s*e\\s*(\\d{1,3})\\b"),
        Regex("\\b(\\d{1,2})\\s*x\\s*(\\d{1,3})\\b"),
        Regex("\\btemporada\\s*(\\d{1,2}).*?capitulo\\s*(\\d{1,3})\\b"),
        Regex("\\btemporada\\s*(\\d{1,2}).*?episodio\\s*(\\d{1,3})\\b"),
        Regex("\\bseason\\s*(\\d{1,2}).*?episode\\s*(\\d{1,3})\\b")
    )

    for (pattern in patterns) {
        val match = pattern.find(normalized)

        if (match != null) {
            val season = match.groupValues[1].toIntOrNull() ?: 1
            val episode = match.groupValues[2].toIntOrNull() ?: 0

            return season to episode
        }
    }

    val singleEpisodePatterns = listOf(
        Regex("\\bcapitulo\\s*(\\d{1,3})\\b"),
        Regex("\\bepisodio\\s*(\\d{1,3})\\b"),
        Regex("\\bepisode\\s*(\\d{1,3})\\b"),
        Regex("\\bep\\s*(\\d{1,3})\\b")
    )

    for (pattern in singleEpisodePatterns) {
        val match = pattern.find(normalized)

        if (match != null) {
            val episode = match.groupValues[1].toIntOrNull() ?: 0
            return 1 to episode
        }
    }

    return null
}

private fun normalizeSeriesKey(value: String): String {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.getDefault())
        .replace("&", " y ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun buildSectionPlaylistUrl(
    activationCode: String,
    fallbackUrl: String,
    contentMode: ContentMode
): String {
    val code = activationCode.trim()

    if (code.isBlank()) {
        return fallbackUrl
    }

    val type = when (contentMode) {
        ContentMode.LiveTv -> "live"
        ContentMode.Movies -> "movies"
        ContentMode.Series -> "series"
    }

    val encodedCode = URLEncoder.encode(code, "UTF-8")

    return "http://82.39.109.213/playlist/proxy?code=$encodedCode&type=$type"
}


private fun episodeUniqueKey(channel: Channel): String {
    val folderKey = seriesFolderKey(channel)
    val seasonEpisode = extractSeasonEpisode(channel.name)
    val urlKey = normalizeSeriesKey(channel.streamUrl)

    return if (seasonEpisode != null) {
        "$folderKey|s${seasonEpisode.first}e${seasonEpisode.second}|$urlKey"
    } else {
        "$folderKey|${normalizeSeriesKey(channel.name)}|$urlKey"
    }
}

private fun episodeSeasonForSort(channel: Channel): Int {
    return extractSeasonEpisode(channel.name)?.first ?: 999
}

private fun episodeNumberForSort(channel: Channel): Int {
    return extractSeasonEpisode(channel.name)?.second ?: 9999
}

private fun cleanEpisodeDisplayName(value: String): String {
    return value
        .replace(Regex("(?i)\\bS\\s*(\\d{1,2})\\s*E\\s*(\\d{1,3})\\b"), "S$1 E$2")
        .replace(Regex("(?i)\\bT\\s*(\\d{1,2})\\s*E\\s*(\\d{1,3})\\b"), "T$1 E$2")
        .replace(Regex("(?i)\\b(\\d{1,2})\\s*x\\s*(\\d{1,3})\\b"), "$1x$2")
        .replace(Regex("\\s+"), " ")
        .trim()
}


private fun fastSeriesFolderKey(channel: Channel): String {
    return fastSeriesTitle(channel)
        .lowercase(Locale.getDefault())
        .replace("&", " y ")
        .replace(Regex("[^a-z0-9áéíóúüñ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun fastSeriesTitle(channel: Channel): String {
    val rawName = channel.name.trim()
    val rawGroup = channel.group.trim()

    var title = rawName

    // Quitar prefijos de categorías que vienen metidos en el nombre.
    title = title
        .replace(Regex("(?i)^series\\s*[|:/-]\\s*"), "")
        .replace(Regex("(?i)^serie\\s*[|:/-]\\s*"), "")
        .replace(Regex("(?i)^temporadas\\s*[|:/-]\\s*"), "")
        .replace(Regex("(?i)^capitulos\\s*[|:/-]\\s*"), "")
        .replace(Regex("(?i)^capítulos\\s*[|:/-]\\s*"), "")

    // Si el nombre empieza igual que la categoría, quitarlo.
    if (rawGroup.isNotBlank()) {
        title = title.replace(
            Regex("^" + Regex.escape(rawGroup) + "\\s*[|:/-]\\s*", RegexOption.IGNORE_CASE),
            ""
        )
    }

    // Quitar tags comunes.
    title = title
        .replace(Regex("(?i)\\b(latino|castellano|subtitulado|dual audio|hd|fhd|4k|1080p|720p)\\b"), "")
        .replace(Regex("(?i)\\[[^\\]]*\\]"), "")
        .replace(Regex("(?i)\\([^)]*\\)"), "")

    // Quitar temporada/capítulo y todo lo posterior.
    title = title
        .replace(Regex("(?i)\\bS\\s*\\d{1,2}\\s*E\\s*\\d{1,3}\\b.*"), "")
        .replace(Regex("(?i)\\bT\\s*\\d{1,2}\\s*E\\s*\\d{1,3}\\b.*"), "")
        .replace(Regex("(?i)\\b\\d{1,2}\\s*x\\s*\\d{1,3}\\b.*"), "")
        .replace(Regex("(?i)\\btemporada\\s*\\d{1,2}\\b.*"), "")
        .replace(Regex("(?i)\\bseason\\s*\\d{1,2}\\b.*"), "")
        .replace(Regex("(?i)\\bcap[ií]tulo\\s*\\d{1,3}\\b.*"), "")
        .replace(Regex("(?i)\\bepisodio\\s*\\d{1,3}\\b.*"), "")
        .replace(Regex("(?i)\\bepisode\\s*\\d{1,3}\\b.*"), "")
        .replace(Regex("(?i)\\bep\\s*\\d{1,3}\\b.*"), "")

    // Quitar separadores finales.
    title = title
        .replace(Regex("(?i)\\s+[-|:]\\s+(cap[ií]tulo|episodio|episode|ep|s\\d|t\\d|\\d+x).*$"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '|', '.', ':', '_')

    // Muy importante: NO usar grupos genéricos como carpeta.
    if (title.length >= 3 && !looksLikeGenericSeriesGroup(title)) {
        return title
    }

    // Solo usamos grupo como último recurso si no es genérico.
    if (rawGroup.isNotBlank() && !looksLikeGenericSeriesGroup(rawGroup)) {
        return rawGroup
    }

    return rawName
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '|', '.', ':', '_')
}

private fun fastEpisodeNumber(name: String): Int {
    val patterns = listOf(
        Regex("(?i)\\bS\\s*\\d{1,2}\\s*E\\s*(\\d{1,3})\\b"),
        Regex("(?i)\\bT\\s*\\d{1,2}\\s*E\\s*(\\d{1,3})\\b"),
        Regex("(?i)\\b\\d{1,2}\\s*x\\s*(\\d{1,3})\\b"),
        Regex("(?i)\\bcap[ií]tulo\\s*(\\d{1,3})\\b"),
        Regex("(?i)\\bepisodio\\s*(\\d{1,3})\\b"),
        Regex("(?i)\\bepisode\\s*(\\d{1,3})\\b"),
        Regex("(?i)\\bep\\s*(\\d{1,3})\\b")
    )

    for (pattern in patterns) {
        val match = pattern.find(name)
        if (match != null) {
            return match.groupValues[1].toIntOrNull() ?: 9999
        }
    }

    return 9999
}


private fun looksLikeGenericSeriesGroup(value: String): Boolean {
    val normalized = value
        .lowercase(Locale.getDefault())
        .replace("&", " y ")
        .replace(Regex("\\s+"), " ")
        .trim()

    return normalized == "series" ||
        normalized == "serie" ||
        normalized.startsWith("series |") ||
        normalized.startsWith("series|") ||
        normalized.startsWith("serie |") ||
        normalized.startsWith("serie|") ||
        normalized.startsWith("series ") ||
        normalized.contains("animadas") ||
        normalized.contains("anime") ||
        normalized.contains("amc+") ||
        normalized.contains("netflix") ||
        normalized.contains("hbo") ||
        normalized.contains("max") ||
        normalized.contains("disney") ||
        normalized.contains("prime") ||
        normalized.contains("paramount") ||
        normalized.contains("adultos") ||
        normalized.contains("infantil") ||
        normalized.contains("documental") ||
        normalized.contains("latinas") ||
        normalized.contains("español") ||
        normalized.contains("espanol")
}

private fun fastEpisodeSeason(name: String): Int {
    val patterns = listOf(
        Regex("(?i)\\bS\\s*(\\d{1,2})\\s*E\\s*\\d{1,3}\\b"),
        Regex("(?i)\\bT\\s*(\\d{1,2})\\s*E\\s*\\d{1,3}\\b"),
        Regex("(?i)\\b(\\d{1,2})\\s*x\\s*\\d{1,3}\\b"),
        Regex("(?i)\\btemporada\\s*(\\d{1,2})\\b"),
        Regex("(?i)\\bseason\\s*(\\d{1,2})\\b")
    )

    for (pattern in patterns) {
        val match = pattern.find(name)
        if (match != null) {
            return match.groupValues[1].toIntOrNull() ?: 1
        }
    }

    return 1
}

private fun fastEpisodeKey(name: String): String {
    return fastEpisodeSeason(name).toString() + "x" + fastEpisodeNumber(name).toString() + "|" +
        name.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9áéíóúüñ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}

private fun formatLiveEpgTime(value: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value))


}
