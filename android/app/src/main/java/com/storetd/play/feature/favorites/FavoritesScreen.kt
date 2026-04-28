package com.storetd.play.feature.favorites

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.storetd.play.core.storage.LocalLibrary
import com.storetd.play.core.storage.SavedChannel

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onPlay: (SavedChannel) -> Unit
) {
    val context = LocalContext.current
    var favorites by remember { mutableStateOf(LocalLibrary.favorites(context)) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Favoritos", style = MaterialTheme.typography.headlineMedium)
        Text("Canales guardados localmente en este dispositivo.")
        Spacer(Modifier.height(16.dp))

        Button(onClick = onBack) {
            Text("Volver")
        }

        Spacer(Modifier.height(16.dp))

        if (favorites.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Todavia no tienes favoritos. Abre un canal y toca Favorito.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(favorites) { channel ->
                    FavoriteRow(
                        channel = channel,
                        onPlay = { onPlay(channel) },
                        onRemove = {
                            LocalLibrary.removeFavorite(context, channel.streamUrl)
                            favorites = LocalLibrary.favorites(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    channel: SavedChannel,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp)) {
            if (!channel.logoUrl.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(channel.logoUrl),
                    contentDescription = channel.name,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                AssistChip(onClick = {}, label = { Text(channel.group) })
            }

            Button(onClick = onPlay) {
                Text("Ver")
            }

            Spacer(Modifier.width(8.dp))

            Button(onClick = onRemove) {
                Text("Quitar")
            }
        }
    }
}
