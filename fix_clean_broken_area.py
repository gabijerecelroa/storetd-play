import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Limpiar la zona rota que quedó (alrededor de NetflixSeriesPosterCard)
# Esto elimina desde el Box roto hasta la tarjeta
content = re.sub(
    r'androidx\.compose\.foundation\.layout\.Box\(modifier = Modifier\.weight\(1f\)\) \{[\s\S]*?NetflixSeriesPosterCard',
    'NetflixSeriesPosterCard',
    content
)

# Limpiar restos de Java que quedaron
content = re.sub(r'writer\.close\(\);?\s*e\.printStackTrace\(\);?', '', content)
content = re.sub(r'"\);', '', content)
content = re.sub(r'^\s*"\s*$', '', content, flags=re.MULTILINE)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ Zona rota limpiada correctamente")
