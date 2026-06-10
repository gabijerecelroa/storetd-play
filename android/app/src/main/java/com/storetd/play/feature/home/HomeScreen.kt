package com.storetd.play.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.storetd.play.core.model.Channel
import com.storetd.play.core.network.OptimizedContentApi
import com.storetd.play.core.storage.LocalAccount
import com.storetd.play.core.storage.LocalLibrary
import com.storetd.play.core.storage.SavedChannel

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
    config: Any? = null
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    
    var history by remember { mutableStateOf<List<SavedChannel>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<SavedChannel>>(emptyList()) }
    
    var estrenos by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var peliculasVistas by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var seriesFolders by remember { mutableStateOf<List<Channel>>(emptyList()) }

    LaunchedEffect(Unit) {
        history = LocalLibrary.history(context).take(15)
        favorites = LocalLibrary.favorites(context).take(15)

        withContext(Dispatchers.IO) {
            try {
                val acc = LocalAccount.getAccount(context)
                val code = acc.activationCode
                
                val mCats = OptimizedContentApi.loadMovieCategoriesLite(code)
                if (mCats.isNotEmpty()) {
                    val estCat = mCats.find { it.title.contains("estreno", true) || it.title.contains("nuevo", true) || it.title.contains("202", true) } ?: mCats.firstOrNull()
                    if (estCat != null) estrenos = OptimizedContentApi.loadMovieCategoryItems(code, estCat.key).take(20)
                    
                    val popCat = mCats.find { it.title.contains("popular", true) || it.title.contains("top", true) || it.title.contains("vista", true) } ?: mCats.getOrNull(1) ?: mCats.firstOrNull()
                    if (popCat != null && popCat.key != estCat?.key) peliculasVistas = OptimizedContentApi.loadMovieCategoryItems(code, popCat.key).take(20)
                }

                // 🔥 FIX SERIES: Ahora traemos las CARPETAS PRINCIPALES, NO capítulos sueltos 🔥
                val sCats = OptimizedContentApi.loadSeriesFoldersLite(code)
                if (sCats.isNotEmpty()) {
                    seriesFolders = sCats.shuffled().take(20).map { 
                        Channel(id = it.key, name = it.title, streamUrl = "dummy_series", logoUrl = it.posterUrl ?: "-", group = it.group ?: "Series", tvgId = null) 
                    }
                }
            } catch(e: Exception) { e.printStackTrace() } finally { isLoading = false }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        if (isLoading && history.isEmpty() && estrenos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFE50914)) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 42.dp, bottom = 60.dp)
            ) {
                item {
                    var isHeroFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(260.dp)
                            .scale(if (isHeroFocused) 1.02f else 1f)
                            .border(if (isHeroFocused) 4.dp else 1.dp, if (isHeroFocused) Color.White else Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(colors = listOf(Color(0xFFE50914).copy(alpha = 0.8f), Color(0xFF000000).copy(alpha = 0.3f))), RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .onFocusChanged { isHeroFocused = it.isFocused || it.hasFocus }
                            .focusable()
                            .clickable { onOpenMovies() }
                    ) {
                        Column(modifier = Modifier.align(Alignment.CenterStart).padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🎬 BIENVENIDO A STORE TD PLAY", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("El mejor entretenimiento\npara tu familia", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Navegá por el menú lateral para descubrir la cartelera completa", color = Color.LightGray, fontSize = 16.sp)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        QuickButton("📺 TV en Vivo") { onOpenLiveTv() }
                        QuickButton("🎬 Películas") { onOpenMovies() }
                        QuickButton("🍿 Series") { onOpenSeries() }
                        QuickButton("❤️ Favoritos") { onOpenFavorites() }
                    }
                }

                if (history.isNotEmpty()) item { CarouselSectionSaved("⏱️ Continuar Viendo", history) { onOpenContinueItem(it) } }
                if (estrenos.isNotEmpty()) item { CarouselSection("🔥 Estrenos y Recomendados", estrenos) { ch -> onOpenContinueItem(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Peliculas", null)) } }
                if (peliculasVistas.isNotEmpty()) item { CarouselSection("⭐ Películas Populares", peliculasVistas) { ch -> onOpenContinueItem(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Peliculas", null)) } }
                if (seriesFolders.isNotEmpty()) {
                    item { 
                        CarouselSection("🍿 Series Destacadas", seriesFolders) { ch -> 
                            // Al hacer clic en cualquier Serie del inicio, lo llevamos a la sección de Series para evitar errores
                            onOpenSeries() 
                        } 
                    } 
                }
            }
        }
    }
}

@Composable
fun QuickButton(text: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)
    
    // 🔥 MAGIA DE ALTO CONTRASTE PARA BOTONES RÁPIDOS 🔥
    val bgColor = if (isFocused) Color.White else Color(0xFFE50914)
    val textColor = if (isFocused) Color.Black else Color.White
    val borderWidth = if (isFocused) 4.dp else 1.dp
    val borderColor = if (isFocused) Color.White else Color.White.copy(alpha=0.2f)
    
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(55.dp)
            .scale(scale)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .focusable()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun CarouselSection(title: String, items: List<Channel>, onClick: (Channel) -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items) { item -> MovieCard(item.name, item.logoUrl) { onClick(item) } }
        }
    }
}

@Composable
fun CarouselSectionSaved(title: String, items: List<SavedChannel>, onClick: (SavedChannel) -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items) { item -> MovieCard(item.name, item.logoUrl) { onClick(item) } }
        }
    }
}

@Composable
fun MovieCard(name: String, logoUrl: String?, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f)
    
    // 🔥 MARCO BLANCO EXTREMO PARA LA TV 🔥
    val borderWidth = if (isFocused) 4.dp else 0.dp
    val borderColor = if (isFocused) Color.White else Color.Transparent

    Box(
        modifier = Modifier
            .width(140.dp)
            .height(210.dp)
            .scale(scale)
            .zIndex(if (isFocused) 1f else 0f)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .background(Color(0xFF18181B), RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .focusable()
            .clickable { onClick() }
    ) {
        if (!logoUrl.isNullOrBlank() && logoUrl != "-") {
            AsyncImage(model = logoUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(name, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
        // Velo blanco sobre la imagen para dar efecto de luz
        if (isFocused) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha=0.15f)))
        }
    }
}
