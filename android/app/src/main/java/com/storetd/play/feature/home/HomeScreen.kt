package com.storetd.play.feature.home
import com.storetd.play.core.network.SeriesFolderLite
import com.storetd.play.core.network.MovieCategoryLite

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.storetd.play.core.model.*
import com.storetd.play.core.network.*
import com.storetd.play.core.storage.*

@Composable
fun HomeScreen(
    onOpenLiveTv: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenContinueItem: (SavedChannel) -> Unit,
    onOpenEpg: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenSettings: () -> Unit,
    config: AppConfig
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    
    var history by remember { mutableStateOf<List<SavedChannel>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<SavedChannel>>(emptyList()) }
    
    var estrenos by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var peliculasVistas by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var seriesVistas by remember { mutableStateOf<List<Channel>>(emptyList()) }

    // Motor de extracción automática de contenido
    LaunchedEffect(Unit) {
        history = LocalLibrary.history(context).take(15)
        favorites = LocalLibrary.favorites(context).take(15)

        withContext(Dispatchers.IO) {
            try {
                val acc = LocalAccount.getAccount(context)
                val code = acc.activationCode
                
                // Extraer Películas Inteligentes
                val mCats = OptimizedContentApi.loadMovieCategoriesLite(code)
                if (mCats.isNotEmpty()) {
                    val estCat = mCats.find { it.name.contains("estreno", true) || it.name.contains("nuevo", true) || it.name.contains("2026", true) } ?: mCats.first()
                    estrenos = OptimizedContentApi.loadMovieCategoryItems(code, estCat.id).take(20)
                    
                    val popCat = mCats.find { it.name.contains("popular", true) || it.name.contains("top", true) || it.name.contains("vista", true) } ?: mCats.getOrNull(1) ?: mCats.first()
                    if (popCat.id != estCat.id) {
                        peliculasVistas = OptimizedContentApi.loadMovieCategoryItems(code, popCat.id).take(20)
                    }
                }

                // Extraer Series Inteligentes
                val sCats = OptimizedContentApi.loadSeriesFoldersLite(code)
                if (sCats.isNotEmpty()) {
                    val popSCat = sCats.find { it.name.contains("popular", true) || it.name.contains("top", true) || it.name.contains("vista", true) } ?: sCats.first()
                    seriesVistas = OptimizedContentApi.loadSeriesFolderEpisodes(code, popSCat.id).take(20)
                }
            } catch(e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        if (isLoading && history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE50914))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 42.dp, bottom = 60.dp)
            ) {
                item {
                    // HERO BANNER GIGANTE
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(260.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFE50914).copy(alpha = 0.8f), Color(0xFF000000).copy(alpha = 0.3f))
                                ),
                                RoundedCornerShape(16.dp)
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .clickable { onOpenMovies() }
                            .focusable()
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.CenterStart).padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🎬 BIENVENIDO A ${config.appName.uppercase()}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("El mejor entretenimiento\npara tu familia", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Navegá por el menú lateral para descubrir la cartelera completa", color = Color.LightGray, fontSize = 16.sp)
                        }
                    }
                }

                if (history.isNotEmpty()) {
                    item {
                        CarouselSectionSaved("⏱️ Continuar Viendo", history) { onOpenContinueItem(it) }
                    }
                }

                if (estrenos.isNotEmpty()) {
                    item {
                        CarouselSection("🔥 Estrenos Recientes", estrenos) { ch ->
                            onOpenContinueItem(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Estrenos", null))
                        }
                    }
                }

                if (peliculasVistas.isNotEmpty()) {
                    item {
                        CarouselSection("⭐ Películas Más Vistas", peliculasVistas) { ch ->
                            onOpenContinueItem(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Peliculas", null))
                        }
                    }
                }

                if (seriesVistas.isNotEmpty()) {
                    item {
                        CarouselSection("🍿 Series Destacadas", seriesVistas) { ch ->
                            onOpenContinueItem(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Series", null))
                        }
                    }
                }
                
                if (favorites.isNotEmpty()) {
                    item {
                        CarouselSectionSaved("❤️ Mi Lista de Favoritos", favorites) { onOpenContinueItem(it) }
                    }
                }
            }
        }
    }
}

@Composable
fun CarouselSection(title: String, items: List<Channel>, onClick: (Channel) -> Unit) {
    Column(modifier = Modifier.padding(top = 36.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items) { item ->
                MovieCard(item.name, item.logoUrl) { onClick(item) }
            }
        }
    }
}

@Composable
fun CarouselSectionSaved(title: String, items: List<SavedChannel>, onClick: (SavedChannel) -> Unit) {
    Column(modifier = Modifier.padding(top = 36.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items) { item ->
                MovieCard(item.name, item.logoUrl) { onClick(item) }
            }
        }
    }
}

@Composable
fun MovieCard(name: String, logoUrl: String?, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f)

    Box(
        modifier = Modifier
            .width(140.dp)
            .height(210.dp)
            .scale(scale)
            .zIndex(if (isFocused) 1f else 0f)
            .background(Color(0xFF18181B), RoundedCornerShape(10.dp))
            .border(if (isFocused) 3.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        if (!logoUrl.isNullOrBlank() && logoUrl != "-") {
            AsyncImage(
                model = logoUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(name, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
        
        if (isFocused) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.15f)))
        }
    }
}
