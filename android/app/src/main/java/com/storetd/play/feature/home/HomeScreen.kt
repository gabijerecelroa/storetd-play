package com.storetd.play.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
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
import coil.request.ImageRequest
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
    var currentBgUrl by remember { mutableStateOf<String?>(null) }

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
                    if (estCat != null) estrenos = OptimizedContentApi.loadMovieCategoryItems(code, estCat.key).filter { !it.logoUrl.isNullOrBlank() && it.logoUrl != "-" }.shuffled().take(20)

                    val popCat = mCats.find { it.title.contains("popular", true) || it.title.contains("top", true) || it.title.contains("vista", true) } ?: mCats.getOrNull(1) ?: mCats.firstOrNull()
                    if (popCat != null && popCat.key != estCat?.key) peliculasVistas = OptimizedContentApi.loadMovieCategoryItems(code, popCat.key).filter { !it.logoUrl.isNullOrBlank() && it.logoUrl != "-" }.shuffled().take(20)
                }

                // 2. CARGAMOS LAS SERIES RECOMENDADAS
                val sCats = OptimizedContentApi.loadSeriesFoldersAsChannels(code)
                if (sCats.isNotEmpty()) {
                    seriesDestacadas = sCats.shuffled().take(20)
                }

            } catch(e: Exception) { e.printStackTrace() } finally { isLoading = false }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF09090B))) {
        // MOTOR CINEMÁTICO: Fondo Difuminado Dinámico Animado
        Crossfade(targetState = currentBgUrl, animationSpec = tween(700), label = "bgFade") { bgUrl ->
            if (!bgUrl.isNullOrBlank() && bgUrl != "-") {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(bgUrl).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.4f).blur(16.dp)
                )
            }
        }
        
        // Degradados oscuros para que el texto y los pósters resalten perfecto
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0xFF09090B).copy(alpha = 0.85f), Color(0xFF09090B)),
                startY = 0f,
                endY = 1000f
            )
        ))
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                colors = listOf(Color(0xFF09090B).copy(alpha = 0.9f), Color.Transparent),
                startX = 0f,
                endX = 800f
            )
        ))

        if (isLoading && history.isEmpty() && estrenos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF07111B)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFE50914)) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Color(0xFF07111B)),
                contentPadding = PaddingValues(top = 42.dp, bottom = 60.dp)
            ) {
                item {
                    var isHeroFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .height(240.dp)
                            .graphicsLayer {
                                val s = if (isHeroFocused) 1.02f else 1f
                                scaleX = s
                                scaleY = s
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .border(3.dp, if (isHeroFocused) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
                            .background(if (isHeroFocused) Color.White.copy(alpha=0.1f) else Color.Transparent)
                            .onFocusChanged {
                                isHeroFocused = it.isFocused || it.hasFocus
                                if (isHeroFocused) {
                                    heroTitle = "STORE TD PLAY"
                                    heroSubtitle = "El mejor entretenimiento para tu familia"
                                    currentBgUrl = null
                                }
                            }
                            .clickable { onOpenMovies() }
                    ) {
                        Column(modifier = Modifier.align(Alignment.BottomStart).padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (heroTitle == "STORE TD PLAY") "🎬 BIENVENIDO" else "▶ SELECCIÓN ACTUAL", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text(heroTitle, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 46.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(heroSubtitle, color = Color.LightGray, fontSize = 16.sp)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TopBarItem("Inicio", "🏠", isSelected = true, onFocused = { heroTitle = "Inicio"; heroSubtitle = "Panel Principal"; currentBgUrl = null }) { }
                        TopBarItem("TV en vivo", "▶", onFocused = { heroTitle = "TV en Vivo"; heroSubtitle = "Canales en directo"; currentBgUrl = null }) { onOpenLiveTv() }
                        TopBarItem("Películas", "★", onFocused = { heroTitle = "Películas"; heroSubtitle = "Explorá todo nuestro catálogo"; currentBgUrl = null }) { onOpenMovies() }
                        TopBarItem("Serie", "≡", onFocused = { heroTitle = "Series"; heroSubtitle = "Tus temporadas favoritas"; currentBgUrl = null }) { onOpenSeries() }
                        TopBarItem("Downloads", "↓", onFocused = { heroTitle = "Descargas"; heroSubtitle = "Contenido sin conexión"; currentBgUrl = null }) { }
                        TopBarItem("Guía", "ℹ", onFocused = { heroTitle = "Guía"; heroSubtitle = "Programación de TV"; currentBgUrl = null }) { onOpenEpg() }
                        TopBarItem("Buscar", "🔍", onFocused = { heroTitle = "Buscar"; heroSubtitle = "Encuentra contenido"; currentBgUrl = null }) { }
                        TopBarItem("Favoritos", "❤️", onFocused = { heroTitle = "Favoritos"; heroSubtitle = "Tu lista guardada"; currentBgUrl = null }) { onOpenFavorites() }
                    }
                }

                if (history.isNotEmpty()) item { 
                    CarouselSectionSaved("⏱️ Continuar Viendo", history, onFocused = { 
                        heroTitle = it.name; heroSubtitle = "Presioná OK para reproducir"; currentBgUrl = it.logoUrl 
                    }) { onOpenContinueItem(it) } 
                }

                if (estrenos.isNotEmpty()) item { 
                    CarouselSection("🔥 Estrenos y Recomendados", estrenos, onFocused = { 
                        heroTitle = it.name; heroSubtitle = "Película Recomendada"; currentBgUrl = it.logoUrl 
                    }) { ch -> onOpenVodDetail(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Peliculas", null)) } 
                }
                
                if (seriesDestacadas.isNotEmpty()) item {
                    CarouselSection("🍿 Series Recomendadas", seriesDestacadas, onFocused = { 
                        heroTitle = it.name; heroSubtitle = "Presioná OK para elegir temporada y capítulo"; currentBgUrl = it.logoUrl 
                    }) { ch -> onOpenVodDetail(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Series", null)) }
                }

                if (peliculasVistas.isNotEmpty()) item { 
                    CarouselSection("⭐ Películas Populares", peliculasVistas, onFocused = { 
                        heroTitle = it.name; heroSubtitle = "Película Destacada"; currentBgUrl = it.logoUrl 
                    }) { ch -> onOpenVodDetail(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Peliculas", null)) } 
                }
            }
        }
    }
}

@Composable
fun TopBarItem(text: String, icon: String, isSelected: Boolean = false, onFocused: () -> Unit, onClick: () -> Unit) { /* Menú central extirpado */ }

@Composable
fun <T> AppCategoryRow(title: String, items: List<T>, keySelector: (T) -> Any, itemContent: @Composable (T) -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)) {
        Text(text = title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items.size, key = { index -> keySelector(items[index]) }) { index ->
                itemContent(items[index])
            }
        }
    }
}

@Composable
fun AppPosterCard(imageUrl: String?, title: String, subtitle: String?, isLandscape: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")

    Column(
        modifier = Modifier
            .width(if (isLandscape) 240.dp else 140.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isLandscape) 16f/9f else 2f/3f)
                .clip(RoundedCornerShape(8.dp))
                .border(if (isFocused) 3.dp else 1.dp, if (isFocused) Color(0xFF69A8FF) else Color.White.copy(alpha=0.1f), RoundedCornerShape(8.dp))
                .background(Color(0xFF162338)) // El azul oscuro elegante de StreamVault
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize().background(Color(0xFF07111B)),
                contentScale = ContentScale.Crop
            )
            
            // 🔥 LA INSIGNIA ESTILO STREAMVAULT (Arriba a la izquierda)
            if (!isLandscape) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.TopStart)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("★", fontSize = 10.sp, color = Color(0xFFFFC766)) // Estrella amarilla
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("8.5/10", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(text = title, color = if (isFocused) Color.White else Color(0xFFBBC6D8), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(text = subtitle, color = Color(0xFF7F8DA5), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun CarouselSection(title: String, items: List<Channel>, onFocused: (Channel) -> Unit, onClick: (Channel) -> Unit) {
    AppCategoryRow(title = title, items = items, keySelector = { it.id }) { item ->
        val finalLogo = if (item.logoUrl.isNullOrBlank() || item.logoUrl == "-" || item.logoUrl.lowercase().contains("default")) {
            "http://82.39.109.213:5000/api/tmdb/poster?title=${android.net.Uri.encode(item.name)}"
        } else item.logoUrl

        AppPosterCard(imageUrl = finalLogo, title = item.name, subtitle = null, isLandscape = false, onFocused = { onFocused(item) }) { onClick(item) }
    }
}

@Composable
fun CarouselSectionSaved(title: String, items: List<SavedChannel>, onFocused: (SavedChannel) -> Unit, onClick: (SavedChannel) -> Unit) {
    AppCategoryRow(title = title, items = items, keySelector = { it.id }) { item ->
        val url = item.streamUrl.lowercase()
        val group = (item.group ?: "").lowercase()
        val isLiveTv = url.contains("/live/") || url.contains("m3uts") || url.contains("tvclub") || group.contains("tv") || group.contains("vivo")

        AppPosterCard(imageUrl = item.logoUrl, title = item.name, subtitle = if (isLiveTv) "TV en Vivo" else null, isLandscape = isLiveTv, onFocused = { onFocused(item) }) { onClick(item) }
    }
}
