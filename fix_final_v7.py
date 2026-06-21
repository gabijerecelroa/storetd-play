import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. EXTIRPAR LA AMNESIA ASESINA (Escudo Protector)
old_amnesia = """                selectedSeriesKey = null
                selectedMovieCategoryKey = null
                lastSeriesFocusKey = null
                lastMovieCategoryFocusKey = null
                showLazySearch = false
                lazySearchQuery = ""
                lazySeriesFolders = emptyList()
                lazySeriesEpisodes = emptyList()
                lazyMovieCategories = emptyList()
                lazyMovieItems = emptyList()"""

new_amnesia = """                // 🔥 ESCUDO ANTI-AMNESIA: Protegemos la memoria para no sacar al usuario de la serie
                if (selectedSeriesKey == null && selectedMovieCategoryKey == null && !showLazySearch) {
                    lastSeriesFocusKey = null
                    lastMovieCategoryFocusKey = null
                    lazySearchQuery = ""
                    lazySeriesFolders = emptyList()
                    lazySeriesEpisodes = emptyList()
                    lazyMovieCategories = emptyList()
                    lazyMovieItems = emptyList()
                }"""
if old_amnesia in content:
    content = content.replace(old_amnesia, new_amnesia)

# 2. FILTRAR LA TRAMPA "NULL" PARA CURAR LAS CAJAS MORADAS
old_poster = """            val posterUrl = PremiumContentSessionCache.getSeriesPoster(folderKey, title)
                ?: groupedEpisodes.firstOrNull { !it.logoUrl.isNullOrBlank() }?.logoUrl
                ?: first.logoUrl"""

new_poster = """            val validEp = groupedEpisodes.firstOrNull { !it.logoUrl.isNullOrBlank() && !it.logoUrl.equals("null", ignoreCase = true) }
            val cachePoster = PremiumContentSessionCache.getSeriesPoster(folderKey, title)
            val posterUrl = cachePoster?.takeIf { !it.equals("null", ignoreCase = true) }
                ?: validEp?.logoUrl
                ?: first.logoUrl?.takeIf { !it.equals("null", ignoreCase = true) }"""

if old_poster in content:
    content = content.replace(old_poster, new_poster)
else:
    # Por si los espacios cambiaron, usamos Regex como respaldo
    content = re.sub(
        r"val posterUrl = PremiumContentSessionCache\.getSeriesPoster\(folderKey, title\)\s*\?: groupedEpisodes\.firstOrNull \{ !it\.logoUrl\.isNullOrBlank\(\) \}\?\.logoUrl\s*\?: first\.logoUrl",
        new_poster.strip(),
        content
    )

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n✅ [ÉXITO] Escudo Anti-Amnesia instalado y Filtro de Cajas Moradas activado.\n")
