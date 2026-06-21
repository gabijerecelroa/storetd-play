import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Añadir import Log si no existe
if 'import android.util.Log' not in content:
    content = re.sub(
        r'(^package\s+.*?\n)',
        r'\1\nimport android.util.Log\n',
        content,
        flags=re.MULTILINE
    )
    print("✅ Import android.util.Log añadido")

# 2. Arreglar la obtención de posterUrl + agregar log de depuración
old_block = r'''            val posterUrl = groupedEpisodes
                    \.firstOrNull \{ !it\.logoUrl\.isNullOrBlank\(\) \}
                    \?\.logoUrl
                    \?: first\.logoUrl'''

new_block = r'''            val posterUrl = first.posterUrl?.takeIf { it.isNotBlank() }
                    ?: groupedEpisodes.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl
                    ?: groupedEpisodes.firstOrNull { !it.logoUrl.isNullOrBlank() }?.logoUrl
                    ?: first.logoUrl

            Log.d("PosterDebug", "Creando SeriesFolder → title='\( {title}', posterUrl=' \){posterUrl}', logoUrl='${first.logoUrl}'")'''

if re.search(old_block, content):
    content = re.sub(old_block, new_block, content)
    print("✅ Lógica de posterUrl corregida + Log inyectado")
else:
    print("⚠️  No encontré el bloque exacto. Pégame de nuevo el contexto si falla.")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n🎉 Patch v5_final aplicado correctamente.")
