import re

file_path = "android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. BISTURÍ: Buscamos el Rádar Log en pantalla y lo amputamos por completo
pattern = r"\s*// 📡 RÁDAR LOG EN PANTALLA[\s\S]*?catch\s*\(\s*e\s*:\s*Exception\s*\)\s*\{\}\s*\}"

if "RÁDAR LOG EN PANTALLA" in content:
    content = re.sub(pattern, "", content)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)

    # 2. Sellar la Versión Definitiva de Producción: 1.6.79 (Code 114)
    gradle_path = "android/app/build.gradle.kts"
    with open(gradle_path, "r", encoding="utf-8") as f:
        gradle = f.read()

    gradle = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 114", gradle)
    gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.6.79"', gradle)

    with open(gradle_path, "w", encoding="utf-8") as f:
        f.write(gradle)

    print("\n✅ [ÉXITO TOTAL] RÁDAR DESTRUIDO. EL REPRODUCTOR AHORA ES INVISIBLE E INDESTRUCTIBLE.\n")
else:
    print("\n⚠️ [AVISO] El Rádar ya había sido eliminado previamente.\n")
