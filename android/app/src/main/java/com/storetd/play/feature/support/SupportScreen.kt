package com.storetd.play.feature.support

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.storetd.play.BuildConfig

@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun openWhatsApp() {
        val phone = BuildConfig.SUPPORT_WHATSAPP.ifBlank { "5490000000000" }
        val message = Uri.encode(
            "Hola, necesito soporte con StoreTD Play.\n\n" +
                "Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android: ${Build.VERSION.RELEASE}\n" +
                "Version app: ${BuildConfig.VERSION_NAME}"
        )

        val uri = Uri.parse("https://wa.me/$phone?text=$message")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        runCatching { context.startActivity(intent) }
    }

    fun sendEmail() {
        val body = """
            Solicitud de soporte StoreTD Play

            Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE}
            Version app: ${BuildConfig.VERSION_NAME}

            Describe el problema:
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${BuildConfig.SUPPORT_EMAIL}")
            putExtra(Intent.EXTRA_SUBJECT, "Soporte StoreTD Play")
            putExtra(Intent.EXTRA_TEXT, body)
        }

        runCatching { context.startActivity(intent) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("Soporte", style = MaterialTheme.typography.headlineMedium)
        Text("Contacta a soporte o envia diagnostico basico del dispositivo.")

        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Diagnostico del dispositivo", style = MaterialTheme.typography.titleMedium)
                Text("Marca: ${Build.MANUFACTURER}")
                Text("Modelo: ${Build.MODEL}")
                Text("Android: ${Build.VERSION.RELEASE}")
                Text("Version app: ${BuildConfig.VERSION_NAME}")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { openWhatsApp() }) {
                Text("WhatsApp")
            }

            OutlinedButton(onClick = { sendEmail() }) {
                Text("Email")
            }

            OutlinedButton(onClick = onBack) {
                Text("Volver")
            }
        }
    }
}
