import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/vod/VodDetailScreen.kt"

if os.path.exists(path):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Inyectar las importaciones faltantes de Compose
    imports_to_add = """
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
"""
    if "import androidx.compose.ui.draw.alpha" not in content:
        content = content.replace(
            "import androidx.compose.ui.draw.clip", 
            "import androidx.compose.ui.draw.clip" + imports_to_add
        )

    # 2. Arreglar la variable desconocida de TMDB
    content = content.replace("info?.releaseDate?.take(4) ?: \"2024\"", "\"HD\"")

    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
        
    print("✅ ¡Importaciones inyectadas y variables estabilizadas!")
else:
    print("⚠️ No se encontró VodDetailScreen.kt")
