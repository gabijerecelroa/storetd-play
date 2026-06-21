import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Creamos la Bóveda de Pósters en el Modo Dios
old_memory = "object GlobalPlayMemory {"
new_memory = """object GlobalPlayMemory {
    val stolenPosters = androidx.compose.runtime.mutableStateMapOf<String, String>()

    fun saveStolenPoster(title: String, url: String) {
        if (url.isNotBlank() && url.length > 10 && !url.equals("null", ignoreCase = true)) {
            stolenPosters[title.trim().lowercase()] = url
        }
    }

    fun getStolenPoster(title: String): String? {
        return stolenPosters[title.trim().lowercase()]
    }"""
if "stolenPosters" not in content:
    content = content.replace(old_memory, new_memory)

# 2. El Ladrón guarda la foto en la Bóveda al entrar a la serie
pattern = r"(val posterUrl = validEp\?\.logoUrl \?: cachePoster \?: first\.logoUrl)"
replacement = r"""\1
            if (!posterUrl.isNullOrBlank() && posterUrl.length > 10 && !posterUrl.equals("null", ignoreCase = true)) {
                GlobalPlayMemory.saveStolenPoster(title, posterUrl)
            }"""
if "saveStolenPoster(" not in content:
    content = re.sub(pattern, replacement, content)

# 3. La Caja de la Serie absorbe la foto de la bóveda e instala el Escudo Anti-Errores
lines = content.split('\n')
for i in range(len(lines)):
    if 'if (!logoUrl.isNullOrBlank() && logoUrl != "-") {' in lines[i]:
        if i+1 < len(lines) and 'contentDescription = title' in lines[i+1]:
            lines[i] = '        val finalLogo = GlobalPlayMemory.getStolenPoster(title) ?: logoUrl\n        val isValidLogo = !finalLogo.isNullOrBlank() && finalLogo != "-" && finalLogo.length > 10 && !finalLogo.equals("null", ignoreCase = true)\n        if (isValidLogo) {'
            lines[i+1] = """            coil.compose.SubcomposeAsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(finalLogo).crossfade(true).build(),
                contentDescription = title,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                error = {
                    androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize().padding(8.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        androidx.compose.material3.Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            )"""

with open(file_path, "w", encoding="utf-8") as f:
    f.write('\n'.join(lines))

print("\n✅ [ÉXITO] OPERACIÓN ROBIN HOOD ACTIVADA: Bóveda de Pósters conectada.\n")
