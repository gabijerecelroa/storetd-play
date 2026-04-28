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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.storetd.play.core.network.RemoteAppConfig

@Composable
fun SupportScreen(
    onBack: () -> Unit,
    config: RemoteAppConfig = RemoteAppConfig()
) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        if (url.isBlank()) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun openWhatsApp() {
        val phone = config.supportWhatsApp.ifBlank { BuildConfig.SUPPORT_WHATSAPP }

        if (phone.isBlank()) return

        val message = Uri.encode(
            "Hola, necesito soporte con ${config.appName}.\n\n" +
                "Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android: ${Build.VERSION.RELEASE}\n" +
                "Version app: ${BuildConfig.VERSION_NAME}"
        )

        val uri = Uri.parse("https://wa.me/$phone?text=$message")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        runCatching { context.startActivity(intent) }
    }

    fun sendEmail() {
        val email = config.supportEmail.ifBlank { BuildConfig.SUPPORT_EMAIL }

        if (email.isBlank()) return

        val body = """
            Solicitud de soporte ${config.appName}

            Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE}
            Version app: ${BuildConfig.VERSION_NAME}

            Describe el problema:
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, "Soporte ${config.appName}")
            putExtra(Intent.EXTRA_TEXT, body)
        }

        runCatching { context.startActivity(intent) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Contacto", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { openWhatsApp() },
                        enabled = config.supportWhatsApp.isNotBlank() || BuildConfig.SUPPORT_WHATSAPP.isNotBlank()
                    ) {
                        Text("WhatsApp")
                    }

                    OutlinedButton(
                        onClick = { sendEmail() },
                        enabled = config.supportEmail.isNotBlank() || BuildConfig.SUPPORT_EMAIL.isNotBlank()
                    ) {
                        Text("Email")
                    }
                }

                Button(
                    onClick = { openUrl(config.renewUrl) },
                    enabled = config.renewUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Renovar servicio")
                }

                OutlinedButton(
                    onClick = { openUrl(config.termsUrl) },
                    enabled = config.termsUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Terminos y condiciones")
                }

                OutlinedButton(
                    onClick = { openUrl(config.privacyUrl) },
                    enabled = config.privacyUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Politica de privacidad")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(onClick = onBack) {
            Text("Volver")
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
