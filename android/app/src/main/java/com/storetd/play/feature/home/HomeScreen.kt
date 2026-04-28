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
    val action: () -> Unit
)

@Composable
fun HomeScreen(
    onOpenLiveTv: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenSettings: () -> Unit,
    config: RemoteAppConfig = RemoteAppConfig()
) {
    val items = listOf(
        HomeItem("TV en vivo", "Canales, categorias y zapping", onOpenLiveTv),
        HomeItem("Favoritos", "Tus canales guardados", onOpenFavorites),
        HomeItem("Ultimos vistos", "Continua donde quedaste", onOpenHistory),
        HomeItem("Mi cuenta", "Estado, vencimiento y cliente", onOpenAccount),
        HomeItem("Soporte", "WhatsApp, email y diagnostico", onOpenSupport),
        HomeItem("Configuracion", "Tema, cache y preferencias", onOpenSettings)
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

            Spacer(modifier = Modifier.height(24.dp))

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
                            text = "Actualizacion requerida",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Version minima requerida: ${config.minimumAppVersion}. Version instalada: ${BuildConfig.VERSION_NAME}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = config.welcomeTitle,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = config.welcomeMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(items) { item ->
                    HomeCard(item = item)
                }
            }

            Text(
                text = config.providerMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
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
private fun HomeCard(item: HomeItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .focusable(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
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
