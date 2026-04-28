package com.storetd.play.feature.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storetd.play.BuildConfig

@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text("Soporte", style = MaterialTheme.typography.headlineMedium)
        Text("Envia diagnostico, limpia cache o contacta soporte.")

        Button(onClick = {
            val url = "https://wa.me/${BuildConfig.SUPPORT_WHATSAPP}"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }) {
            Text("Contactar por WhatsApp")
        }

        Button(onClick = {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${BuildConfig.SUPPORT_EMAIL}")
                putExtra(Intent.EXTRA_SUBJECT, "Soporte StoreTD Play")
                putExtra(Intent.EXTRA_TEXT, "Describe el problema y el canal afectado.")
            }
            context.startActivity(intent)
        }) {
            Text("Enviar email")
        }

        Button(onClick = onBack) { Text("Volver") }
    }
}
