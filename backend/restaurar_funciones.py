import re

pl_path = 'src/playlistContent.js'
with open(pl_path, 'r', encoding='utf-8') as f:
    data = f.read()

# Buscamos exactamente el bloque destruido hasta la función que sigue intacta
patron = r"function xtreamSourceUrlMasked[\s\S]*?(?=function normalizeXtreamLiveItems)"

# Reconstruimos las 4 funciones con sintaxis perfecta
reconstruccion = """function xtreamSourceUrlMasked(playlistUrl) {
  const { baseUrl } = xtreamConfig(playlistUrl);
  return baseUrl;
}

function xtreamLiveUrl(playlistUrl, streamId, ext = "ts") {
  return "http://82.39.109.213/api/magma-lite/live/" + streamId + ".m3u8";
}

function xtreamMovieUrl(playlistUrl, streamId, ext = "mp4") {
  const { baseUrl, username, password } = xtreamConfig(playlistUrl);
  return baseUrl + "/movie/" + username + "/" + password + "/" + streamId + "." + ext;
}

function xtreamSeriesEpisodeUrl(playlistUrl, episodeId, ext = "mp4") {
  const { baseUrl, username, password } = xtreamConfig(playlistUrl);
  return baseUrl + "/series/" + username + "/" + password + "/" + episodeId + "." + ext;
}

"""

match = re.search(patron, data)
if match:
    # Inyectamos el bloque reconstruido quirúrgicamente
    data_arreglada = data[:match.start()] + reconstruccion + data[match.end():]
    with open(pl_path, 'w', encoding='utf-8') as f:
        f.write(data_arreglada)
    print("✅ ¡Cirugía reconstructiva exitosa! Funciones restauradas.")
else:
    print("⚠️ No se encontró la zona de impacto.")

