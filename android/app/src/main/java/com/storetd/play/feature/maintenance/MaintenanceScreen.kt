package com.storetd.play.feature.maintenance

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.storetd.play.R
import com.storetd.play.core.network.RemoteAppConfig

@Composable
fun MaintenanceScreen(
    config: RemoteAppConfig,
    onRetry: () -> Unit
) {
    val context = LocalContext.current

    fun openWhatsApp() {
        if (config.supportWhatsApp.isBlank()) return

        val text = Uri.encode("Hola, necesito soporte con ${config.appName}.")
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/${config.supportWhatsApp}?text=$text")
        )

        runCatching { context.startActivity(intent) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_storetd_logo),
            contentDescription = config.appName,
            modifier = Modifier.size(110.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = config.appName,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(18.dp))

        Card {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Modo mantenimiento",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = config.maintenanceMessage,
                    style = MaterialTheme.typography.bodyLarge
                )

                Button(onClick = onRetry) {
                    Text("Reintentar")
                }

                if (config.supportWhatsApp.isNotBlank()) {
                    OutlinedButton(onClick = { openWhatsApp() }) {
                        Text("Contactar soporte")
                    }
                }
            }
        }
    }
}
