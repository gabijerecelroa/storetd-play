import os

file_path = "backend/src/playlistContent.js"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Remplazamos las palabras clave por versiones más cortas "a prueba de trampas de IPTV"
    old_keywords = """      const allowedKeywords = [
        "paraguay", "gran hermano", "argentina", "libertadores",
        "eventos", "espn", "fox", "movistar", "24 7", "cinema", "cine",
        "infantil", "musica", "latino", "ufc", "zona", "mundial",
        "d port", "dsport", "deporte", "pelicula", "premium", "arg", " ar ", "🇦🇷", "nacional", "local"
      ];"""

    # En este nuevo bloque, en vez de "argentina" ponemos "argent" para atrapar "argentlna".
    # Y quitamos espacios raros.
    new_keywords = """      const allowedKeywords = [
        "paraguay", "gran hermano", "argent", "libertadores",
        "eventos", "espn", "fox", "movistar", "24", "7", "cinema", "cine",
        "infantil", "music", "latino", "ufc", "zona", "mundial",
        "port", "deport", "pelicula", "premium", "arg", "nacional", "local", "liga", "mexic"
      ];"""

    if old_keywords in content:
        content = content.replace(old_keywords, new_keywords)
    else:
        # Si no lo encuentra por espacios, hacemos un reemplazo de texto puro a lo bestia
        import re
        content = re.sub(r'const allowedKeywords = \[.*?\];', new_keywords, content, flags=re.DOTALL)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("\n✅ [ÉXITO] Aduana V5 Anti-Trampas instalada. ¡'ARGENTlNA' caerá en la red!\n")
else:
    print("\n❌ Error: No se encontró playlistContent.js\n")
