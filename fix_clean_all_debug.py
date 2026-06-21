import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Eliminar TODAS las líneas que contienen PosterDebug o código de logs roto
content = re.sub(r'.*PosterDebug.*\n', '', content)
content = re.sub(r'.*logFile.*\n', '', content)
content = re.sub(r'.*FileWriter.*\n', '', content)
content = re.sub(r'.*IOException.*\n', '', content)
content = re.sub(r'.*appendText.*\n', '', content)
content = re.sub(r'.*try \{.*\n', '', content)
content = re.sub(r'.*catch \(e:.*\n', '', content)

# 2. Quitar imports innecesarios que pudieron quedar
content = re.sub(r'import java\.io\.File\n', '', content)
content = re.sub(r'import java\.io\.FileWriter\n', '', content)
content = re.sub(r'import java\.io\.IOException\n', '', content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ Archivo limpiado. Debería compilar ahora.")
