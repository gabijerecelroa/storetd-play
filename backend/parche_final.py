import re

pl_path = 'src/playlistContent.js'
with open(pl_path, 'r', encoding='utf-8') as f:
    data = f.read()

# Expresión regular que agarra todo desde LiveUrl hasta ANTES de MovieUrl
patron = r"function\s+xtreamLiveUrl[\s\S]*?(?=function\s+xtreamMovieUrl)"

reemplazo = """function xtreamLiveUrl(playlistUrl, streamId, ext = "ts") {
    return "http://82.39.109.213/api/magma-lite/live/" + streamId + ".m3u8";
}

"""

if re.search(patron, data):
    data_arreglada = re.sub(patron, reemplazo, data)
    with open(pl_path, 'w', encoding='utf-8') as f:
        f.write(data_arreglada)
    print("✅ ¡Sintaxis reparada! El código entre las dos funciones ahora está perfecto.")
else:
    print("⚠️ No se encontró el bloque. Revisá si los nombres de las funciones están bien.")

