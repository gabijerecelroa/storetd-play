import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Eliminamos el sensor @Composable ilegal y usamos la variable en memoria de tu app
content = content.replace(
    "androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700",
    "!LiveTvBgState.isCompact"
)

# 2. Reemplazamos la palabra sin import (defaultMinSize) por una válida (heightIn)
content = content.replace(
    "defaultMinSize(minHeight = 40.dp)",
    "heightIn(min = 40.dp)"
)

# 3. Aseguramos el permiso de la librería heightIn
if "import androidx.compose.foundation.layout.heightIn" not in content:
    content = content.replace(
        "import androidx.compose.foundation.layout.aspectRatio",
        "import androidx.compose.foundation.layout.aspectRatio\nimport androidx.compose.foundation.layout.heightIn"
    )

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ ¡Errores de Kotlin reparados de forma milimétrica!")
