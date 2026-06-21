import re

gradle_path = "android/app/build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as f:
    content = f.read()

# Forzar explícitamente a la versión 104 y 1.6.69
content = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 104", content)
content = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.6.69"', content)

with open(gradle_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n✅ [ÉXITO] ETIQUETA PUESTA: Versión 1.6.69 (Code 104) lista para compilar.\n")
