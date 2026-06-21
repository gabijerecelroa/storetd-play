import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

def replace_between(start_str, end_str, new_code):
    global content
    start_idx = content.find(start_str)
    end_idx = content.find(end_str, start_idx)
    if start_idx != -1 and end_idx != -1:
        content = content[:start_idx] + new_code + "\n\n" + content[end_idx:]
    else:
        print(f"⚠️ Error encontrando el bloque: {start_str[:30]}...")

# Botón Central de Pausa/Play
replace_between(
    "@Composable\nprivate fun PlayerCenterControl",
    "@Composable\nprivate fun ReportDialog",
    """@Composable\nprivate fun PlayerCenterControl(selected: Boolean, isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {\n    val bgColor = if (selected) Color(0xFF69A8FF) else Color(0xFF07111B).copy(alpha = 0.75f)\n    val textColor = if (selected) Color(0xFF07111B) else Color.White\n    Surface(modifier = modifier.clickable { onClick() }, color = bgColor, shape = RoundedCornerShape(50), border = BorderStroke(2.dp, if (selected) Color.White else Color.Transparent)) {\n        Text(text = if (isPlaying) "⏸ Pausa" else "▶ Reproducir", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))\n    }\n}"""
)

# Capa Inferior de Controles (Título, EPG, Barra)
replace_between(
    "@Composable\nprivate fun PlayerBottomOverlay",
    "@Composable\nprivate fun PlayerControlChip",
    """@Composable\nprivate fun PlayerBottomOverlay(\n    channel: SavedChannel, isFavorite: Boolean, canPrevious: Boolean, canNext: Boolean, resizeModeLabel: String, currentProgram: EpgProgram?, nextProgram: EpgProgram?, isLandscape: Boolean, isVodContent: Boolean, currentPositionMs: Long, durationMs: Long, selectedControlIndex: Int, onPrevious: () -> Unit, onNext: () -> Unit, onFavorite: () -> Unit, onReport: () -> Unit, onRetry: () -> Unit, onChangeResizeMode: () -> Unit, onBack: () -> Unit\n) {\n    Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xFF07111B).copy(alpha = 0.95f)))).padding(horizontal = if (isLandscape) 48.dp else 16.dp, vertical = 24.dp)) {\n        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {\n            Column {\n                Text(text = channel.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)\n                Text(text = "${channel.group} • $resizeModeLabel", color = Color(0xFFBBC6D8), fontSize = 14.sp)\n                PlayerEpgInfo(currentProgram, nextProgram)\n            }\n            if (isVodContent) { PlayerProgressBar(currentPositionMs, durationMs) }\n            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {\n                PlayerControlChip("⏮ Ant", selectedControlIndex == 0, canPrevious, onPrevious)\n                PlayerControlChip(if (isFavorite) "❤️ Quitar" else "🤍 Fav", selectedControlIndex == 1, true, onFavorite)\n                PlayerControlChip("⚠️ Rep", selectedControlIndex == 2, true, onReport)\n                PlayerControlChip("Sig ⏭", selectedControlIndex == 3, canNext, onNext)\n                PlayerControlChip("⛶ Ajustar", selectedControlIndex == 4, true, onChangeResizeMode)\n            }\n        }\n    }\n}"""
)

# Botones Píldora Inferiores
replace_between(
    "@Composable\nprivate fun PlayerControlChip",
    "@Composable\nprivate fun PlayerProgressBar",
    """@Composable\nprivate fun PlayerControlChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {\n    val bgColor = if (selected) Color(0xFF69A8FF) else Color(0xFF162338).copy(alpha = 0.8f)\n    val textColor = if (selected) Color(0xFF07111B) else Color(0xFFBBC6D8)\n    Surface(color = if (enabled) bgColor else Color.Transparent, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, if (selected) Color.Transparent else Color(0x264C6D95)), modifier = Modifier.clickable(enabled = enabled) { onClick() }) {\n        Text(text = label, color = textColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))\n    }\n}"""
)

# Barra de Progreso Azul StreamVault
replace_between(
    "@Composable\nprivate fun PlayerProgressBar",
    "@Composable\nprivate fun PlayerEpgInfo",
    """@Composable\nprivate fun PlayerProgressBar(currentPositionMs: Long, durationMs: Long) {\n    val progress = (currentPositionMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)\n    Column(modifier = Modifier.fillMaxWidth()) {\n        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF223754), RoundedCornerShape(50))) {\n            Box(modifier = Modifier.fillMaxWidth(progress).height(4.dp).background(Color(0xFF69A8FF), RoundedCornerShape(50)))\n        }\n        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {\n            Text(formatPlaybackTime(currentPositionMs), color = Color(0xFFBBC6D8), fontSize = 12.sp)\n            Text(formatPlaybackTime(durationMs), color = Color(0xFFBBC6D8), fontSize = 12.sp)\n        }\n    }\n}"""
)

# Botón Volver 
replace_between(
    "@Composable\nprivate fun PlayerPortraitBackButton",
    "private fun friendlyPlaybackErrorMessage",
    """@Composable\nprivate fun PlayerPortraitBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {\n    Surface(modifier = modifier.clickable { onBack() }.padding(16.dp), color = Color(0xFF162338).copy(alpha = 0.9f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Color(0x264C6D95))) {\n        Text("⬅ Volver", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))\n    }\n}"""
)

# Inyectar importaciones necesarias de Compose
imports = [
    "import androidx.compose.ui.graphics.Color",
    "import androidx.compose.ui.graphics.Brush",
    "import androidx.compose.ui.unit.sp",
    "import androidx.compose.foundation.BorderStroke",
    "import androidx.compose.foundation.horizontalScroll"
]
for imp in imports:
    if imp not in content:
        content = content.replace("import androidx.compose.runtime.*", f"import androidx.compose.runtime.*\n{imp}")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ ¡Código restaurado y reemplazos aplicados con precisión láser!")
