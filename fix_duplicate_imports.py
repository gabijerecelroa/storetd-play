import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"

if os.path.exists(path):
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    clean_lines = []
    imports_seen = set()

    for line in lines:
        if line.startswith("import "):
            clean_line = line.strip()
            if clean_line in imports_seen:
                continue  # Es un duplicado, lo ignoramos
            imports_seen.add(clean_line)
        clean_lines.append(line)

    with open(path, "w", encoding="utf-8") as f:
        f.writelines(clean_lines)

    print("✅ ¡Clon destruido! Importaciones duplicadas purgadas con éxito.")
else:
    print("⚠️ Error: No se encontró PlayerScreen.kt")
