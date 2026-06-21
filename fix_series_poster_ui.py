import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Buscamos la llamada a NetflixSeriesPosterCard que usa folder.logoUrl
old_call = r'NetflixSeriesPosterCard\(\s*title = folder\.title,\s*logoUrl = folder\.logoUrl,'

new_call = r'''NetflixSeriesPosterCard(
                    title = folder.title,
                    logoUrl = folder.posterUrl ?: folder.logoUrl,'''

if re.search(old_call, content):
    content = re.sub(old_call, new_call, content)
    print("✅ UI actualizada: ahora usa posterUrl ?: logoUrl en NetflixSeriesPosterCard")
else:
    print("⚠️  No encontré la llamada exacta. Puedo ajustar el patrón si me das más contexto.")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("🎉 Fix aplicado.")
