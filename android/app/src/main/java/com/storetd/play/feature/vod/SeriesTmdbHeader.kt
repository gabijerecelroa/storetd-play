package com.storetd.play.feature.vod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.storetd.play.core.api.TmdbRepository
import com.storetd.play.core.api.TmdbResult
import java.util.Locale

@Composable
fun SeriesTmdbHeader(groupName: String, isSeriesMode: Boolean) {
    var info by remember { mutableStateOf<TmdbResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    if (!isSeriesMode || groupName.isBlank() || groupName.lowercase(Locale.ROOT) == "todo") return

    // Limpiamos el nombre de la carpeta por si viene con texto extra
    val cleanGroupName = groupName.replace(Regex("(?i)^(series?|tv|carpetas?)\\s*\\|?\\s*"), "").trim()

    LaunchedEffect(cleanGroupName) {
        isLoading = true
        info = TmdbRepository().searchContent(cleanGroupName, true)
        isLoading = false
    }

    if (info == null && !isLoading) return

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
        shape = RoundedCornerShape(20.dp)
    ) {
        if (isLandscape) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (info?.posterPath != null) {
                    AsyncImage(
                        model = info!!.posterPath, contentDescription = null, contentScale = ContentScale.Crop, 
                        modifier = Modifier.width(130.dp).aspectRatio(0.66f).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = info?.title ?: cleanGroupName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!info?.releaseYear.isNullOrEmpty()) {
                            Text(text = info!!.releaseYear, color = Color.LightGray, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        if (info?.voteAverage != null && info!!.voteAverage > 0) {
                            Text(text = "⭐ ${String.format(Locale.US, "%.1f", info!!.voteAverage)}", color = Color(0xFFFFD700), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = info?.overview ?: "Buscando información...", color = Color.LightGray, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    if (info?.posterPath != null) {
                        AsyncImage(
                            model = info!!.posterPath, contentDescription = null, contentScale = ContentScale.Crop, 
                            modifier = Modifier.width(100.dp).aspectRatio(0.66f).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = info?.title ?: cleanGroupName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!info?.releaseYear.isNullOrEmpty()) {
                                Text(text = info!!.releaseYear, color = Color.LightGray, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            if (info?.voteAverage != null && info!!.voteAverage > 0) {
                                Text(text = "⭐ ${String.format(Locale.US, "%.1f", info!!.voteAverage)}", color = Color(0xFFFFD700), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = info?.overview ?: "Buscando información...", color = Color.LightGray, fontSize = 14.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
