package com.storetd.play.feature.vod

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.storetd.play.core.api.TmdbRepository
import com.storetd.play.core.api.TmdbResult
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun VodDetailScreen(
    channelName: String, streamUrl: String, groupName: String, logoUrl: String?,
    onPlay: () -> Unit, onBack: () -> Unit
) {
    var info by remember { mutableStateOf<TmdbResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
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
                        onClick = onPlay, modifier = Modifier.focusRequester(focusRequester).height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914), contentColor = Color.White)
                    ) {
                        Text("▶ Reproducir", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
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
                    onClick = onPlay, modifier = Modifier.focusRequester(focusRequester).fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914), contentColor = Color.White)
                ) {
                    Text("▶ Reproducir", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(text = descText, color = Color.LightGray, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Justify)
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
