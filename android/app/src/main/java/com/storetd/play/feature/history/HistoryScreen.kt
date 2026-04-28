package com.storetd.play.feature.history

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
fun HistoryScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text("Ultimos vistos", style = MaterialTheme.typography.headlineMedium)
        Text("MVP: historial local preparado para Room/DataStore.")
        Button(onClick = onBack) { Text("Volver") }
    }
}
