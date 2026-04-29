package com.storetd.play.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.storetd.play.R
import com.storetd.play.core.storage.LocalAccount

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
    val customerName = account.customerName
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            HomeHeader(
                customerName = customerName,
                onSupport = onOpenSupport
            )
        }

        item {
            HeroCard(
                onLiveTv = onOpenLiveTv
            )
        }

        item {
            Text(
                text = "Explorar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PremiumSectionCard(
                    title = "TV en vivo",
                    subtitle = "Canales, categorías y zapping",
                    badge = "LIVE",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenLiveTv
                )

                PremiumSectionCard(
                    title = "Películas",
                    subtitle = "Cine y contenido VOD",
                    badge = "VOD",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenMovies
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PremiumSectionCard(
                    title = "Series",
                    subtitle = "Carpetas y capítulos",
                    badge = "SERIES",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSeries
                )

                PremiumSectionCard(
                    title = "Guía EPG",
                    subtitle = "Programación actual",
                    badge = "GUÍA",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenEpg
                )
            }
        }

        item {
            Text(
                text = "Continuar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            QuickActionsCard(
                onFavorites = onOpenFavorites,
                onHistory = onOpenHistory
            )
        }

        item {
            AccountAndSettingsCard(
                onAccount = onOpenAccount,
                onSettings = onOpenSettings
            )
        }

        item {
            Text(
                text = "StoreTD Play · Reproductor privado para contenido autorizado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun HomeHeader(
    customerName: String,
    onSupport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_storetd_logo),
            contentDescription = "StoreTD Play",
            modifier = Modifier.size(58.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "StoreTD Play",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Text(
                text = "Bienvenido ${customerName.ifBlank { "cliente" }}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(onClick = onSupport) {
            Text("Soporte")
        }
    }
}

@Composable
private fun HeroCard(
    onLiveTv: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .focusable(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 310.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "Contenido autorizado",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )

                Text(
                    text = "Tu entretenimiento en un solo lugar",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "TV en vivo, películas, series, guía EPG, favoritos e historial con experiencia premium.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                )

                Button(onClick = onLiveTv) {
                    Text("Entrar ahora")
                }
            }
        }
    }
}

@Composable
private fun PremiumSectionCard(
    title: String,
    subtitle: String,
    badge: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .focusable(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AssistChip(
                onClick = {},
                label = { Text(badge) }
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Abrir")
            }
        }
    }
}

@Composable
private fun QuickActionsCard(
    onFavorites: () -> Unit,
    onHistory: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Accesos rápidos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onFavorites,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Favoritos")
                }

                OutlinedButton(
                    onClick = onHistory,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Últimos vistos")
                }
            }
        }
    }
}

@Composable
private fun AccountAndSettingsCard(
    onAccount: () -> Unit,
    onSettings: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Cuenta y mantenimiento",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Consulta tu estado de servicio, dispositivo vinculado, caché local y control parental.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onAccount,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mi cuenta")
                }

                Button(
                    onClick = onSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Configuración")
                }
            }
        }
    }
}
