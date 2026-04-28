package com.storetd.play.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text("Configuracion", style = MaterialTheme.typography.headlineMedium)
        Text("Preferencias preparadas para tema, idioma, cache, reproductor externo y control parental.")
        Button(onClick = onBack) { Text("Volver") }
    }
}
