package com.storetd.play.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    onOpenVodDetail: (SavedChannel) -> Unit = {},
    onOpenEpg: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenSettings: () -> Unit,
    config: Any? = null
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    
    var history by remember { mutableStateOf<List<SavedChannel>>(emptyList()) }
    var estrenos by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var peliculasVistas by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var seriesDestacadas by remember { mutableStateOf<List<Channel>>(emptyList()) }

    var heroTitle by remember { mutableStateOf("STORE TD PLAY") }
    var heroSubtitle by remember { mutableStateOf("El mejor entretenimiento para tu familia") }

    LaunchedEffect(Unit) {
        history = LocalLibrary.history(context).take(15)

        withContext(Dispatchers.IO) {
            try {
                val acc = LocalAccount.getAccount(context)
                val code = acc.activationCode
                
                // 1. CARGAMOS PELÍCULAS
                val mCats = OptimizedContentApi.loadMovieCategoriesLite(code)
                if (mCats.isNotEmpty()) {
                    val estCat = mCats.find { it.title.contains("estreno", true) || it.title.contains("nuevo", true) || it.title.contains("202", true) } ?: mCats.firstOrNull()
                    if (estCat != null) estrenos = OptimizedContentApi.loadMovieCategoryItems(code, estCat.key).filter { !it.logoUrl.isNullOrBlank() && it.logoUrl != "-" }.take(20)
                    
                    val popCat = mCats.find { it.title.contains("popular", true) || it.title.contains("top", true) || it.title.contains("vista", true) } ?: mCats.getOrNull(1) ?: mCats.firstOrNull()
                    if (popCat != null && popCat.key != estCat?.key) peliculasVistas = OptimizedContentApi.loadMovieCategoryItems(code, popCat.key).filter { !it.logoUrl.isNullOrBlank() && it.logoUrl != "-" }.take(20)
                }

                // 2. CARGAMOS LAS SERIES (Usando el motor correcto para Carpetas)
                val sCats = OptimizedContentApi.loadSeriesFoldersAsChannels(code)
                if (sCats.isNotEmpty()) {
                    seriesDestacadas = sCats.filter { !it.logoUrl.isNullOrBlank() && it.logoUrl != "-" }.take(20) // Tomamos las mejores 20 con portada limpia
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
                            .graphicsLayer {
                                val s = if (isHeroFocused) 1.02f else 1f
                                scaleX = s
                                scaleY = s
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .border(4.dp, if (isHeroFocused) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(colors = listOf(Color(0xFFE50914).copy(alpha = 0.8f), Color(0xFF000000).copy(alpha = 0.3f))))
                            .onFocusChanged { 
                                isHeroFocused = it.isFocused || it.hasFocus
                                if (isHeroFocused) {
                                    heroTitle = "STORE TD PLAY"
                                    heroSubtitle = "El mejor entretenimiento para tu familia"
                                }
                            }
                            .clickable { onOpenMovies() }
                    ) {
                        Column(modifier = Modifier.align(Alignment.CenterStart).padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (heroTitle == "STORE TD PLAY") "🎬 BIENVENIDO" else "▶ SELECCIÓN ACTUAL", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text(heroTitle, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 38.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(heroSubtitle, color = Color.LightGray, fontSize = 16.sp)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        QuickButton("📺 TV en Vivo", onFocused = { heroTitle = "TV en Vivo"; heroSubtitle = "Mirá los canales en directo" }) { onOpenLiveTv() }
                        QuickButton("🎬 Películas", onFocused = { heroTitle = "Películas"; heroSubtitle = "Explorá todo nuestro catálogo" }) { onOpenMovies() }
                        QuickButton("🍿 Series", onFocused = { heroTitle = "Series"; heroSubtitle = "Tus temporadas favoritas" }) { onOpenSeries() }
                        QuickButton("❤️ Favoritos", onFocused = { heroTitle = "Favoritos"; heroSubtitle = "Tu contenido guardado" }) { onOpenFavorites() }
                    }
                }

                if (history.isNotEmpty()) item { CarouselSectionSaved("⏱️ Continuar Viendo", history, onFocused = { heroTitle = it.name; heroSubtitle = "Presioná OK para reproducir" }) { onOpenContinueItem(it) } }
                
                // NOTA: Para películas y series enviamos a "onOpenVodDetail" para mostrar la sinopsis y opciones
                if (estrenos.isNotEmpty()) item { CarouselSection("🔥 Estrenos y Recomendados", estrenos, onFocused = { heroTitle = it.name; heroSubtitle = "Película Recomendada" }) { ch -> onOpenVodDetail(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Peliculas", null)) } }
                if (peliculasVistas.isNotEmpty()) item { CarouselSection("⭐ Películas Populares", peliculasVistas, onFocused = { heroTitle = it.name; heroSubtitle = "Película Destacada" }) { ch -> onOpenVodDetail(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Peliculas", null)) } }
                
                if (seriesDestacadas.isNotEmpty()) item { 
                    CarouselSection("🍿 Series Destacadas", seriesDestacadas, onFocused = { heroTitle = it.name; heroSubtitle = "Presioná OK para elegir temporada y capítulo" }) { ch -> 
                        // LO LLEVA AL MENÚ DE ESA SERIE EXACTA
                        onOpenVodDetail(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Series", null)) 
                    } 
                }
            }
        }
    }
}

@Composable
fun QuickButton(text: String, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)
    
    val bgColor = if (isFocused) Color.White else Color(0xFFE50914)
    val textColor = if (isFocused) Color.Black else Color.White
    
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(55.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .border(4.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .background(bgColor)
            .onFocusChanged { 
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun CarouselSection(title: String, items: List<Channel>, onFocused: (Channel) -> Unit, onClick: (Channel) -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items) { item -> MovieCard(item.name, item.logoUrl, onFocused = { onFocused(item) }) { onClick(item) } }
        }
    }
}

@Composable
fun CarouselSectionSaved(title: String, items: List<SavedChannel>, onFocused: (SavedChannel) -> Unit, onClick: (SavedChannel) -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items) { item -> MovieCard(item.name, item.logoUrl, onFocused = { onFocused(item) }) { onClick(item) } }
        }
    }
}

@Composable
fun MovieCard(name: String, logoUrl: String?, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f)

    Box(
        modifier = Modifier
            .width(140.dp)
            .height(210.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (isFocused) 1f else 0f)
            .clip(RoundedCornerShape(10.dp))
            .border(4.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .background(Color(0xFF18181B))
            .onFocusChanged { 
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused() 
            }
            .clickable { onClick() }
    ) {
        if (!logoUrl.isNullOrBlank() && logoUrl != "-") {
            AsyncImage(model = logoUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(name, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
        if (isFocused) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha=0.15f)))
    }
}
