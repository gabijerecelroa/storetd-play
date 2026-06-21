import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Asegurar import de Log
if 'import android.util.Log' not in content:
    content = re.sub(
        r'(^package\s+.*?\n)',
        r'\1\nimport android.util.Log\n',
        content,
        flags=re.MULTILINE
    )
    print("✅ Import de Log añadido")

# 2. Log simple en buildSeriesFolders (después de calcular posterUrl)
# Buscamos justo después de la línea del posterUrl
poster_block = r'(val posterUrl = .*?first\.logoUrl)'

poster_log = r'''\1
            Log.d("PosterDebug", "buildSeriesFolders -> finalPosterUrl=" + posterUrl)'''

if re.search(poster_block, content, re.DOTALL):
    content = re.sub(poster_block, poster_log, content, flags=re.DOTALL)
    print("✅ Log añadido en buildSeriesFolders")
else:
    print("⚠️  No encontré el bloque de posterUrl")

# 3. Log simple antes de NetflixSeriesPosterCard
ui_block = r'(NetflixSeriesPosterCard\(\s*title = folder\.title,\s*logoUrl = folder\.posterUrl \?: folder\.logoUrl,)'

ui_log = r'''Log.d("PosterDebug", "UI -> title=" + folder.title + " | posterUrl=" + folder.posterUrl + " | logoUrl=" + folder.logoUrl)
                    \1'''

if re.search(ui_block, content):
    content = re.sub(ui_block, ui_log, content)
    print("✅ Log añadido en la UI (NetflixSeriesPosterCard)")
else:
    print("⚠️  No encontré la llamada a NetflixSeriesPosterCard")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n🎉 Logs limpios añadidos correctamente (tag: PosterDebug)")
