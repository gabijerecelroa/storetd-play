package com.storetd.play.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.storetd.play.R
import com.storetd.play.core.network.ActivationApi
import com.storetd.play.core.device.DeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.storetd.play.ui.components.premiumStoreTdBackground

@Composable
fun ActivationScreen(
    onActivate: (
        customerName: String,
        activationCode: String,
        status: String,
        expiresAt: String,
        playlistUrl: String,
        epgUrl: String,
        maxDevices: Int,
        deviceCount: Int
    ) -> Unit,
    onDemo: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var customerName by remember { mutableStateOf("") }
    var activationCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .premiumStoreTdBackground()
            .navigationBarsPadding()
            .padding(22.dp)
    ) {
        val isTvWide = maxWidth >= 700.dp
        val contentWidth = if (isTvWide) 0.82f else 1f

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isTvWide) 12.dp else 16.dp)
        ) {
            item {
                Image(
                    painter = painterResource(id = R.drawable.ic_storetd_logo),
                    contentDescription = "StoreTD Play",
                    modifier = Modifier.size(if (isTvWide) 74.dp else 96.dp)
                )
            }

            item {
                Text(
                    text = "StoreTD Play",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Text(
                    text = "Activa tu dispositivo para continuar",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f)
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(contentWidth)) {
                    Column(
                        modifier = Modifier.padding(if (isTvWide) 18.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            label = { Text("Nombre del cliente") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )

                        OutlinedTextField(
                            value = activationCode,
                            onValueChange = { activationCode = it.uppercase().take(20) },
                            label = { Text("Código de activación") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )

                        errorMessage?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (isLoading) {
                            CircularProgressIndicator()
                        }

                        Button(
                            onClick = {
                                if (customerName.trim().length < 2) {
                                    errorMessage = "Ingresa el nombre del cliente."
                                    return@Button
                                }

                                if (activationCode.trim().length < 4) {
                                    errorMessage = "El código debe tener al menos 4 caracteres."
                                    return@Button
                                }

                                isLoading = true
                                errorMessage = null

                                scope.launch {
                                    val deviceCode = DeviceIdentity.getOrCreateDeviceCode(context)

                                    val result = withContext(Dispatchers.IO) {
                                        ActivationApi.activate(
                                            customerName = customerName.trim(),
                                            activationCode = activationCode.trim(),
                                            deviceCode = deviceCode
                                        )
                                    }

                                    isLoading = false

                                    if (result.success) {
                                        onActivate(
                                            result.customerName ?: customerName.trim(),
                                            result.activationCode ?: activationCode.trim(),
                                            result.status ?: "Activa",
                                            result.expiresAt ?: "",
                                            result.playlistUrl ?: "",
                                            result.epgUrl ?: "",
                                            result.maxDevices ?: 1,
                                            result.deviceCount ?: 1
                                        )
                                    } else {
                                        errorMessage = result.message
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Text("Activar con backend")
                        }

                        OutlinedButton(
                            onClick = onDemo,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Text("Entrar en modo demo")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Esta app no incluye contenido. Usa solo listas autorizadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                    modifier = Modifier.fillMaxWidth(contentWidth)
                )
            }

            item {
                Spacer(modifier = Modifier.padding(bottom = 12.dp))
            }
        }
    }
}
