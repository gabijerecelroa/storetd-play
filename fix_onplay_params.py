import os

path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Buscamos la línea defectuosa que tiene 1 parámetro (item)
linea_rota = "onPlay = { item -> if (previewItem == item) onPlay(item) else previewItem = item }"

# La reemplazamos por la línea correcta que acepta los 2 parámetros (item y playlist)
linea_sana = "onPlay = { item, playlist -> if (previewItem == item) onPlay(item, playlist) else previewItem = item }"

# Aplicamos el parche milimétrico
if linea_rota in content:
    content = content.replace(linea_rota, linea_sana)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("✅ ¡Cables del botón Play reconectados con éxito!")
else:
    print("⚠️ No se encontró la línea. Verifica si ya fue modificada.")

