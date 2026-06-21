import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Inyectamos nuestro buscador en el Caché Global (Caballo de Troya)
old_cache = """    fun putSeriesFolders(key: String, value: List<OptimizedContentApi.SeriesFolderLite>) {
        if (value.isNotEmpty()) {
            seriesFolders[key] = value
        }
    }"""

new_cache = """    fun putSeriesFolders(key: String, value: List<OptimizedContentApi.SeriesFolderLite>) {
        if (value.isNotEmpty()) {
            seriesFolders[key] = value
        }
    }

    fun getSeriesPoster(targetKey: String): String? {
        for (list in seriesFolders.values) {
            val found = list.find { it.key == targetKey }
            if (found != null) return found.posterUrl
        }
        return null
    }"""

if "fun getSeriesPoster" not in content and old_cache in content:
    content = content.replace(old_cache, new_cache)

# 2. Usamos el Caché Global para extraer la foto de forma segura y legal
old_code = """            val posterUrl = groupedEpisodes
                .firstOrNull { !it.logoUrl.isNullOrBlank() }
                ?.logoUrl
                ?: first.logoUrl"""

new_code = """            val posterUrl = PremiumContentSessionCache.getSeriesPoster(folderKey)
                ?: groupedEpisodes.firstOrNull { !it.logoUrl.isNullOrBlank() }?.logoUrl
                ?: first.logoUrl"""

if old_code in content:
    content = content.replace(old_code, new_code)
else:
    # Usamos Regex por si los espacios cambiaron
    content = re.sub(
        r"val posterUrl = groupedEpisodes\s*\.firstOrNull \{ !it\.logoUrl\.isNullOrBlank\(\) \}\s*\?\.logoUrl\s*\?: first\.logoUrl",
        new_code.strip(),
        content
    )

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n✅ [ÉXITO] Puente conectado a través del Caché Global. ¡Seguridad de Kotlin burlada legalmente!\n")
