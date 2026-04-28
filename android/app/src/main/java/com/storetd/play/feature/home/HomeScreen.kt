package com.storetd.play.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.storetd.play.BuildConfig
import com.storetd.play.R
import com.storetd.play.core.network.RemoteAppConfig

private data class HomeItem(
    val title: String,
    val subtitle: String,
    val tag: String,
    val action: () -> Unit
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
    config: RemoteAppConfig = RemoteAppConfig()
) {
    val mainItems = listOf(
        HomeItem("TV en vivo", "Canales, categorías y zapping.", "LIVE", onOpenLiveTv),
        HomeItem("Películas", "Cine, estrenos y contenido VOD.", "VOD", onOpenMovies),
        HomeItem("Series", "Temporadas, episodios y colecciones.", "SERIES", onOpenSeries)
    )

    val secondaryItems = listOf(
        HomeItem("Favoritos", "Tus canales y contenidos guardados.", "GUARDADO", onOpenFavorites),
        HomeItem("Últimos vistos", "Continúa donde quedaste.", "HISTORIAL", onOpenHistory),
        HomeItem("Guía TV", "Programación actual y próximos eventos.", "EPG", onOpenEpg),
        HomeItem("Mi cuenta", "Estado, vencimiento y servicio.", "CLIENTE", onOpenAccount),
        HomeItem("Configuración", "Preferencias y mantenimiento local.", "APP", onOpenSettings)
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        val compact = maxWidth < 700.dp
        val columns = if (compact) 1 else 3

        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                appName = config.appName,
                providerMessage = config.providerMessage,
                onOpenSupport = onOpenSupport
            )

            Spacer(modifier = Modifier.height(22.dp))

            if (config.forceAppUpdate) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Actualización requerida",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Versión mínima: ${config.minimumAppVersion}. Instalada: ${BuildConfig.VERSION_NAME}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            HeroCard(
                title = config.welcomeTitle,
                message = config.welcomeMessage
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Contenido",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(mainItems + secondaryItems) { item ->
                    HomeCard(item = item)
                }
            }

            Text(
                text = config.providerMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun Header(
    appName: String,
    providerMessage: String,
    onOpenSupport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_storetd_logo),
            contentDescription = appName,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = providerMessage.ifBlank { "Streaming privado y autorizado" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
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
private fun HeroCard(
    title: String,
    message: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 5.dp,
        shadowElevation = 10.dp,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f)
            )
        }
    }
}

@Composable
private fun HomeCard(item: HomeItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .focusable(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = item.tag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Button(onClick = item.action) {
                Text("Abrir")
            }
        }
    }
}
