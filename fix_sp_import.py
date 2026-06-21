import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    lines = f.readlines()

sp_import = "import androidx.compose.ui.unit.sp\n"

# Revisamos si falta
if not any("import androidx.compose.ui.unit.sp" in line for line in lines):
    # Buscamos la primera línea de importación para ponerla junta
    for i, line in enumerate(lines):
        if line.startswith("import "):
            lines.insert(i, sp_import)
            break

with open(path, "w", encoding="utf-8") as f:
    f.writelines(lines)

print("✅ ¡Regla de medir (SP) inyectada con precisión!")
