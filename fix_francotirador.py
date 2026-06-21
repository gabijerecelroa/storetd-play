import os, re

file_path = "backend/src/playlistContent.js"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. EL FRANCOTIRADOR: Reemplazamos la red vieja por coincidencias EXACTAS
    new_keywords = """      // 🎯 FRANCOTIRADOR V6: MATCH EXACTO
      const allowedKeywords = [
        "paraguay vip", "gran hermano", "deportes argentina", "argentina", "argentlna",
        "eventos premium", "espn", "fox sports", "movistar",
        "24 7 pelicula", "cinema vip", "cine premium", "24 7 infantil", "24 7 premium",
        "musica", "4k movistar", "fox one", "latinos premium", "ufc",
        "zona latina", "mundial 2026", "dsport", "d port", "infantiles premium"
      ];"""
    
    content = re.sub(r'const allowedKeywords\s*=\s*\[.*?\];', new_keywords, content, flags=re.DOTALL)

    # 2. RESCATE DE HOME Y SERIES: Forzamos la recolección de 'cover' en todas partes
    # Limpiamos duplicados de inyecciones viejas
    content = content.replace("folder.posterUrl = item.cover || item.stream_icon || item.logoUrl;\n      folder.posterUrl", "folder.posterUrl")
    
    # Inyectamos el puente a los pósters de Xtream para que las cajas moradas desaparezcan
    if "posterUrl: item.cover" not in content:
        content = re.sub(
            r'logoUrl:\s*item\.logoUrl\s*\|\|\s*null,', 
            'logoUrl: item.cover || item.stream_icon || item.logoUrl || null,\n        posterUrl: item.cover || item.stream_icon || item.logoUrl || null,', 
            content
        )

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("✅ [ÉXITO] Francotirador V6 Instalado.")
    print("✅ [ÉXITO] Estructura de pósters reconstruida para el Home y Series.")
else:
    print("❌ Error: No se encontró playlistContent.js")
