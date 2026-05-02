package com.storetd.play.feature.live

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.storetd.play.core.network.BrokenLinksApi
import com.storetd.play.core.epg.EpgProgram
import com.storetd.play.core.model.Channel
import com.storetd.play.core.storage.BrokenLinkStore
import com.storetd.play.core.storage.LocalAccount
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
    var isLazyMoviesLoading by remember(contentMode, selectedMovieCategoryKey) {
        mutableStateOf(false)
    }

    val refreshScope = rememberCoroutineScope()

    fun refreshCurrentContent() {
        val account = LocalAccount.getAccount(context)
        val activationCode = account.activationCode.trim()

        refreshMessage = "Actualizando contenido..."

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
                            async = false
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
                refreshMessage = if (refreshed) {
                    "Contenido actualizado."
                } else {
                    "No se pudo confirmar la actualización. Reintentando carga..."
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

    BackHandler(enabled = true) {
        when {
            selectedSeriesKey != null -> {
                selectedSeriesKey = null
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
        lazySeriesFolders = emptyList()
        lazySeriesEpisodes = emptyList()
        lazyMovieCategories = emptyList()
        lazyMovieItems = emptyList()
        isLazySeriesLoading = false
        isLazyMoviesLoading = false

        viewModel.setContentMode(contentMode)
        viewModel.setHideAdultContent(ParentalControl.isAdultContentHidden(context))

        val account = LocalAccount.getAccount(context)
        val activationCode = account.activationCode.trim()

        if (activationCode.isNotBlank() && contentMode == ContentMode.Series) {
            isLazySeriesLoading = true
            val folders = withContext(Dispatchers.IO) {
                runCatching {
                    OptimizedContentApi.loadSeriesFoldersLite(
                        activationCode = activationCode,
                        includeAdult = !ParentalControl.isAdultContentHidden(context)
                    )
                }.getOrDefault(emptyList())
            }
            isLazySeriesLoading = false

            if (folders.isNotEmpty()) {
                lazySeriesFolders = folders
                return@LaunchedEffect
            }
        }

        if (activationCode.isNotBlank() && contentMode == ContentMode.Movies) {
            isLazyMoviesLoading = true
            val categories = withContext(Dispatchers.IO) {
                runCatching {
                    OptimizedContentApi.loadMovieCategoriesLite(
                        activationCode = activationCode,
                        includeAdult = !ParentalControl.isAdultContentHidden(context)
                    )
                }.getOrDefault(emptyList())
            }
            isLazyMoviesLoading = false

            if (categories.isNotEmpty()) {
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
        val isCompact = maxWidth < 700.dp
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
                    ContentControls(
                        state = state,
                        mode = contentMode,
                        onSearchChange = viewModel::setSearchQuery,
                        onHideAdultChange = { hidden ->
                            ParentalControl.setAdultContentHidden(context, hidden)
                            viewModel.setHideAdultContent(hidden)
                            refreshCurrentContent()
                        },
                        onRefresh = { refreshCurrentContent() },
                        refreshMessage = refreshMessage,
                        onBack = onBack
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
                    onSelectSeries = {
                        lastSeriesFocusKey = it
                        selectedSeriesKey = it
                    },
                    onClearSeries = { selectedSeriesKey = null },
                    lazySeriesFolders = lazySeriesFolders,
                    lazySeriesEpisodes = lazySeriesEpisodes,
                    isLazySeriesLoading = isLazySeriesLoading,
                    selectedMovieCategoryKey = selectedMovieCategoryKey,
                    lastSeriesFocusKey = lastSeriesFocusKey,
                    lastMovieCategoryFocusKey = lastMovieCategoryFocusKey,
                    lazyMovieCategories = lazyMovieCategories,
                    lazyMovieItems = lazyMovieItems,
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
                        .width(360.dp)
                        .fillMaxHeight()
                ) {
                    ContentControls(
                        state = state,
                        mode = contentMode,
                        onSearchChange = viewModel::setSearchQuery,
                        onHideAdultChange = { hidden ->
                            ParentalControl.setAdultContentHidden(context, hidden)
                            viewModel.setHideAdultContent(hidden)
                            refreshCurrentContent()
                        },
                        onRefresh = { refreshCurrentContent() },
                        refreshMessage = refreshMessage,
                        onBack = onBack
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

                Spacer(Modifier.width(24.dp))

                LazyColumn(
                    state = contentListState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        onSelectSeries = {
                        lastSeriesFocusKey = it
                        selectedSeriesKey = it
                    },
                        onClearSeries = { selectedSeriesKey = null },
                        lazySeriesFolders = lazySeriesFolders,
                    lazySeriesEpisodes = lazySeriesEpisodes,
                    isLazySeriesLoading = isLazySeriesLoading,
                    selectedMovieCategoryKey = selectedMovieCategoryKey,
                    lastSeriesFocusKey = lastSeriesFocusKey,
                    lastMovieCategoryFocusKey = lastMovieCategoryFocusKey,
                    lazyMovieCategories = lazyMovieCategories,
                    lazyMovieItems = lazyMovieItems,
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
    onSelectSeries: (String) -> Unit,
    onClearSeries: () -> Unit,
    lazySeriesFolders: List<OptimizedContentApi.SeriesFolderLite>,
    lazySeriesEpisodes: List<Channel>,
    isLazySeriesLoading: Boolean,
    selectedMovieCategoryKey: String?,
    lastSeriesFocusKey: String?,
    lastMovieCategoryFocusKey: String?,
    lazyMovieCategories: List<OptimizedContentApi.MovieCategoryLite>,
    lazyMovieItems: List<Channel>,
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
        val visibleMovieCategories = if (movieSearchText.isBlank()) {
            lazyMovieCategories
        } else {
            lazyMovieCategories.filter {
                it.title.lowercase(Locale.getDefault()).contains(movieSearchText)
            }
        }

        val selectedCategory = lazyMovieCategories.firstOrNull { it.key == selectedMovieCategoryKey }

        if (selectedCategory == null) {
            item {
                LazySearchHeader(
                    title = "${visibleMovieCategories.size} categorías encontradas",
                    placeholder = "Buscar película o categoría...",
                    showSearch = showLazySearch,
                    query = lazySearchQuery,
                    onQueryChange = onLazySearchQueryChange,
                    onToggleSearch = onToggleLazySearch
                )
            }

            if (visibleMovieCategories.isEmpty()) {
                item {
                    Text(
                        text = "Sin resultados para \"$lazySearchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                    )
                }
            }

            itemsIndexed(visibleMovieCategories) { index, category ->
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
                                                visibleMovieCategories.none { it.key == lastMovieCategoryFocusKey }
                                        ) && index == 0
                                    )
                            ),
                    onOpen = { onSelectMovieCategory(category.key) }
                )
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
        val seriesSearchText = lazySearchQuery.trim().lowercase(Locale.getDefault())
        val visibleSeriesFolders = if (seriesSearchText.isBlank()) {
            lazySeriesFolders
        } else {
            lazySeriesFolders.filter {
                it.title.lowercase(Locale.getDefault()).contains(seriesSearchText) ||
                    it.group.lowercase(Locale.getDefault()).contains(seriesSearchText)
            }
        }

        val selectedFolder = lazySeriesFolders.firstOrNull { it.key == selectedSeriesKey }

        if (selectedFolder == null) {
            item {
                LazySearchHeader(
                    title = "${visibleSeriesFolders.size} series encontradas",
                    placeholder = "Buscar serie...",
                    showSearch = showLazySearch,
                    query = lazySearchQuery,
                    onQueryChange = onLazySearchQueryChange,
                    onToggleSearch = onToggleLazySearch
                )
            }

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
                    onOpen = { onSelectSeries(folder.key) }
                )
            }
        } else {
            item {
                SeriesFolderLiteHeader(
                    folder = selectedFolder,
                    onBack = onClearSeries
                )
            }

            itemsIndexed(lazySeriesEpisodes) { index, episode ->
                ChannelRow(
                    channel = episode,
                    currentProgram = null,
                    nextProgram = null,
                    requestInitialFocus = index == 0,
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
private fun ContentControls(
    state: LiveTvUiState,
    mode: ContentMode,
    onSearchChange: (String) -> Unit,
    onHideAdultChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    refreshMessage: String? = null,
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
            horizontalArrangement = Arrangement.spacedBy(18.dp),
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
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f)
                            },
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp)
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
                        onClick()
                        true
                    }

                    else -> false
                }
            }
            .border(
                width = if (active) 3.dp else 1.dp,
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
            .clickable { onClick() },
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
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Surface(
                modifier = Modifier.clickable { onToggleSearch() },
                color = if (showSearch) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
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
                    text = if (showSearch) "Cerrar" else "🔍 Buscar",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (showSearch) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
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

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            runCatching { focusRequester.requestFocus() }
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
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.50f)
        },
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            }
        ),
        shadowElevation = if (isFocused) 8.dp else 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${category.itemCount} películas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
            )
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
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${category.itemCount} películas disponibles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.80f)
                )
            }

            Text(
                text = "Volver",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SeriesFolderLiteRow(
    folder: OptimizedContentApi.SeriesFolderLite,
    requestInitialFocus: Boolean = false,
    onOpen: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            runCatching { focusRequester.requestFocus() }
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
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.50f)
        },
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            }
        ),
        shadowElevation = if (isFocused) 8.dp else 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = folder.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${folder.episodeCount} capítulos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
            )
        }
    }
}





