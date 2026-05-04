package com.storetd.play.feature.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.storetd.play.BuildConfig
import com.storetd.play.core.storage.LocalAccount

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val account = remember { LocalAccount.getAccount(context) }
    var message by remember { mutableStateOf<String?>(null) }

    fun copyText(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        message = "$label copiado."
    }

    fun openRenewWhatsApp() {
        val phone = BuildConfig.SUPPORT_WHATSAPP.ifBlank { "5493718698291" }
        val text = Uri.encode(
            "Hola, quiero renovar mi servicio StoreTD Play.\n\n" +
                "Cliente: ${account.customerName}\n" +
                "Estado: ${account.status}\n" +
                "Vence: ${account.expiresAt}\n" +
                "Dispositivo: ${account.deviceCode}"
        )

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=$text"))
        runCatching { context.startActivity(intent) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Mi cuenta", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Informacion del cliente, dispositivo y servicio asignado.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Cliente", style = MaterialTheme.typography.titleMedium)
                Text("Nombre: ${account.customerName}")
                Text("Codigo de activacion: ${account.activationCode}")
                Text("Estado: ${account.status}")
                Text("Vencimiento: ${account.expiresAt}")
                Text("Dispositivos: ${account.deviceCount}/${account.maxDevices}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Servicio asignado", style = MaterialTheme.typography.titleMedium)

                if (account.playlistUrl.isBlank()) {
                    Text("Lista M3U: no asignada")
                } else {
                    Text("Lista M3U: asignada")
                }

                if (account.epgUrl.isBlank()) {
                    Text("EPG: no asignada")
                } else {
                    Text("EPG: asignada")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Dispositivo", style = MaterialTheme.typography.titleMedium)
                Text("Codigo: ${account.deviceCode}")
                Text("Este codigo sirve para soporte, renovacion o vinculacion futura con backend.")

                Button(
                    onClick = { copyText("Codigo de dispositivo", account.deviceCode) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copiar codigo de dispositivo")
                }
            }
        }

        message?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { openRenewWhatsApp() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Renovar por WhatsApp")
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver")
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                LocalAccount.logout(context)
                onLogout()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesion")
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
