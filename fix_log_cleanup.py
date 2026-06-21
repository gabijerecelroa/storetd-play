import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Arreglar el Log.d que quedó con escapes raros
old_log = r'''Log\.d\("PosterDebug", "Creando SeriesFolder → title='\$title', posterUrl='\\\( posterUrl', logoUrl=' \\)\{first\.logoUrl\}'"\)'''

new_log = r'''Log.d("PosterDebug", "Creando SeriesFolder → title='$title', posterUrl='\( posterUrl', logoUrl=' \){first.logoUrl}'")'''

if re.search(old_log, content):
    content = re.sub(old_log, new_log, content)
    print("✅ Log.d limpiado correctamente")
else:
    print("⚠️  No encontré el Log.d para limpiar (puede que ya esté bien)")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