@Composable
private fun SeriesFolderLiteHeader(
    folder: OptimizedContentApi.SeriesFolderLite,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${folder.episodeCount} capítulos disponibles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.80f)
                )
            }

            Text(
                text = "Volver",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
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
    onSkipNext: (() -> Unit)? = null,
    onPlay: () -> Unit
) {
    val rowFocusRequester = remember { FocusRequester() }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            runCatching { rowFocusRequester.requestFocus() }
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
    val shape = RoundedCornerShape(22.dp)

    fun handlePlayRequest() {
        if (isReportedBroken) {
            showReportedDialog = true
        } else {
            onPlay()
        }
    }

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
            .clickable { handlePlayRequest() },
        colors = CardDefaults.cardColors(
            containerColor = if (focused) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            } else if (isReportedBroken) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.32f)
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
            if (!channel.logoUrl.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(channel.logoUrl),
                    contentDescription = channel.name,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isReportedBroken) {
                    Spacer(Modifier.height(6.dp))
                    ReportedBrokenChip()
                }

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

                Spacer(Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AssistChip(
                            onClick = {},
                            label = { Text(channel.group) }
                        )
                    }

                    item {
                        AssistChip(
                            onClick = {},
                            label = { Text(if (focused) "OK para ver" else "Listo") }
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
                    text = "Ver",
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
    if (showReportedDialog) {
        AlertDialog(
            onDismissRequest = { showReportedDialog = false },
            title = { Text("Este contenido fue reportado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Este enlace fue marcado como caído o no disponible en este dispositivo.")

                    Button(
                        onClick = {
                            showReportedDialog = false
                            onPlay()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reproducir igual")
                    }

                    if (onSkipNext != null) {
                        OutlinedButton(
                            onClick = {
                                showReportedDialog = false
                                onSkipNext()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Siguiente")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            BrokenLinkStore.clear(context, channel.streamUrl)
                            isReportedBroken = false
                            showReportedDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quitar marca local")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReportedDialog = false }) {
                    Text("Cancelar")
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

    return "https://storetd-play-backend.onrender.com/playlist/proxy?code=$encodedCode&type=$type"
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
