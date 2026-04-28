package com.storetd.play.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.storetd.play.core.storage.LocalAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_storetd_logo),
            contentDescription = "StoreTD Play",
            modifier = Modifier.size(110.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "StoreTD Play",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Activa tu dispositivo para continuar",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    label = { Text("Codigo de activacion") },
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
                            errorMessage = "El codigo debe tener al menos 4 caracteres."
                            return@Button
                        }

                        isLoading = true
                        errorMessage = null

                        scope.launch {
                            val deviceCode = LocalAccount.getDeviceCode(context)

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

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Esta app no incluye contenido. Usa solo listas autorizadas.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
