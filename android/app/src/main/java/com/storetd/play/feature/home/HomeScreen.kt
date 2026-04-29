package com.storetd.play.feature.home

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.storetd.play.R
import com.storetd.play.core.storage.LocalAccount

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

    val mainActions = listOf(
        HomeAction("TV en vivo", "Canales, categorías y zapping", "LIVE", onOpenLiveTv),
        HomeAction("Películas", "Cine y contenido VOD", "VOD", onOpenMovies),
        HomeAction("Series", "Temporadas, carpetas y capítulos", "SERIES", onOpenSeries)
    )

    val secondaryActions = listOf(
        HomeAction("Guía EPG", "Programación actual", "GUÍA", onOpenEpg),
        HomeAction("Favoritos", "Tus contenidos guardados", "FAV", onOpenFavorites),
        HomeAction("Últimos vistos", "Continúa donde quedaste", "HIST", onOpenHistory),
        HomeAction("Mi cuenta", "Estado y dispositivo", "CUENTA", onOpenAccount),
        HomeAction("Configuración", "Caché, PIN y ajustes", "AJUSTES", onOpenSettings)
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(22.dp)
    ) {
        val isTvWide = maxWidth >= 700.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Header(
                    customerName = customerName,
                    onOpenSupport = onOpenSupport
                )
            }

            item {
                HeroBanner()
            }

            item {
                Text(
                    text = "Principales",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isTvWide) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        mainActions.forEachIndexed { index, action ->
                            TvHomeCard(
                                action = action,
                                large = true,
                                requestInitialFocus = index == 0,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                items(mainActions.size) { index ->
                    TvHomeCard(
                        action = mainActions[index],
                        large = true,
                        requestInitialFocus = index == 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Text(
                    text = "Accesos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isTvWide) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        secondaryActions.take(3).forEach { action ->
                            TvHomeCard(
                                action = action,
                                large = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        secondaryActions.drop(3).forEach { action ->
                            TvHomeCard(
                                action = action,
                                large = false,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            } else {
                items(secondaryActions.size) { index ->
                    TvHomeCard(
                        action = secondaryActions[index],
                        large = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Text(
                    text = "StoreTD Play · Reproductor privado para contenido autorizado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun Header(
    customerName: String,
    onOpenSupport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_storetd_logo),
            contentDescription = "StoreTD Play",
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "StoreTD Play",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Text(
                text = "Bienvenido $customerName",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(onClick = onOpenSupport) {
            Text("Soporte")
        }
    }
}

@Composable
private fun HeroBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 115.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Contenido autorizado",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            Text(
                text = "Elegí una sección con el control remoto",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TvHomeCard(
    action: HomeAction,
    large: Boolean,
    requestInitialFocus: Boolean = false,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(24.dp)

    if (requestInitialFocus) {
        LaunchedEffect(Unit) {
            runCatching {
                focusRequester.requestFocus()
            }
        }
    }

    Surface(
        modifier = modifier
            .height(if (large) 168.dp else 128.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
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
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                },
                shape = shape
            )
            .focusable()
            .clickable { action.onClick() },
        color = if (focused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shadowElevation = if (focused) 14.dp else 5.dp,
        tonalElevation = if (focused) 8.dp else 2.dp,
        shape = shape
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            Text(
                text = action.title,
                style = if (large) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleLarge
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = action.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = if (focused) "OK para abrir" else "Abrir",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
