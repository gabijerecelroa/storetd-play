import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Reparar el Mapper (La asignación de datos)
    if "posterUrl = lite.posterUrl" not in content:
        content = re.sub(
            r'(logoUrl\s*=\s*lite\.posterUrl,)',
            r'\1\n                                        posterUrl = lite.posterUrl,',
            content
        )
        print("✅ [MAPPER] Variable de póster asignada con éxito.")
    else:
        print("⚠️ [MAPPER] El código ya estaba inyectado.")

    # 2. Reparar la UI (Para matar las cajas moradas)
    if "val imageToUse = folder.posterUrl ?: folder.logoUrl" not in content:
        content = re.sub(
            r'if\s*\(!folder\.logoUrl\.isNullOrBlank\(\)\)\s*\{\s*Image\(\s*painter\s*=\s*rememberAsyncImagePainter\(folder\.logoUrl\),',
            r'val imageToUse = folder.posterUrl ?: folder.logoUrl\n            if (!imageToUse.isNullOrBlank()) {\n                Image(\n                    painter = rememberAsyncImagePainter(imageToUse),',
            content
        )
        print("✅ [UI] Interfaz gráfica de Series conectada a la carátula.")
    else:
        print("⚠️ [UI] La interfaz gráfica ya estaba actualizada.")

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("\n🚀 CIRUGÍA BLINDADA COMPLETADA.\n")
else:
    print("❌ Error: No se encontró LiveTvScreen.kt")
