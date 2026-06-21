import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Asegurar que exista el import de Log
if 'import android.util.Log' not in content:
    content = re.sub(
        r'(^package\s+.*?\n)',
        r'\1\nimport android.util.Log\n',
        content,
        flags=re.MULTILINE
    )
    print("✅ Import de Log añadido")

# 2. Agregar un log simple justo antes de NetflixSeriesPosterCard
# Buscamos la línea donde se llama a la tarjeta
pattern = r'(NetflixSeriesPosterCard\(\s*title = folder\.title,\s*logoUrl = folder\.posterUrl \?: folder\.logoUrl,)'

replacement = r'''Log.d("PosterDebug", "SeriesGrid → title=" + folder.title + " | posterUrl=" + folder.posterUrl + " | logoUrl=" + folder.logoUrl)
                    \1'''

if re.search(pattern, content):
    content = re.sub(pattern, replacement, content)
    print("✅ Log simple añadido en NetflixSeriesPosterCard")
else:
    print("⚠️  No encontré exactamente la línea. Puedo ajustarlo si me das más contexto.")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("🎉 Listo. Ahora compilá y probá.")
