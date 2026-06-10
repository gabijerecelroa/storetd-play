package com.storetd.play.feature.home

import android.content.Context
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
import com.storetd.play.core.storage.LocalAppConfig
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
    config: com.storetd.play.core.storage.AppConfig? = null // Ignoramos el del NavHost y leemos el local
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    
    val localConfig = remember { LocalAppConfig.get(context) }
    val appNameSafe = localConfig.appName ?: "STORE TD"
    
    var history by remember { mutableStateOf<List<SavedChannel>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<SavedChannel>>(emptyList()) }
    
    var estrenos by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var peliculasVistas by remember { mutableStateOf<List<Channel>>(emptyList()) }

    // Motor de extracción SUPER SEGURO (Buscando canales directos, sin usar clases "Lite")
    LaunchedEffect(Unit) {
        history = LocalLibrary.history(context).take(15)
        favorites = LocalLibrary.favorites(context).take(15)

        withContext(Dispatchers.IO) {
            try {
                val acc = LocalAccount.getAccount(context)
                val code = acc.activationCode
                
                // Pedimos TODAS las películas juntas para no fallar con los IDs
                val allMovies = OptimizedContentApi.loadSection(code, "movie").take(150)
                if (allMovies.isNotEmpty()) {
                    estrenos = allMovies.shuffled().take(20) // Simulamos los estrenos mezclando
                    peliculasVistas = allMovies.take(20)     // Simulamos las más vistas tomando las primeras
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
                            Text("🎬 BIENVENIDO A ${appNameSafe.uppercase()}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("El mejor entretenimiento\npara tu familia", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Navegá por el menú lateral para descubrir la cartelera completa", color = Color.LightGray, fontSize = 16.sp)
                        }
                    }
                }

                if (history.isNotEmpty()) {
                    item { CarouselSectionSaved("⏱️ Continuar Viendo", history) { onOpenContinueItem(it) } }
                }

                if (estrenos.isNotEmpty()) {
                    item { CarouselSection("🔥 Recomendados para ti", estrenos) { ch -> 
                        onOpenContinueItem(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Peliculas", null))
                    } }
                }

                if (peliculasVistas.isNotEmpty()) {
                    item { CarouselSection("⭐ Películas Más Vistas", peliculasVistas) { ch -> 
                        onOpenContinueItem(SavedChannel(ch.id, ch.name, ch.streamUrl, ch.logoUrl, ch.group ?: "Peliculas", null))
                    } }
                }
                
                if (favorites.isNotEmpty()) {
                    item { CarouselSectionSaved("❤️ Mi Lista de Favoritos", favorites) { onOpenContinueItem(it) } }
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
            items(items) { item -> MovieCard(item.name, item.logoUrl) { onClick(item) } }
        }
    }
}

@Composable
fun CarouselSectionSaved(title: String, items: List<SavedChannel>, onClick: (SavedChannel) -> Unit) {
    Column(modifier = Modifier.padding(top = 36.dp)) {
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
            AsyncImage(model = logoUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(name, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
        if (isFocused) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.15f)))
    }
}
