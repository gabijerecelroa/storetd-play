package com.storetd.play.feature.support

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.storetd.play.BuildConfig
import com.storetd.play.core.network.RemoteAppConfig
import com.storetd.play.core.network.OptimizedContentApi
import com.storetd.play.core.parental.ParentalControl
import com.storetd.play.core.storage.LocalAccount
import com.storetd.play.core.storage.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private fun formatSupportSyncStatus(context: Context): String {
    val lastSuccessAt = LocalSettings.getContentSyncSuccessAt(context)
    val message = LocalSettings.getContentSyncMessage(context).ifBlank {
        "Sin sincronización confirmada"
    }

    if (lastSuccessAt <= 0L) {
        return message
    }

    val elapsedMinutes = ((System.currentTimeMillis() - lastSuccessAt) / 60000L)
        .coerceAtLeast(0L)

    val age = when {
        elapsedMinutes < 1L -> "recién"
        elapsedMinutes == 1L -> "hace 1 minuto"
        elapsedMinutes < 60L -> "hace $elapsedMinutes minutos"
        elapsedMinutes < 120L -> "hace 1 hora"
        elapsedMinutes < 1440L -> "hace ${elapsedMinutes / 60L} horas"
        elapsedMinutes < 2880L -> "ayer"
        else -> "hace ${elapsedMinutes / 1440L} días"
    }

    return "$message ($age)"
}

private fun buildSupportDiagnosticText(
    context: Context,
    config: RemoteAppConfig,
    backendSummary: String
): String {
    val account = LocalAccount.getAccount(context)
    val adultStatus = if (ParentalControl.isAdultContentHidden(context)) {
        "Oculto"
    } else {
        "Visible"
    }

    return """
        App: ${config.appName.ifBlank { "StoreTD Play" }}
        Cliente: ${account.customerName.ifBlank { "Sin activar" }}
        Código: ${account.activationCode.ifBlank { "-" }}
        Estado: ${account.status.ifBlank { "-" }}
        Vencimiento: ${account.expiresAt.ifBlank { "-" }}
        Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}
        Android: ${Build.VERSION.RELEASE}
        Versión app: ${BuildConfig.VERSION_NAME}
        Adultos: $adultStatus
        Última sincronización: ${formatSupportSyncStatus(context)}
        Backend/contenido: ${backendSummary.ifBlank { "No probado" }}
    """.trimIndent()
}


@Composable
fun SupportScreen(
    onBack: () -> Unit,
    config: RemoteAppConfig = RemoteAppConfig()
) {
    val context = LocalContext.current
    val supportScope = rememberCoroutineScope()

    var backendDiagnostic by remember { mutableStateOf("") }
    var supportMessage by remember { mutableStateOf("") }
    var isDiagnosticRunning by remember { mutableStateOf(false) }

    val diagnosticText = buildSupportDiagnosticText(
        context = context,
        config = config,
        backendSummary = backendDiagnostic
    )

    fun copyDiagnostic() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("StoreTD Play diagnóstico", diagnosticText)
        )
        supportMessage = "Diagnóstico copiado."
    }

    fun updateDiagnostic() {
        if (isDiagnosticRunning) return

        val account = LocalAccount.getAccount(context)
        val activationCode = account.activationCode.trim()

        if (activationCode.isBlank()) {
            backendDiagnostic = "Sin código de activación."
            supportMessage = "No hay código de activación."
            return
        }

        isDiagnosticRunning = true
        backendDiagnostic = "Probando conexión..."
        supportMessage = "Actualizando diagnóstico..."

        supportScope.launch {
            val includeAdult = !ParentalControl.isAdultContentHidden(context)

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val liveCount = OptimizedContentApi.loadSection(
                        activationCode = activationCode,
                        section = "live",
                        includeAdult = includeAdult
                    ).size

                    val movieCategories = OptimizedContentApi.loadMovieCategoriesLite(
                        activationCode = activationCode,
                        includeAdult = includeAdult
                    )

                    val movieCount = movieCategories.sumOf { it.itemCount }

                    val seriesFolders = OptimizedContentApi.loadSeriesFoldersLite(
                        activationCode = activationCode,
                        includeAdult = includeAdult
                    )

                    val episodeCount = seriesFolders.sumOf { it.episodeCount }

                    "Backend conectado. TV: $liveCount · Películas: $movieCount · Series: ${seriesFolders.size} carpetas / $episodeCount episodios"
                }.getOrElse {
                    "No se pudo conectar con el backend."
                }
            }

            backendDiagnostic = result
            supportMessage = "Diagnóstico actualizado."
            isDiagnosticRunning = false
        }
    }

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
                diagnosticText +
                "\n\nDescribe el problema:"
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

            $diagnosticText

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
                Text("Diagnóstico para soporte", style = MaterialTheme.typography.titleMedium)

                Text(
                    text = diagnosticText,
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { updateDiagnostic() },
                        enabled = !isDiagnosticRunning
                    ) {
                        Text(
                            if (isDiagnosticRunning) {
                                "Probando..."
                            } else {
                                "Actualizar diagnóstico"
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = { copyDiagnostic() }
                    ) {
                        Text("Copiar diagnóstico")
                    }
                }

                if (supportMessage.isNotBlank()) {
                    Text(
                        text = supportMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
