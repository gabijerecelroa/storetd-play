package com.storetd.play.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.storetd.play.core.api.TmdbRepository
import com.storetd.play.core.api.TmdbResult
import kotlinx.coroutines.delay

@Composable
fun VodDetailScreen(
    channelName: String, streamUrl: String, groupName: String, logoUrl: String?,
    onPlay: () -> Unit, onBack: () -> Unit
) {
    var info by remember { mutableStateOf<TmdbResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }
    val isSeries = groupName.contains("serie", ignoreCase = true) || groupName.contains("temporada", ignoreCase = true)

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
        Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF0F0F0F), Color(0xCC0F0F0F), Color.Transparent), endX = 1400f)))
        
        Row(modifier = Modifier.fillMaxSize().padding(56.dp), verticalAlignment = Alignment.CenterVertically) {
            val posterUrl = info?.posterPath ?: logoUrl
            if (!posterUrl.isNullOrEmpty() && posterUrl != "-") {
                AsyncImage(
                    model = posterUrl, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.width(260.dp).aspectRatio(0.66f).clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
            
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(text = info?.title ?: channelName.replace(Regex("\\(\\d{4}\\)"), "").trim(), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!info?.releaseYear.isNullOrEmpty()) {
                        Text(text = info!!.releaseYear, color = Color.LightGray, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    if (info?.voteAverage != null && info!!.voteAverage > 0) {
                        Text(text = "⭐ ${String.format("%.1f", info!!.voteAverage)}", color = Color(0xFFFFD700), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = info?.overview ?: if (isLoading) "Buscando información..." else "Sin descripción disponible.", color = Color.LightGray, fontSize = 18.sp, maxLines = 5, lineHeight = 26.sp)
                Spacer(modifier = Modifier.height(40.dp))
                Button(
                    onClick = onPlay, modifier = Modifier.focusRequester(focusRequester).height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914), contentColor = Color.White)
                ) {
                    Text("▶ Reproducir", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
