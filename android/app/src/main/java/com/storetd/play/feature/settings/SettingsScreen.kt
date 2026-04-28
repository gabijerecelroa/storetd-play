package com.storetd.play.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.storetd.play.core.storage.LocalLibrary
import com.storetd.play.core.storage.LocalSettings

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var showPinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }

    var infoMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Text("Configuracion", style = MaterialTheme.typography.headlineMedium)
        Text("Preferencias, control parental y limpieza local.")

        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Control parental", style = MaterialTheme.typography.titleMedium)
                Text("PIN actual configurado. PIN inicial por defecto: 1234.")

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { showPinDialog = true }) {
                        Text("Cambiar PIN")
                    }

                    OutlinedButton(
                        onClick = {
                            LocalSettings.setAdultContentHidden(context, true)
                            infoMessage = "Contenido adulto oculto nuevamente."
                        }
                    ) {
                        Text("Ocultar adultos")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Limpieza local", style = MaterialTheme.typography.titleMedium)
                Text("Estas acciones solo afectan este dispositivo.")

                Button(
                    onClick = {
                        LocalLibrary.clearHistory(context)
                        infoMessage = "Historial limpiado."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Limpiar historial")
                }

                OutlinedButton(
                    onClick = {
                        LocalLibrary.clearFavorites(context)
                        infoMessage = "Favoritos limpiados."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Limpiar favoritos")
                }

                OutlinedButton(
                    onClick = {
                        LocalSettings.resetSettings(context)
                        infoMessage = "Configuracion restaurada. PIN vuelve a 1234."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restaurar configuracion")
                }
            }
        }

        infoMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = it,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onBack) {
            Text("Volver")
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                newPin = ""
                pinMessage = null
            },
            title = { Text("Cambiar PIN") },
            text = {
                Column {
                    Text("Ingresa un nuevo PIN de 4 a 8 numeros.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { value ->
                            newPin = value.filter { it.isDigit() }.take(8)
                        },
                        label = { Text("Nuevo PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    pinMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPin.length < 4) {
                            pinMessage = "El PIN debe tener al menos 4 numeros."
                        } else {
                            LocalSettings.setPin(context, newPin)
                            infoMessage = "PIN actualizado correctamente."
                            showPinDialog = false
                            newPin = ""
                            pinMessage = null
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                        newPin = ""
                        pinMessage = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
