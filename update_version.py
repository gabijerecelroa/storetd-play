import os, re

# Buscamos el archivo de configuración de Gradle (puede ser .kts o normal)
file_path = "android/app/build.gradle.kts"
if not os.path.exists(file_path):
    file_path = "android/app/build.gradle"

if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Cambiamos el versionCode a 103
    content = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 103', content)
    # Cambiamos el versionName a "1.6.68"
    content = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.6.68"', content)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"\n✅ [ÉXITO] Etiqueta de fábrica actualizada en {file_path}")
    print("🎯 Nueva versión: 1.6.68 (Code 103)\n")
else:
    print("\n❌ Error: No se encontró el archivo build.gradle\n")
