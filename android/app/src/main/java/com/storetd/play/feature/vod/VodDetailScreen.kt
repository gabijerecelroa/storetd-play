package com.storetd.play.feature.vod

import android.net.Uri

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.storetd.play.core.api.TmdbRepository
import com.storetd.play.core.api.TmdbResult
import com.storetd.play.core.network.OptimizedContentApi
import com.storetd.play.core.storage.LocalAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private fun isMagmaMovieUrl(url: String): Boolean { return url.contains("/magma-lite/movie/", ignoreCase = true) || url.contains("/api/xtream/play/", ignoreCase = true) }

private fun extractMagmaMovieStreamId(url: String): String? {
    val magma = Regex("/magma-lite/movie/([0-9]+)\\.m3u8", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
    if (magma != null) return magma
    val xtreamMovie = Regex("/api/xtream/play/movie/([0-9]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
    if (xtreamMovie != null) return xtreamMovie
    return Regex("/api/xtream/play/series/([0-9]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
}

private fun extractUrlQueryParam(url: String, key: String): String {
    return runCatching {
        Uri.parse(url).getQueryParameter(key).orEmpty()
    }.getOrDefault("")
}


@Composable
fun VodDetailScreen(
    channelName: String,
    streamUrl: String,
    groupName: String,
    logoUrl: String?,
    onPlay: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf<TmdbResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingSources by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var sourceMessage by remember { mutableStateOf<String?>(null) }
    var movieSources by remember { mutableStateOf<List<OptimizedContentApi.MovieSourceLite>>(emptyList()) }
    val sourceDialogScrollState = rememberScrollState()

    val focusRequester = remember { FocusRequester() }
    val isSeries = groupName.contains("serie", ignoreCase = true) || groupName.contains("temporada", ignoreCase = true)
    
    // Detectamos si es Celular (Vertical) o TV (Horizontal)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    LaunchedEffect(channelName) {
        info = TmdbRepository().searchContent(channelName, isSeries)
        isLoading = false
        delay(100)
        try { focusRequester.requestFocus() } catch (e: Exception) {}
    }

    val isMagmaMovie = remember(streamUrl) { isMagmaMovieUrl(streamUrl) }
    val magmaMovieStreamId = remember(streamUrl) { extractMagmaMovieStreamId(streamUrl) }
    val playButtonText = when {
        isLoadingSources -> "Cargando fuentes..."
        isMagmaMovie -> "▶ Reproducir"
        else -> "▶ Reproducir"
    }

    fun handlePlayRequest() {
        if (!isMagmaMovie) {
            onPlay(streamUrl)
            return
        }

        val streamId = magmaMovieStreamId

        if (streamId.isNullOrBlank()) {
            onPlay(streamUrl)
            return
        }

        // MAGMA_FRESH_SOURCE_SELECTOR_START
        isLoadingSources = true
        showSourceDialog = false

        scope.launch {
            val account = LocalAccount.getAccount(context)
            val activationCode = account.activationCode.trim()
            
            val isEpisodeUrl = streamUrl.contains("/series/", ignoreCase = true)

            val loadedSources = withContext(Dispatchers.IO) {
                OptimizedContentApi.loadMagmaMovieSources(
                    activationCode = activationCode,
                    streamId = streamId,
                    kind = if (isEpisodeUrl) "episode" else "movie",
                    seriesId = if (isEpisodeUrl) extractUrlQueryParam(streamUrl, "seriesId") else "",
                    season = if (isEpisodeUrl) extractUrlQueryParam(streamUrl, "season") else "",
                    episode = if (isEpisodeUrl) extractUrlQueryParam(streamUrl, "episode") else "",
                    streamUrl = streamUrl
                )
            }

            movieSources = loadedSources
            if (loadedSources.isEmpty()) {
                // Solo mostramos error si no hay enlaces
                sourceMessage = "Esta película no está disponible en este momento. Probá otra opción."
            } else {
                sourceMessage = null
            }
            showSourceDialog = true

            isLoadingSources = false
        }
        // MAGMA_FRESH_SOURCE_SELECTOR_END
    }

    
    if (showSourceDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = {
                Text(
                    text = "Elegí una fuente",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (isLandscape) 420.dp else 560.dp)
                        .verticalScroll(sourceDialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sourceMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    movieSources.forEach { source ->
                        Button(
                            onClick = {
                                showSourceDialog = false
                                onPlay(source.streamUrl)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE50914),
                                contentColor = Color.White
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = source.title,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = listOf(source.subtitle, source.quality, source.language)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.82f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSourceDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07111B)) // StreamVault Canvas (Fondo oficial)
    ) {
        val backdropUrl = info?.backdropPath?.let { "https://image.tmdb.org/t/p/original$it" } ?: logoUrl

        // 1. IMAGEN DE FONDO GIGANTE (Backdrop)
        AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(backdropUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.5f) // Difuminado cinematográfico
        )

        // 2. DEGRADADO OSCURO ESTILO STREAMVAULT (HeroTop a HeroBottom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x1A07111B), // Casi transparente arriba
                            Color(0xCC07111B), // HeroTop
                            Color(0xFF07111B)  // Canvas sólido abajo
                        ),
                        startY = 0f,
                        endY = 1400f
                    )
                )
        )

        // 3. CONTENIDO DE LA PELÍCULA (Textos y Botones)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(56.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Insignias (Badges)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HD",
                    color = Color(0xFFBBC6D8), // TextSecondary
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "★ ${info?.voteAverage ?: "8.5"}",
                    color = Color(0xFFFFC766), // Warning (StreamVault Star)
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .border(1.dp, Color(0x264C6D95), RoundedCornerShape(4.dp)) // Outline Color
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "4K HDR",
                        color = Color(0xFFBBC6D8), // TextSecondary
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Título
            Text(
                text = info?.title ?: channelName,
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 48.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sinopsis
            Text(
                text = info?.overview ?: "Sin descripción disponible por el momento.",
                color = Color(0xFFBBC6D8), // TextSecondary
                fontSize = 16.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(if (isLandscape) 0.6f else 1f),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Botones Píldora
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var isPlayFocused by remember { mutableStateOf(false) }
                var isBackFocused by remember { mutableStateOf(false) }

                // Botón Reproducir
                Button(
                    onClick = { handlePlayRequest() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayFocused) Color.White else Color(0xFFF5F7FB).copy(alpha = 0.9f),
                        contentColor = Color(0xFF07111B)
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { isPlayFocused = it.isFocused || it.hasFocus }
                ) {
                    Text(
                        text = playButtonText,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Botón Volver
                Button(
                    onClick = { onBack() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBackFocused) Color.White else Color(0xFF223754), // SurfaceAccent
                        contentColor = if (isBackFocused) Color(0xFF07111B) else Color.White
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.onFocusChanged { isBackFocused = it.isFocused || it.hasFocus }
                ) {
                    Text(
                        text = "Volver al menú",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
