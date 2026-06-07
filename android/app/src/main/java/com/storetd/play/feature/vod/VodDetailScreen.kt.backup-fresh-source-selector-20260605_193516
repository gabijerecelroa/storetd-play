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

private fun isMagmaMovieUrl(url: String): Boolean {
    return url.contains("/magma-lite/movie/", ignoreCase = true)
}

private fun extractMagmaMovieStreamId(url: String): String? {
    return Regex("/magma-lite/movie/([0-9]+)\\.m3u8", RegexOption.IGNORE_CASE)
        .find(url)
        ?.groupValues
        ?.getOrNull(1)
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
        isMagmaMovie -> "▶ Elegir fuente"
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

        isLoadingSources = true
        sourceMessage = null

        scope.launch {
            val account = LocalAccount.getAccount(context)
            val activationCode = account.activationCode.trim()

            val loadedSources = withContext(Dispatchers.IO) {
                OptimizedContentApi.loadMagmaMovieSources(
                    activationCode = activationCode,
                    streamId = streamId,
                    kind = extractUrlQueryParam(streamUrl, "kind"),
                    seriesId = extractUrlQueryParam(streamUrl, "seriesId"),
                    season = extractUrlQueryParam(streamUrl, "season"),
                    episode = extractUrlQueryParam(streamUrl, "episode")
                )
            }

            if (loadedSources.isNotEmpty()) {
                movieSources = loadedSources
                sourceMessage = null
            } else {
                movieSources = emptyList()
                sourceMessage = "Esta película no está disponible en este momento. Probá otra opción."
            }

            isLoadingSources = false
            showSourceDialog = true
        }
    }

    if (showSourceDialog) {
        AlertDialog(
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
                TextButton(onClick = { showSourceDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        AsyncImage(
            model = info?.backdropPath ?: logoUrl, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.35f
        )
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0F0F0F)))))
        
        if (isLandscape) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF0F0F0F), Color(0xCC0F0F0F), Color.Transparent), endX = 1400f)))
        }

        val posterUrl = info?.posterPath ?: logoUrl
        val baseName = channelName.replace(Regex("\\(\\d{4}\\)"), "").trim()
        val titleText = if (isSeries) baseName else (info?.title ?: baseName)
        val descText = info?.overview ?: if (isLoading) "Buscando información..." else "Sin descripción disponible."

        if (isLandscape) {
            // DISEÑO PARA TV / HORIZONTAL
            Row(modifier = Modifier.fillMaxSize().padding(56.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!posterUrl.isNullOrEmpty() && posterUrl != "-") {
                    AsyncImage(
                        model = posterUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.width(260.dp).aspectRatio(0.66f).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = titleText, color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!info?.releaseYear.isNullOrEmpty()) {
                            Text(text = info!!.releaseYear, color = Color.LightGray, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        if (info?.voteAverage != null && info!!.voteAverage > 0) {
                            Text(text = "⭐ ${String.format(Locale.US, "%.1f", info!!.voteAverage)}", color = Color(0xFFFFD700), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // BOTÓN ARRIBA (Para que nunca desaparezca en la TV)
                    Button(
                        onClick = { handlePlayRequest() }, modifier = Modifier.focusRequester(focusRequester).height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914), contentColor = Color.White)
                    ) {
                        Text(playButtonText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = descText, color = Color.LightGray, fontSize = 18.sp, 
                        lineHeight = 26.sp, maxLines = 6, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            // DISEÑO PARA CELULAR / VERTICAL
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                if (!posterUrl.isNullOrEmpty() && posterUrl != "-") {
                    AsyncImage(
                        model = posterUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.width(200.dp).aspectRatio(0.66f).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                Text(text = titleText, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (!info?.releaseYear.isNullOrEmpty()) {
                        Text(text = info!!.releaseYear, color = Color.LightGray, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    if (info?.voteAverage != null && info!!.voteAverage > 0) {
                        Text(text = "⭐ ${String.format(Locale.US, "%.1f", info!!.voteAverage)}", color = Color(0xFFFFD700), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // BOTÓN ANCHO Y FÁCIL DE TOCAR EN CELULARES
                Button(
                    onClick = { handlePlayRequest() }, modifier = Modifier.focusRequester(focusRequester).fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914), contentColor = Color.White)
                ) {
                    Text(playButtonText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(text = descText, color = Color.LightGray, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Justify)
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
