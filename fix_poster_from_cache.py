import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Buscamos el bloque donde se calcula posterUrl (después del title)
old_block = r'val posterUrl = groupedEpisodes\s*\n\s+\.firstOrNull \{ !it\.logoUrl\.isNullOrBlank\(\) \}\s*\n\s+\?\.logoUrl\s*\n\s+\?:\s*first\.logoUrl'

new_block = r'''val posterUrl = PremiumContentSessionCache.getSeriesFolders(folderKey)
                    ?.firstOrNull { !it.posterUrl.isNullOrBlank() }
                    ?.posterUrl
                    ?: groupedEpisodes.firstOrNull { !it.logoUrl.isNullOrBlank() }?.logoUrl
                    ?: first.logoUrl'''

if re.search(old_block, content, re.DOTALL):
    content = re.sub(old_block, new_block, content, flags=re.DOTALL)
    print("✅ v7 aplicado: ahora intenta obtener posterUrl desde el cache de SeriesFolderLite")
else:
    print("⚠️  Patrón no encontrado exactamente. Puedo ajustarlo si me das las líneas actuales.")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("🎉 Listo.")
