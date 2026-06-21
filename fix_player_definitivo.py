import os
import re

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

# 1. Botón Central (Pausa/Play)
replace_func("PlayerCenterControl", """@Composable
private fun PlayerCenterControl(selected: Boolean, isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (selected) androidx.compose.ui.graphics.Color(0xFF69A8FF) else androidx.compose.ui.graphics.Color(0xFF07111B).copy(alpha = 0.75f)
    val textColor = if (selected) androidx.compose.ui.graphics.Color(0xFF07111B) else androidx.compose.ui.graphics.Color.White
    Surface(modifier = modifier.clickable { onClick() }, color = bgColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(2.dp, if (selected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent)) {
        androidx.compose.material3.Text(text = if (isPlaying) "⏸ Pausa" else "▶ Reproducir", color = textColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
    }
}""")

# 2. El diseño Píldora para TUS botones (Respeta TV y VOD)
replace_func("PlayerControlChip", """@Composable
private fun PlayerControlChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val bgColor = if (selected) androidx.compose.ui.graphics.Color(0xFF69A8FF) else androidx.compose.ui.graphics.Color(0xFF162338).copy(alpha = 0.8f)
    val textColor = if (selected) androidx.compose.ui.graphics.Color(0xFF07111B) else androidx.compose.ui.graphics.Color(0xFFBBC6D8)
    Surface(color = if (enabled) bgColor else androidx.compose.ui.graphics.Color.Transparent, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) androidx.compose.ui.graphics.Color.Transparent else androidx.compose.ui.graphics.Color(0x264C6D95)), modifier = Modifier.clickable(enabled = enabled) { onClick() }) {
        androidx.compose.material3.Text(text = label, color = textColor, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}""")

# 3. Barra de progreso StreamVault
replace_func("PlayerProgressBar", """@Composable
private fun PlayerProgressBar(currentPositionMs: Long, durationMs: Long) {
    val progress = (currentPositionMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(androidx.compose.ui.graphics.Color(0xFF223754), androidx.compose.foundation.shape.RoundedCornerShape(50))) {
            Box(modifier = Modifier.fillMaxWidth(progress).height(4.dp).background(androidx.compose.ui.graphics.Color(0xFF69A8FF), androidx.compose.foundation.shape.RoundedCornerShape(50)))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            androidx.compose.material3.Text(formatPlaybackTime(currentPositionMs), color = androidx.compose.ui.graphics.Color(0xFFBBC6D8), fontSize = 12.sp)
            androidx.compose.material3.Text(formatPlaybackTime(durationMs), color = androidx.compose.ui.graphics.Color(0xFFBBC6D8), fontSize = 12.sp)
        }
    }
}""")

# 4. Botón Volver
replace_func("PlayerPortraitBackButton", """@Composable
private fun PlayerPortraitBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.clickable { onBack() }.padding(16.dp), color = androidx.compose.ui.graphics.Color(0xFF162338).copy(alpha = 0.9f), shape = androidx.compose.foundation.shape.RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x264C6D95))) {
        androidx.compose.material3.Text("⬅ Volver", color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}""")

# 5. Inyectar el degradado oscuro AL FONDO de tus botones
content = re.sub(
    r'BoxWithConstraints\(\s*modifier\s*=\s*Modifier\s*\.fillMaxWidth\(\)',
    'BoxWithConstraints(\n        modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(colors = listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color(0xFF07111B).copy(alpha = 0.95f))))',
    content
)

# 6. Inyectar la unidad "sp" de forma segura (sin tocar la línea 1)
if "import androidx.compose.ui.unit.sp" not in content:
    content = content.replace("import androidx.compose.runtime.*", "import androidx.compose.runtime.*\nimport androidx.compose.ui.unit.sp")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ ¡Pintura Premium aplicada SOBRE tus botones originales!")
