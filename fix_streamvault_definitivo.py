import os
import re

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
with open(path, "r", encoding="utf-8") as f: content = f.read()

# 1. Imports necesarios para el formato 16:9 y textos
if "import androidx.compose.foundation.layout.aspectRatio" not in content:
    content = content.replace("import androidx.compose.runtime.*", "import androidx.compose.runtime.*\nimport androidx.compose.foundation.layout.aspectRatio\nimport androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.style.TextOverflow")

# 2. Reparamos el sensor de pantalla
content = re.sub(r'val isCompact\s*=\s*true', 'val isCompact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 700', content)

# 3. Extraemos el bloque de "Pantalla Completa" intacto
idx_compact = content.find("if (isCompact) {")
if idx_compact != -1:
    brace_start = content.find("{", idx_compact)
    b_count = 1
    i = brace_start + 1
    while b_count > 0 and i < len(content):
        if content[i] == '{': b_count += 1
        elif content[i] == '}': b_count -= 1
        i += 1
    end_of_compact = i
    compact_block = content[brace_start:end_of_compact]
    
    # Extraemos el else completo para reemplazarlo
    idx_else = content.find("else", end_of_compact)
    brace_else = content.find("{", idx_else)
    b_count = 1
    i = brace_else + 1
    while b_count > 0 and i < len(content):
        if content[i] == '{': b_count += 1
        elif content[i] == '}': b_count -= 1
        i += 1
    end_of_else = i
    
    # Extraemos tu llamada a los canales para no romperla
    ci_start = compact_block.find("contentItems(")
    ci_brace = compact_block.find("(", ci_start)
    b_count = 1
    i = ci_brace + 1
    while b_count > 0 and i < len(compact_block):
        if compact_block[i] == '(': b_count += 1
        elif compact_block[i] == ')': b_count -= 1
        i += 1
    content_items_call = compact_block[ci_start:i]

    # 4. Inyectamos la Lógica Bifurcada (Películas 100% vs TV 3 Columnas)
    new_logic = f"""val isTvMode = !isCompact
        if (!isTvMode || contentMode != ContentMode.LiveTv) {compact_block} else {{
            // 📺 STREAMVAULT: 3 COLUMNAS (EXCLUSIVO TV EN VIVO LANDSCAPE)
            Row(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {{
                // Columna 1: Categorías
                Column(modifier = Modifier.weight(0.22f).fillMaxHeight().padding(start = 24.dp)) {{
                    PremiumSectionHeader(mode = contentMode, refreshMessage = refreshMessage, isLoading = state.isLoading || state.isFiltering || isLazySeriesLoading || isLazyMoviesLoading)
                    if (!usingLazyBackendContent) {{
                        Spacer(Modifier.height(16.dp))
                        CategoryColumn(groups = state.groups, selectedGroup = state.selectedGroup, onSelectGroup = viewModel::selectGroup)
                    }}
                }}

                Spacer(Modifier.width(16.dp))

                // Columna 2: Lista Elegante de Canales
                LazyColumn(state = contentListState, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(0.35f)) {{
                    if (!usingLazyBackendContent) {{
                        item {{ StatusBlock(state = state, mode = contentMode) }}
                    }}
                    {content_items_call}
                }}

                Spacer(Modifier.width(16.dp))

                // Columna 3: Vista Previa 16:9
                Column(modifier = Modifier.weight(0.43f).fillMaxHeight().padding(end = 24.dp)) {{
                    androidx.compose.material3.Text("Vista previa del canal", color = androidx.compose.ui.graphics.Color(0xFFBBC6D8), fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp, top = 24.dp))
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color(0xFF0B1724)).border(1.dp, androidx.compose.ui.graphics.Color(0x264C6D95), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {{
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {{
                            androidx.compose.material3.Text("Seleccione un canal", color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            androidx.compose.material3.Text("En modo Pro, el primer clic previsualiza la transmisión y el segundo abre la reproducción completa.", color = androidx.compose.ui.graphics.Color(0xFFBBC6D8), fontSize = 12.sp, textAlign = TextAlign.Center)
                        }}
                    }}
                }}
            }}
        }}"""
    content = content[:idx_compact] + new_logic + content[end_of_else:]

# 5. Inyectamos las Categorías Finas para la TV (Textos 12.sp, 2 líneas)
cat_col_code = """@Composable
private fun CategoryColumn(groups: List<String>, selectedGroup: String, onSelectGroup: (String) -> Unit) {
    val visibleGroups = groups.filter { it.isNotBlank() }.distinct()
    if (visibleGroups.isEmpty()) return
    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        items(items = visibleGroups, key = { it.hashCode() }, contentType = { "category_item" }) { group ->
            var focused by androidx.compose.runtime.remember(group, selectedGroup) { androidx.compose.runtime.mutableStateOf(false) }
            val active = group == selectedGroup || focused
            val scale by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.05f else 1f, label = "scale")
            val bgColor = if (active) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent
            val textColor = if (active) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color(0xFFBBC6D8)
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp).graphicsLayer { scaleX = scale; scaleY = scale }.onFocusChanged { focused = it.isFocused || it.hasFocus }.clickable { onSelectGroup(group) },
                color = bgColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), border = if (!active) androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x264C6D95)) else null
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    androidx.compose.material3.Text(text = group, color = textColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
"""
if "fun CategoryColumn" not in content:
    content = content.replace("@Composable\nprivate fun CategoryRow", cat_col_code + "\n@Composable\nprivate fun CategoryRow")

# 6. El Hack Definitivo de los Canales (Forzamos a 1 Columna SÓLO en TV en Vivo)
content = re.sub(
    r"val cols = if \(LiveTvBgState\.isCompact\) (\d+) else (\d+)",
    r"val cols = if (contentMode == ContentMode.LiveTv && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700) 1 else if (LiveTvBgState.isCompact) \1 else \2",
    content
)

with open(path, "w", encoding="utf-8") as f: f.write(content)
print("✅ ¡Arquitectura Bifurcada! Películas libres y TV PRO inyectada.")
