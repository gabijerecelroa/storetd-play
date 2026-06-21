import re
import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

if os.path.exists(path):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Fondo Global del Hero y Cabecera
    content = re.sub(
        r'Surface\(\s*modifier = Modifier\s*\.fillMaxWidth\(\),\s*color = MaterialTheme\.colorScheme\.surface\.copy\(alpha = 0\.16f\)',
        'Surface(\n        modifier = Modifier.fillMaxWidth(),\n        color = Color(0xFF07111B),\n        shape = RoundedCornerShape(0.dp)',
        content
    )
    
    # 2. El Buscador (LazySearchHeader)
    content = re.sub(
        r'color = MaterialTheme\.colorScheme\.surface\.copy\(alpha = 0\.48f\)',
        'color = Color(0xFF162338)',
        content
    )

    # 3. Categorías (Eliminar el rojo y poner el Azul Eléctrico y fondo Premium)
    content = content.replace("Color(0xFFE50914)", "Color(0xFF69A8FF)")
    content = content.replace("Color(0xFF27272A).copy(alpha = 0.8f)", "Color(0xFF0B1724)")
    
    # 4. Diseño del Botón "ChannelRow" (Tarjeta de Canal)
    # Cambiando los fondos de Material Theme a tonos StreamVault
    content = re.sub(
        r'MaterialTheme\.colorScheme\.surfaceVariant',
        'Color(0xFF162338)',
        content
    )
    content = re.sub(
        r'MaterialTheme\.colorScheme\.primaryContainer',
        'Color(0xFF223754)',
        content
    )
    
    # 5. Textos y Detalles (De Gris a Plata/Azulado)
    content = re.sub(
        r'MaterialTheme\.colorScheme\.onSurfaceVariant',
        'Color(0xFFBBC6D8)',
        content
    )

    # 6. Inyección de Importación Color
    if "import androidx.compose.ui.graphics.Color" not in content:
        content = content.replace("import androidx.compose.runtime.*", "import androidx.compose.runtime.*\nimport androidx.compose.ui.graphics.Color")

    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("✅ ¡Pintura Premium aplicada al módulo de TV en Vivo!")
else:
    print("⚠️ No se encontró LiveTvScreen.kt")
