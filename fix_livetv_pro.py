import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
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

# 1. DESTRUIR LA CAJA GIGANTE DEL TÍTULO (Minimalismo puro)
replace_func("PremiumSectionHeader", """@Composable
private fun PremiumSectionHeader(mode: ContentMode, refreshMessage: String? = null, isLoading: Boolean = false) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, top = 32.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val title = when (mode) { ContentMode.LiveTv -> "TV en vivo"; ContentMode.Movies -> "Películas"; ContentMode.Series -> "Series" }
            androidx.compose.material3.Text(text = title, color = androidx.compose.ui.graphics.Color.White, fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            if (isLoading) { androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp) }
        }
    }
}""")

# 2. PASTILLAS DE CATEGORÍA ESTILO STREAMVAULT (Blanco sobre oscuro)
replace_func("CategoryRow", """@Composable
private fun CategoryRow(groups: List<String>, selectedGroup: String, onSelectGroup: (String) -> Unit, modifier: Modifier = Modifier) {
    val visibleGroups = groups.filter { it.isNotBlank() }.distinct()
    if (visibleGroups.isEmpty()) return
    androidx.compose.foundation.lazy.LazyRow(modifier = modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp)) {
        items(items = visibleGroups, key = { it.hashCode() }, contentType = { "category_item" }) { group ->
            var focused by androidx.compose.runtime.remember(group, selectedGroup) { androidx.compose.runtime.mutableStateOf(false) }
            val active = group == selectedGroup || focused
            val scale by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.05f else 1f, label = "scale")
            
            val bgColor = if (active) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF162338).copy(alpha=0.6f)
            val textColor = if (active) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color(0xFFBBC6D8)
            
            androidx.compose.material3.Surface(
                modifier = Modifier.height(38.dp).graphicsLayer { scaleX = scale; scaleY = scale }.onFocusChanged { focused = it.isFocused || it.hasFocus }.clickable { onSelectGroup(group) },
                color = bgColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                border = if (!active) androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x264C6D95)) else null
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 18.dp)) {
                    androidx.compose.material3.Text(text = group, color = textColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}""")

with open(path, "w", encoding="utf-8") as f: f.write(content)
print("✅ ¡Cajas destruidas y minimalismo PRO inyectado!")
