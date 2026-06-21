import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Patrón para encontrar el bloque actual de posterUrl (el que tiene el error)
pattern = r'val posterUrl = first\.posterUrl\?\.takeIf \{ it\.isNotBlank\(\) \}\s*\n\s+\?:\s*groupedEpisodes\.firstOrNull \{ !it\.posterUrl\.isNullOrBlank\(\) \}\?\.posterUrl\s*\n\s+\?:\s*groupedEpisodes\.firstOrNull \{ !it\.logoUrl\.isNullOrBlank\(\) \}\?\.logoUrl\s*\n\s+\?:\s*first\.logoUrl'

replacement = '''val posterUrl = groupedEpisodes.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl
                ?: groupedEpisodes.firstOrNull { !it.logoUrl.isNullOrBlank() }?.logoUrl
                ?: first.logoUrl'''

if re.search(pattern, content, re.DOTALL):
    content = re.sub(pattern, replacement, content, flags=re.DOTALL)
    print("✅ Bloque de posterUrl corregido correctamente")
else:
    print("⚠️  No encontré el bloque exacto. Voy a intentar una versión más flexible...")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("🎉 Patch v5.2 aplicado.")
