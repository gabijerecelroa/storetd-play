package com.storetd.play.feature.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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

private enum class EpgTab {
    Now,
    Upcoming,
    All
}

@OptIn(ExperimentalLayoutApi::class)
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
    var tab by remember { mutableStateOf(EpgTab.Now) }
    var programmes by remember { mutableStateOf(emptyList<EpgProgram>()) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var lastUpdatedAt by remember { mutableStateOf(LocalEpgCache.lastUpdatedAt(context)) }

    fun loadCacheAsync() {
        loading = true
        message = "Leyendo EPG guardada..."

        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                val cachedXml = LocalEpgCache.load(context)
                if (cachedXml.isBlank()) emptyList() else XmlTvParser.parse(cachedXml)
            }

            loading = false
            nowMillis = System.currentTimeMillis()
            programmes = parsed
            lastUpdatedAt = LocalEpgCache.lastUpdatedAt(context)

            message = when {
                parsed.isNotEmpty() -> "EPG cargada desde caché local."
                epgUrl.isNotBlank() -> "Toca Actualizar EPG para descargar programación."
                else -> "Pega una URL XMLTV autorizada y toca Actualizar EPG."
            }
        }
    }

    fun updateEpg(url: String) {
        val cleanUrl = url.trim()

        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            message = "Ingresa una URL EPG válida."
            return
        }

        LocalEpgCache.rememberUrl(context, cleanUrl)
        epgUrl = cleanUrl

        loading = true
        message = "Descargando EPG. Si la fuente es grande puede tardar..."

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
            lastUpdatedAt = LocalEpgCache.lastUpdatedAt(context)

            result.onSuccess {
                programmes = it
                message = "EPG actualizada correctamente. Programas cargados: ${it.size}"
            }.onFailure { error ->
                val cached = withContext(Dispatchers.IO) {
                    val cachedXml = LocalEpgCache.load(context)
                    if (cachedXml.isBlank()) emptyList() else XmlTvParser.parse(cachedXml)
                }

                if (cached.isNotEmpty()) {
                    programmes = cached
                    message = "No se pudo actualizar. Mostrando caché local."
                } else {
                    programmes = emptyList()
                    message = error.message ?: "No se pudo cargar la EPG."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadCacheAsync()
    }

    val normalizedQuery = query.trim().lowercase(Locale.getDefault())

    val nowCount = programmes.count { it.isNow(nowMillis) }
    val upcomingCount = programmes.count { it.isUpcoming(nowMillis) }
    val totalCount = programmes.size

    val visiblePrograms = programmes
        .filter { program ->
            when (tab) {
                EpgTab.Now -> program.isNow(nowMillis)
                EpgTab.Upcoming -> program.isUpcoming(nowMillis)
                EpgTab.All -> true
            }
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
        .sortedWith(compareBy<EpgProgram> { it.startAtMillis }.thenBy { it.channelName })
        .take(180)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Text(
            text = "Guía EPG",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Programación XMLTV para contenido autorizado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Fuente EPG",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it },
                    label = { Text("URL EPG XMLTV") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Button(
                    onClick = { updateEpg(epgUrl) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Cargando..." else "Actualizar EPG")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { loadCacheAsync() },
                        enabled = !loading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Usar caché")
                    }

                    OutlinedButton(
                        onClick = {
                            LocalEpgCache.clear(context)
                            programmes = emptyList()
                            lastUpdatedAt = 0L
                            message = "Caché EPG limpiada."
                        },
                        enabled = !loading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Limpiar")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Volver")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Estado de guía",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Ahora: $nowCount") }
                    )

                    AssistChip(
                        onClick = {},
                        label = { Text("Próximos: $upcomingCount") }
                    )

                    AssistChip(
                        onClick = {},
                        label = { Text("Total: $totalCount") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (lastUpdatedAt > 0) {
                        "Última actualización: ${formatDateTime(lastUpdatedAt)}"
                    } else {
                        "Sin actualización guardada."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )

                if (message.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar canal o programa") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tab == EpgTab.Now,
                onClick = { tab = EpgTab.Now },
                label = { Text("Ahora") }
            )

            FilterChip(
                selected = tab == EpgTab.Upcoming,
                onClick = { tab = EpgTab.Upcoming },
                label = { Text("Próximo") }
            )

            FilterChip(
                selected = tab == EpgTab.All,
                onClick = { tab = EpgTab.All },
                label = { Text("Todo") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "${visiblePrograms.size} programas visibles",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (visiblePrograms.isEmpty()) {
            EmptyEpgCard(
                tab = tab,
                hasPrograms = programmes.isNotEmpty()
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = visiblePrograms,
                    key = { program ->
                        "${program.channelId}-${program.startAtMillis}-${program.title}"
                    }
                ) { program ->
                    EpgProgramCard(
                        program = program,
                        isLive = program.isNow(nowMillis)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyEpgCard(
    tab: EpgTab,
    hasPrograms: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Sin programas para mostrar",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            val text = when {
                !hasPrograms -> "Actualiza la EPG con una URL XMLTV válida o usa una fuente más liviana."
                tab == EpgTab.Now -> "No hay programas activos en este horario. Prueba la pestaña Próximo o Todo."
                tab == EpgTab.Upcoming -> "No hay próximos programas detectados. Prueba la pestaña Todo."
                else -> "No hay resultados para esta búsqueda."
            }

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EpgProgramCard(
    program: EpgProgram,
    isLive: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusable(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = program.channelName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isLive) {
                    Box(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "EN VIVO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = program.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${formatTime(program.startAtMillis)} - ${formatTime(program.stopAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f)
            )

            if (program.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = program.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                )
            }
        }
    }
}

private fun formatTime(value: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value))
}

private fun formatDateTime(value: Long): String {
    return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(value))
}
