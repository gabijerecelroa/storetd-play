import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Reemplazar el bloque actual por la versión original que solo usa logoUrl
old_block = r'val posterUrl = groupedEpisodes\.firstOrNull \{ !it\.posterUrl\.isNullOrBlank\(\) \}\?\.posterUrl\s*\n\s+\?:\s*groupedEpisodes\.firstOrNull \{ !it\.logoUrl\.isNullOrBlank\(\) \}\?\.logoUrl\s*\n\s+\?:\s*first\.logoUrl'

new_block = r'''val posterUrl = groupedEpisodes
                    .firstOrNull { !it.logoUrl.isNullOrBlank() }
                    ?.logoUrl
                    ?: first.logoUrl'''

if re.search(old_block, content, re.DOTALL):
    content = re.sub(old_block, new_block, content, flags=re.DOTALL)
    print("✅ Revertido a versión que compila (solo logoUrl)")
else:
    print("⚠️  No encontré el bloque. Intentando versión alternativa...")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("🎉 Listo para compilar.")
