package com.storetd.play.feature.epg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.storetd.play.core.epg.AccountEpgReader
import com.storetd.play.core.epg.EpgApi
import com.storetd.play.core.epg.EpgProgram
import com.storetd.play.core.epg.LocalEpgCache
import com.storetd.play.core.epg.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpgScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var epgUrl by remember { mutableStateOf(AccountEpgReader.epgUrl(context)) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var showNow by remember { mutableStateOf(true) }
    var programmes by remember { mutableStateOf(emptyList<EpgProgram>()) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    fun loadCacheAsync() {
        loading = true
        message = "Leyendo cache local..."

        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                val cachedXml = LocalEpgCache.load(context)
                if (cachedXml.isBlank()) emptyList() else XmlTvParser.parse(cachedXml)
            }

            loading = false
            nowMillis = System.currentTimeMillis()
            programmes = parsed

            message = if (parsed.isNotEmpty()) {
                "EPG cargada desde cache local. Programas: ${parsed.size}"
            } else if (epgUrl.isNotBlank()) {
                "URL EPG detectada. Toca Cargar EPG para actualizar."
            } else {
                "Pega una URL EPG XMLTV y toca Cargar EPG."
            }
        }
    }

    fun loadEpg(url: String) {
        val cleanUrl = url.trim()

        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            message = "Ingresa una URL EPG valida."
            return
        }

        loading = true
        message = "Descargando EPG. La primera carga puede tardar..."

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val xml = EpgApi.downloadXml(cleanUrl)
                    LocalEpgCache.save(context, cleanUrl, xml)
                    XmlTvParser.parse(xml)
                }
            }

            loading = false
            nowMillis = System.currentTimeMillis()

            result.onSuccess {
                programmes = it
                message = "EPG actualizada. Programas cargados: ${it.size}"
            }.onFailure { error ->
                val cached = withContext(Dispatchers.IO) {
                    val cachedXml = LocalEpgCache.load(context)
                    if (cachedXml.isBlank()) emptyList() else XmlTvParser.parse(cachedXml)
                }

                if (cached.isNotEmpty()) {
                    programmes = cached
                    message = "No se pudo descargar. Mostrando cache local."
                } else {
                    message = error.message ?: "No se pudo cargar la EPG."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadCacheAsync()
    }

    val normalizedQuery = query.trim().lowercase(Locale.getDefault())

    val visiblePrograms = programmes
        .filter { program ->
            if (showNow) program.isNow(nowMillis) else program.isUpcoming(nowMillis)
        }
        .filter { program ->
            if (normalizedQuery.isBlank()) {
                true
            } else {
                program.channelName.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                    program.title.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                    program.description.lowercase(Locale.getDefault()).contains(normalizedQuery)
            }
        }
        .take(150)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Text("Guia EPG", style = MaterialTheme.typography.headlineMedium)
        Text("Programacion XMLTV para contenido autorizado.")

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = epgUrl,
            onValueChange = { epgUrl = it },
            label = { Text("URL EPG XMLTV") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { loadEpg(epgUrl) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Cargando..." else "Cargar EPG")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { loadCacheAsync() },
                    enabled = !loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Leer cache")
                }

                OutlinedButton(
                    onClick = {
                        LocalEpgCache.clear(context)
                        programmes = emptyList()
                        message = "Cache EPG limpiada."
                    },
                    enabled = !loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Limpiar cache")
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Volver")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar canal o programa") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = showNow,
                onClick = { showNow = true },
                label = { Text("Ahora") }
            )

            FilterChip(
                selected = !showNow,
                onClick = { showNow = false },
                label = { Text("Proximamente") }
            )
        }

        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "${visiblePrograms.size} programas visibles",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (visiblePrograms.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Sin programas para mostrar", style = MaterialTheme.typography.titleMedium)
                    Text("Carga una EPG XMLTV valida o prueba Proximamente.")
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(visiblePrograms) { program ->
                    EpgProgramCard(program = program)
                }
            }
        }
    }
}

@Composable
private fun EpgProgramCard(program: EpgProgram) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = program.channelName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = program.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${formatTime(program.startAtMillis)} - ${formatTime(program.stopAtMillis)}",
                style = MaterialTheme.typography.bodySmall
            )

            if (program.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = program.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTime(value: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value))
}
