import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# --- 1. PÓSTERS: Enseñar al Caballo de Troya a buscar por Título ---
old_trojan = """    fun getSeriesPoster(targetKey: String): String? {
        for (list in seriesFolders.values) {
            val found = list.find { it.key == targetKey }
            if (found != null) return found.posterUrl
        }
        return null
    }"""
new_trojan = """    fun getSeriesPoster(targetKey: String, targetTitle: String): String? {
        for (list in seriesFolders.values) {
            val found = list.find { 
                it.key == targetKey || 
                it.key.startsWith(targetKey) || 
                it.title.equals(targetTitle, ignoreCase = true) 
            }
            if (found != null && !found.posterUrl.isNullOrBlank()) return found.posterUrl
        }
        return null
    }"""
content = content.replace(old_trojan, new_trojan)
content = content.replace("PremiumContentSessionCache.getSeriesPoster(folderKey)", "PremiumContentSessionCache.getSeriesPoster(folderKey, title)")

# --- 2. AMNESIA: Preparar el Disco Duro Global ---
old_cache_start = "private object PremiumContentSessionCache {"
new_cache_start = """private object PremiumContentSessionCache {
    val seriesEpisodesMap = mutableMapOf<String, List<Channel>>()

    fun saveEpisodes(key: String, eps: List<Channel>) {
        if (key.isNotBlank()) seriesEpisodesMap[key] = eps
    }

    fun getEpisodes(key: String?): List<Channel> {
        return seriesEpisodesMap[key ?: ""] ?: emptyList()
    }"""
if "seriesEpisodesMap" not in content:
    content = content.replace(old_cache_start, new_cache_start)

# --- 3. AMNESIA: Conectar los Capítulos al Disco Duro ---
regex_lazy = r"(var lazySeriesEpisodes by remember\([^)]+\)\s*\{\s*mutableStateOf<List<Channel>>\()emptyList\(\)(\)\s*\})"
new_lazy = r"\1PremiumContentSessionCache.getEpisodes(selectedSeriesKey)\2\n\n    LaunchedEffect(lazySeriesEpisodes, selectedSeriesKey) {\n        if (lazySeriesEpisodes.isNotEmpty() && selectedSeriesKey != null) {\n            PremiumContentSessionCache.saveEpisodes(selectedSeriesKey!!, lazySeriesEpisodes)\n        }\n    }"
if "PremiumContentSessionCache.getEpisodes" not in content:
    content = re.sub(regex_lazy, new_lazy, content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n✅ [ÉXITO] Cirugía Maestra Completada: Pósters inteligentes y Disco Duro instalados.\n")
