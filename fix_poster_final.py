import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Buscamos el código donde Android le roba la foto a los episodios vacíos
old_code = """            val posterUrl = groupedEpisodes
                .firstOrNull { !it.logoUrl.isNullOrBlank() }
                ?.logoUrl
                ?: first.logoUrl"""

# Lo reemplazamos para que busque primero en la memoria original del servidor
new_code = """            val posterUrl = lazySeriesFolders.firstOrNull { it.key == folderKey }?.posterUrl
                ?: groupedEpisodes.firstOrNull { !it.logoUrl.isNullOrBlank() }?.logoUrl
                ?: first.logoUrl"""

if old_code in content:
    content = content.replace(old_code, new_code)
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("\n✅ [ÉXITO] Puente del póster conectado. ¡Adiós a las cajas moradas!\n")
else:
    print("\n⚠️ [ALERTA] No se encontró el código exacto. Intentando búsqueda flexible...\n")
    regex = r"val posterUrl = groupedEpisodes\s*\.firstOrNull \{ !it\.logoUrl\.isNullOrBlank\(\) \}\s*\?\.logoUrl\s*\?: first\.logoUrl"
    new_regex_code = """val posterUrl = lazySeriesFolders.firstOrNull { it.key == folderKey }?.posterUrl\n                ?: groupedEpisodes.firstOrNull { !it.logoUrl.isNullOrBlank() }?.logoUrl\n                ?: first.logoUrl"""
    content, count = re.subn(regex, new_regex_code, content)
    if count > 0:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print("\n✅ [ÉXITO] Puente del póster conectado (Regex). ¡Adiós a las cajas moradas!\n")
    else:
        print("\n❌ Error: No se pudo inyectar el código.\n")
