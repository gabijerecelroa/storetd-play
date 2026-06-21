import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Añadir import si no existe (ya debería estar)
if 'import android.util.Log' not in content:
    content = re.sub(
        r'(^package\s+.*?\n)',
        r'\1\nimport android.util.Log\n',
        content,
        flags=re.MULTILINE
    )
    print("✅ Import añadido")
else:
    print("ℹ️  Import ya existía")

# 2. Reemplazo más robusto de la lógica de posterUrl
# Buscamos desde "val posterUrl = groupedEpisodes" hasta la línea "?: first.logoUrl"
pattern = r'(val posterUrl = groupedEpisodes\s*\n\s+\.firstOrNull \{ !it\.logoUrl\.isNullOrBlank\(\) \}\s*\n\s+\?\.logoUrl\s*\n\s+\?: first\.logoUrl)'

replacement = r'''val posterUrl = first.posterUrl?.takeIf { it.isNotBlank() }
                ?: groupedEpisodes.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl
                ?: groupedEpisodes.firstOrNull { !it.logoUrl.isNullOrBlank() }?.logoUrl
                ?: first.logoUrl

            Log.d("PosterDebug", "Creando SeriesFolder → title='$title', posterUrl='\( posterUrl', logoUrl=' \){first.logoUrl}'")'''

if re.search(pattern, content):
    content = re.sub(pattern, replacement, content)
    print("✅ Lógica de posterUrl corregida + Log inyectado")
else:
    print("⚠️  Todavía no coincidió el patrón. Necesito ajustar más.")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n🎉 Script v5.1 terminado.")
