import re
import os

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

print("🔍 Buscando el mapeo de SeriesFolder...")

# Buscar el lugar donde se crea SeriesFolder desde lite
if "SeriesFolder(" in content and "posterUrl = lite.posterUrl" not in content:
    # Patrón más amplio y seguro
    content = re.sub(
        r'(\s*)SeriesFolder\s*\(\s*key\s*=\s*lite\.key,',
        r'\1SeriesFolder(\n\1    key = lite.key,',
        content
    )
    
    content = re.sub(
        r'(logoUrl\s*=\s*lite\.(posterUrl|logoUrl),?)',
        r'\1\n        posterUrl = lite.posterUrl ?: lite.logoUrl,',
        content
    )
    
    print("✅ [MAPPER] Inyectado posterUrl en la creación de SeriesFolder")
else:
    print("⚠️ Ya existía o no se encontró el patrón SeriesFolder")

# Asegurar UI
content = re.sub(
    r'model\s*=\s*folder\.logoUrl',
    'model = folder.posterUrl ?: folder.logoUrl',
    content
)

# Asegurar data class
if "val posterUrl: String? = null" not in content:
    content = re.sub(
        r'(val episodes: List<Channel>\s*\))',
        r'\1\n    val posterUrl: String? = null',
        content
    )
    print("✅ [DATA CLASS] posterUrl añadido")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n🚀 PARCHE V3 APLICADO\n")
