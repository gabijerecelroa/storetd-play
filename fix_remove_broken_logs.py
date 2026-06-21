import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Eliminar cualquier línea que contenga PosterDebug (los logs rotos)
content = re.sub(r'.*PosterDebug.*\n', '', content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ Logs rotos eliminados. El archivo debería compilar ahora.")
