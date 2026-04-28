package com.storetd.play.feature.account

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
fun AccountScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text("Mi cuenta", style = MaterialTheme.typography.headlineMedium)
        Text("En la version con backend se mostrara vencimiento, dispositivos y soporte comercial.")
        Button(onClick = onBack) { Text("Volver") }
    }
}
