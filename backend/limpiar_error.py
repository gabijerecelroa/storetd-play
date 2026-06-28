import re

pl_path = 'src/playlistContent.js'
with open(pl_path, 'r', encoding='utf-8') as f:
    data = f.read()

# Esta es la expresión exacta para encontrar el código basura que quedó colgado y borrarlo
patron_basura = r"\}[\s]*=[\s]*xtreamConfig\(playlistUrl\);[\s\S]*?return url\.replace[^\}]*\}"

if re.search(patron_basura, data):
    data_limpia = re.sub(patron_basura, "", data)
    with open(pl_path, 'w', encoding='utf-8') as f:
        f.write(data_limpia)
    print("✅ ¡Cirugía exitosa! Código basura eliminado.")
else:
    print("⚠️ No se encontró la basura. (O ya se limpió).")

