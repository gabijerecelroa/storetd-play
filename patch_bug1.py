file_path = "android/app/src/main/java/com/storetd/play/feature/vod/VodDetailScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    lines = f.readlines()

# 1. Anulamos la llamada a la API muerta (cero delay) y mantenemos la inferencia de tipos
lines[119] = "                emptyList<Nothing>() /* " + lines[119].lstrip()
lines[126] = lines[126].replace(")", ") */")

# 2. Forzamos el puente directo a DPlatino (Xtream Codes)
lines[129] = lines[129].replace("if (loadedSources.isNotEmpty())", "if (true)")
lines[131] = lines[131].replace("loadedSources.first().streamUrl", "streamUrl")

# 3. Apagamos el renderizado del AlertDialog de raíz
lines[143] = lines[143].replace("if (showSourceDialog)", "if (false) /* 🔥 POPUP MAGMA AMPUTADO */")

with open(file_path, "w", encoding="utf-8") as f:
    f.writelines(lines)

print("\n🚀 [BUG 1 ERRADICADO] Cirugía completada. Carga instantánea a DPlatino activada.\n")
