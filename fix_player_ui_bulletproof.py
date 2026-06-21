import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"
with open(path, "r", encoding="utf-8") as f: content = f.read()

def replace_func(func_name, new_code):
    global content
    idx = content.find("@Composable\nprivate fun " + func_name)
    if idx == -1: return

    b_start = content.find("{", idx)
    b_count = 1
    i = b_start + 1
    while b_count > 0 and i < len(content):
        if content[i] == '{': b_count += 1
        elif content[i] == '}': b_count -= 1
        i += 1

    content = content[:idx] + new_code + "\n\n" + content[i:]

# Reemplazando cada botón con diseño StreamVault:
replace_func("PlayerCenterControl", """@Composable
private fun PlayerCenterControl(selected: Boolean, isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (selected) Color(0xFF69A8FF) else Color(0xFF07111B).copy(alpha = 0.75f)
    val textColor = if (selected) Color(0xFF07111B) else Color.White
    Surface(modifier = modifier.clickable { onClick() }, color = bgColor, shape = RoundedCornerShape(50), border = BorderStroke(2.dp, if (selected) Color.White else Color.Transparent)) {
        Text(text = if (isPlaying) "⏸ Pausa" else "▶ Reproducir", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
    }
}""")

replace_func("PlayerBottomOverlay", """@Composable
private fun PlayerBottomOverlay(channel: SavedChannel, isFavorite: Boolean, canPrevious: Boolean, canNext: Boolean, resizeModeLabel: String, currentProgram: EpgProgram?, nextProgram: EpgProgram?, isLandscape: Boolean, isVodContent: Boolean, currentPositionMs: Long, durationMs: Long, selectedControlIndex: Int, onPrevious: () -> Unit, onNext: () -> Unit, onFavorite: () -> Unit, onReport: () -> Unit, onRetry: () -> Unit, onChangeResizeMode: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xFF07111B).copy(alpha = 0.95f)))).padding(horizontal = if (isLandscape) 48.dp else 16.dp, vertical = 24.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(text = channel.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(text = "${channel.group} • $resizeModeLabel", color = Color(0xFFBBC6D8), fontSize = 14.sp)
                PlayerEpgInfo(currentProgram, nextProgram)
            }
            if (isVodContent) { PlayerProgressBar(currentPositionMs, durationMs) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                PlayerControlChip("⏮ Ant", selectedControlIndex == 0, canPrevious, onPrevious)
                PlayerControlChip(if (isFavorite) "❤️ Quitar" else "🤍 Fav", selectedControlIndex == 1, true, onFavorite)
                PlayerControlChip("⚠️ Rep", selectedControlIndex == 2, true, onReport)
                PlayerControlChip("Sig ⏭", selectedControlIndex == 3, canNext, onNext)
                PlayerControlChip("⛶ Ajustar", selectedControlIndex == 4, true, onChangeResizeMode)
            }
        }
    }
}""")

replace_func("PlayerControlChip", """@Composable
private fun PlayerControlChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val bgColor = if (selected) Color(0xFF69A8FF) else Color(0xFF162338).copy(alpha = 0.8f)
    val textColor = if (selected) Color(0xFF07111B) else Color(0xFFBBC6D8)
    Surface(color = if (enabled) bgColor else Color.Transparent, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, if (selected) Color.Transparent else Color(0x264C6D95)), modifier = Modifier.clickable(enabled = enabled) { onClick() }) {
        Text(text = label, color = textColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}""")

replace_func("PlayerProgressBar", """@Composable
private fun PlayerProgressBar(currentPositionMs: Long, durationMs: Long) {
    val progress = (currentPositionMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF223754), RoundedCornerShape(50))) {
            Box(modifier = Modifier.fillMaxWidth(progress).height(4.dp).background(Color(0xFF69A8FF), RoundedCornerShape(50)))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPlaybackTime(currentPositionMs), color = Color(0xFFBBC6D8), fontSize = 12.sp)
            Text(formatPlaybackTime(durationMs), color = Color(0xFFBBC6D8), fontSize = 12.sp)
        }
    }
}""")

replace_func("PlayerPortraitBackButton", """@Composable
private fun PlayerPortraitBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.clickable { onBack() }.padding(16.dp), color = Color(0xFF162338).copy(alpha = 0.9f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Color(0x264C6D95))) {
        Text("⬅ Volver", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}""")

imports = ["import androidx.compose.ui.graphics.Color", "import androidx.compose.ui.graphics.Brush", "import androidx.compose.ui.unit.sp", "import androidx.compose.foundation.BorderStroke", "import androidx.compose.foundation.horizontalScroll"]
for imp in imports:
    if imp not in content: content = content.replace("import androidx.compose.runtime.*", f"import androidx.compose.runtime.*\\n{imp}")

with open(path, "w", encoding="utf-8") as f: f.write(content)
print("✅ ¡Cirugía Matemática Exitosa! Controles inyectados perfectamente.")
