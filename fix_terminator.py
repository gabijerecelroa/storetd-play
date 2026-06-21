import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    lines = f.readlines()

new_lines = []
in_backhandler = False
brace_count = 0

for line in lines:
    if "BackHandler(enabled = true)" in line:
        in_backhandler = True
        brace_count = 0
        
    if in_backhandler:
        brace_count += line.count('{') - line.count('}')
        if brace_count <= 0 and "}" in line:
            in_backhandler = False

    # 🔥 MUTEAMOS TODOS LOS RESETEOS FUERA DEL BOTON ATRÁS 🔥
    if not in_backhandler:
        if "selectedSeriesKey = null" in line and "//" not in line:
            line = line.replace("selectedSeriesKey = null", "/* MUTED_AMNESIA */")
        if "selectedMovieCategoryKey = null" in line and "//" not in line:
            line = line.replace("selectedMovieCategoryKey = null", "/* MUTED_AMNESIA */")
        if "selectedSeriesGroup = null" in line and "//" not in line:
            line = line.replace("selectedSeriesGroup = null", "/* MUTED_AMNESIA */")
        if "lazySeriesEpisodes = emptyList()" in line and "//" not in line:
            line = line.replace("lazySeriesEpisodes = emptyList()", "/* MUTED_AMNESIA */")
        if "lazyMovieItems = emptyList()" in line and "//" not in line:
            line = line.replace("lazyMovieItems = emptyList()", "/* MUTED_AMNESIA */")
        if "lazySeriesFolders = emptyList()" in line and "//" not in line:
            line = line.replace("lazySeriesFolders = emptyList()", "/* MUTED_AMNESIA */")
            
    new_lines.append(line)

content = "".join(new_lines)

# 🕵️‍♂️ LADRÓN DE PÓSTERS EXTREMO (Le damos prioridad 1 absoluta al episodio)
pattern = r"val validEp = groupedEpisodes\.firstOrNull.*?first\.logoUrl\?\.takeIf\s*\{[^}]+\}"
replacement = """// 🕵️‍♂️ LADRÓN DE PÓSTERS EXTREMO: Robamos la foto del capítulo por la fuerza bruta
            val validEp = groupedEpisodes.firstOrNull { !it.logoUrl.isNullOrBlank() && (it.logoUrl?.length ?: 0) > 10 && !it.logoUrl.equals("null", ignoreCase = true) }
            val cachePoster = PremiumContentSessionCache.getSeriesPoster(folderKey, title)?.takeIf { it.length > 10 && !it.equals("null", ignoreCase = true) }
            val posterUrl = validEp?.logoUrl ?: cachePoster ?: first.logoUrl"""
            
content, count = re.subn(pattern, replacement, content, flags=re.DOTALL)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n✅ [ÉXITO] TERMINATOR APLICADO: Sincronización asesina amputada y Ladrón de Pósters Extremo activado.\n")
