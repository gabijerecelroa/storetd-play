import os
import re

path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Buscamos la cabeza de nuestra función del reproductor
viejo = "@androidx.compose.runtime.Composable\nfun MiniPlayerPreview"

# Le pegamos el Permiso OptIn de AndroidX Media3
nuevo = "@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)\n@androidx.compose.runtime.Composable\nfun MiniPlayerPreview"

if viejo in content:
    content = content.replace(viejo, nuevo)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("✅ ¡Permiso VIP de ExoPlayer concedido! El inspector Lint ya no molestará.")
else:
    # Por si los espacios son distintos, usamos un buscador láser
    content = re.sub(r'(@androidx\.compose\.runtime\.Composable\s*fun MiniPlayerPreview)', r'@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)\n\1', content)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("✅ ¡Permiso VIP concedido vía Escáner!")

