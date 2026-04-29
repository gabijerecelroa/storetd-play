package com.storetd.play.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.storetd.play.R
import com.storetd.play.core.storage.LocalAccount
import com.storetd.play.core.preload.PlaylistPreloader
import com.storetd.play.ui.components.premiumStoreTdBackground

private data class HomeAction(
    val title: String,
    val subtitle: String,
    val badge: String,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    onOpenLiveTv: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenEpg: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenSettings: () -> Unit,
    config: Any? = null
) {
    val context = LocalContext.current
    val account = LocalAccount.getAccount(context)
    val customerName = account.customerName.ifBlank { "cliente" }

    LaunchedEffect(account.activationCode, account.playlistUrl) {
        PlaylistPreloader.preloadAccount(context.applicationContext)
    }

    val actions = listOf(
        HomeAction("TV en vivo", "Canales, categorías y zapping", "LIVE", onOpenLiveTv),
        HomeAction("Películas", "Cine y contenido VOD", "VOD", onOpenMovies),
        HomeAction("Series", "Temporadas, carpetas y capítulos", "SERIES", onOpenSeries),
        HomeAction("Guía EPG", "Programación actual", "GUÍA", onOpenEpg),
        HomeAction("Favoritos", "Tus contenidos guardados", "FAV", onOpenFavorites),
        HomeAction("Últimos vistos", "Continúa donde quedaste", "HIST", onOpenHistory),
        HomeAction("Mi cuenta", "Estado, vencimiento y dispositivo", "CUENTA", onOpenAccount),
        HomeAction("Configuración", "Caché, PIN parental y ajustes", "AJUSTES", onOpenSettings),
        HomeAction("Soporte", "Ayuda y contacto", "HELP", onOpenSupport)
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .premiumStoreTdBackground()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        val isTvWide = maxWidth >= 700.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column {
                Image(
                    painter = painterResource(id = R.drawable.ic_storetd_logo),
                    contentDescription = "StoreTD Play",
                    modifier = Modifier.size(if (isTvWide) 64.dp else 54.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "StoreTD Play",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Bienvenido $customerName",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Elegí una sección con el control remoto",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f)
                )
            }

            if (isTvWide) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(actions) { action ->
                        TvHomeCard(
                            action = action,
                            requestInitialFocus = action.title == "TV en vivo",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(actions.size) { index ->
                        TvHomeCard(
                            action = actions[index],
                            requestInitialFocus = index == 0,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Text(
                text = "StoreTD Play · Reproductor privado para contenido autorizado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
            )
        }
    }
}

@Composable
private fun TvHomeCard(
    action: HomeAction,
    requestInitialFocus: Boolean = false,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(24.dp)

    if (requestInitialFocus) {
        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Surface(
        modifier = modifier
            .height(154.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        action.onClick()
                        true
                    }
                    else -> false
                }
            }
            .border(
                width = if (focused) 4.dp else 1.dp,
                color = if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                },
                shape = shape
            )
            .focusable()
            .clickable { action.onClick() },
        color = if (focused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shadowElevation = if (focused) 14.dp else 5.dp,
        tonalElevation = if (focused) 8.dp else 2.dp,
        shape = shape,
        border = BorderStroke(
            1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.36f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = action.badge,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Text(
                text = action.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = action.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )        }
    }
}
