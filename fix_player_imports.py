import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

imports = """
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
"""

if "import androidx.compose.ui.graphics.Color" not in content:
    content = content.replace("package com.storetd.play.feature.player", "package com.storetd.play.feature.player\n" + imports)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ ¡Permisos de colores y pinceles (Imports) inyectados con éxito!")
