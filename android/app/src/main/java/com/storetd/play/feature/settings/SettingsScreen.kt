package com.storetd.play.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.storetd.play.core.parental.ParentalControl

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var adultHidden by remember {
        mutableStateOf(ParentalControl.isAdultContentHidden(context))
    }

    var message by remember { mutableStateOf("") }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Configuración",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Preferencias locales de la app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Control parental",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Protege categorías adultas con PIN. Por defecto el contenido adulto queda oculto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (adultHidden) {
                                "Contenido adulto: oculto"
                            } else {
                                "Contenido adulto: visible"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = if (adultHidden) {
                                "Las categorías adultas se filtran en TV, Películas y Series."
                            } else {
                                "El contenido adulto está visible hasta que vuelvas a ocultarlo."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )

                        Switch(
                            checked = !adultHidden,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showUnlockDialog = true
                                } else {
                                    ParentalControl.setAdultContentHidden(context, true)
                                    adultHidden = true
                                    message = "Contenido adulto oculto."
                                }
                            }
                        )
                    }
                }

                Button(
                    onClick = { showChangePinDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cambiar PIN parental")
                }

                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restablecer PIN a 1234")
                }

                if (ParentalControl.isUsingDefaultPin(context)) {
                    Text(
                        text = "Aviso: estás usando el PIN inicial 1234. Cámbialo antes de entregar la app a clientes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver")
        }
    }

    if (showUnlockDialog) {
        UnlockAdultDialog(
            onDismiss = { showUnlockDialog = false },
            onSuccess = {
                ParentalControl.setAdultContentHidden(context, false)
                adultHidden = false
                message = "Contenido adulto visible."
                showUnlockDialog = false
            }
        )
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onSuccess = {
                message = "PIN parental actualizado."
                showChangePinDialog = false
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Restablecer PIN") },
            text = {
                Text("Esto volverá el PIN parental a 1234 y ocultará el contenido adulto.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ParentalControl.resetToDefault(context)
                        adultHidden = true
                        message = "PIN restablecido a 1234."
                        showResetDialog = false
                    }
                ) {
                    Text("Restablecer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun UnlockAdultDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mostrar contenido adulto") },
        text = {
            Column {
                Text("Ingresa el PIN parental.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.take(8) },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (ParentalControl.verifyPin(context, pin)) {
                        onSuccess()
                    } else {
                        error = "PIN incorrecto"
                    }
                }
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var repeatPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar PIN parental") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { currentPin = it.take(8) },
                    label = { Text("PIN actual") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.take(8) },
                    label = { Text("Nuevo PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = repeatPin,
                    onValueChange = { repeatPin = it.take(8) },
                    label = { Text("Repetir nuevo PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        newPin.length < 4 -> {
                            error = "El nuevo PIN debe tener al menos 4 dígitos."
                        }

                        newPin != repeatPin -> {
                            error = "Los PIN no coinciden."
                        }

                        !ParentalControl.changePin(context, currentPin, newPin) -> {
                            error = "PIN actual incorrecto."
                        }

                        else -> {
                            onSuccess()
                        }
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
