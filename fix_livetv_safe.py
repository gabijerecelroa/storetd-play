import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
with open(path, "r", encoding="utf-8") as f: content = f.read()

# Fondo Global (Reemplazo exacto sin duplicar variables)
content = content.replace("color = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),", "color = Color(0xFF07111B),")

# El Buscador
content = content.replace("color = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),", "color = Color(0xFF162338),")

# Categorías (Cambiamos el rojo y los grises por el Azul Eléctrico y Negro Premium)
content = content.replace("Color(0xFFE50914)", "Color(0xFF69A8FF)")
content = content.replace("Color(0xFF27272A).copy(alpha = 0.8f)", "Color(0xFF0B1724)")

# Tarjetas de Canales y Textos (Estética StreamVault)
content = content.replace("MaterialTheme.colorScheme.surfaceVariant", "Color(0xFF162338)")
content = content.replace("MaterialTheme.colorScheme.primaryContainer", "Color(0xFF223754)")
content = content.replace("MaterialTheme.colorScheme.onSurfaceVariant", "Color(0xFFBBC6D8)")

# Inyección de Importación de Colores
if "import androidx.compose.ui.graphics.Color" not in content:
    content = content.replace("import androidx.compose.runtime.*", "import androidx.compose.runtime.*\nimport androidx.compose.ui.graphics.Color")

with open(path, "w", encoding="utf-8") as f: f.write(content)
print("✅ ¡Pintura oscura inyectada con éxito y sin duplicar parámetros!")
