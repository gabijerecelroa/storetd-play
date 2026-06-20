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
                        QuickButton("📺 TV en Vivo", onFocused = { heroTitle = "TV en Vivo"; heroSubtitle = "Mirá los canales en directo"; currentBgUrl = null }) { onOpenLiveTv() }
                        QuickButton("🎬 Películas", onFocused = { heroTitle = "Películas"; heroSubtitle = "Explorá todo nuestro catálogo"; currentBgUrl = null }) { onOpenMovies() }
                        QuickButton("🍿 Series", onFocused = { heroTitle = "Series"; heroSubtitle = "Tus temporadas favoritas"; currentBgUrl = null }) { onOpenSeries() }
                        QuickButton("❤️ Favoritos", onFocused = { heroTitle = "Favoritos"; heroSubtitle = "Tu contenido guardado"; currentBgUrl = null }) { onOpenFavorites() }
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
fun QuickButton(text: String, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")

    val bgColor = if (isFocused) Color.White else Color(0xFF27272A).copy(alpha = 0.8f)
    val textColor = if (isFocused) Color.Black else Color.White

    Box(
        modifier = Modifier
            .width(160.dp)
            .height(55.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .border(3.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
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
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // EL SECRETO PARA ELIMINAR EL TEMBLOR: key = { it.id }
            items(items, key = { it.id }) { item -> 
                MovieCard(item.name, item.logoUrl, onFocused = { onFocused(item) }) { onClick(item) } 
            }
        }
    }
}

@Composable
fun CarouselSectionSaved(title: String, items: List<SavedChannel>, onFocused: (SavedChannel) -> Unit, onClick: (SavedChannel) -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // EL SECRETO PARA ELIMINAR EL TEMBLOR: key = { it.id }
            items(items, key = { it.id }) { item -> 
                val url = item.streamUrl.lowercase()
                val group = (item.group ?: "").lowercase()
                
                // Inteligencia de formato: Si detecta que es TV, muestra un rectangulo horizontal 16:9
                val isLiveTv = url.contains("/live/") || url.contains("m3uts") || url.contains("tvclub") || group.contains("tv") || group.contains("vivo")
                
                if (isLiveTv) {
                    LandscapeCard(item.name, item.logoUrl, onFocused = { onFocused(item) }) { onClick(item) } 
                } else {
                    MovieCard(item.name, item.logoUrl, onFocused = { onFocused(item) }) { onClick(item) } 
                }
            }
        }
    }
}

@Composable
fun MovieCard(name: String, logoUrl: String?, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "scale")

    Box(
        modifier = Modifier
            .width(140.dp)
            .height(210.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (isFocused) 1f else 0f)
            .clip(RoundedCornerShape(10.dp))
            .border(3.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .background(Color(0xFF18181B))
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .clickable { onClick() }
    ) {
        if (!logoUrl.isNullOrBlank() && logoUrl != "-") {
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(logoUrl).crossfade(true).build(), contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(name, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
        if (isFocused) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha=0.15f)))
    }
}

@Composable
fun LandscapeCard(name: String, logoUrl: String?, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "scale")

    Box(
        modifier = Modifier
            .width(240.dp)
            .height(135.dp) // Diseño 16:9 perfecto para logos de TV
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (isFocused) 1f else 0f)
            .clip(RoundedCornerShape(10.dp))
            .border(3.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .background(Color(0xFF18181B))
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .clickable { onClick() }
    ) {
        if (!logoUrl.isNullOrBlank() && logoUrl != "-") {
            // Capa 1: Fondo difuminado para rellenar los bordes negros
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(logoUrl).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(12.dp).alpha(0.4f))
            // Capa 2: Logo original con ContentScale.Fit para que no se recorte nada
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(logoUrl).crossfade(true).build(), contentDescription = name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(bottom = 36.dp, top = 8.dp, start = 8.dp, end = 8.dp))
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(name, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
        
        // Degradado inferior para proteger la visibilidad del título
        Box(modifier = Modifier.fillMaxWidth().height(36.dp).align(Alignment.BottomCenter).background(Color(0xFF09090B).copy(alpha = 0.95f)))
        Text(text = name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp, start = 8.dp, end = 8.dp))

        if (isFocused) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha=0.15f)))
    }
}
