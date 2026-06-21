import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. MODO DIOS: Estado Inmortal
ui_state = """
object LiveTvUiState {
    var selectedSeriesKey = mutableStateOf<String?>(null)
    var selectedSeriesGroup = mutableStateOf<String?>(null)
    var selectedMovieCategoryKey = mutableStateOf<String?>(null)
    var lastSeriesFocusKey = mutableStateOf<String?>(null)
    var lastMovieCategoryFocusKey = mutableStateOf<String?>(null)
    var lazySeriesEpisodes = mutableStateOf<List<Channel>>(emptyList())
    var lazyMovieItems = mutableStateOf<List<Channel>>(emptyList())
}

private object PremiumContentSessionCache"""

if "object LiveTvUiState" not in content:
    content = content.replace("private object PremiumContentSessionCache", ui_state)

variables = [
    "selectedSeriesKey", "selectedSeriesGroup", "selectedMovieCategoryKey", 
    "lastSeriesFocusKey", "lastMovieCategoryFocusKey",
    "lazySeriesEpisodes", "lazyMovieItems"
]

for var_name in variables:
    pattern = r"var\s+" + var_name + r"\s+by\s+remember(?:Saveable)?(?:\([^)]*\))?\s*\{\s*mutableStateOf[^}]+\}"
    replacement = f"var {var_name} by LiveTvUiState.{var_name}"
    content = re.sub(pattern, replacement, content)

# 2. ESCUDO ANTI-REBOTE EN BACKHANDLER (Evita doble clicks fantasma)
back_pattern = r"BackHandler\(enabled\s*=\s*true\) \{"
debounce_code = """
    var lastBackPressTime by remember { mutableStateOf(0L) }
    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 1000) return@BackHandler
        lastBackPressTime = currentTime
"""
if "lastBackPressTime" not in content:
    content = re.sub(back_pattern, debounce_code, content, count=1)

# 3. FILTRO ESTRICTO CAJAS MORADAS (Exige enlaces reales, rechaza guiones o n/a)
poster_pattern = r"val validEp = groupedEpisodes\.firstOrNull\s*\{[^}]+\}.*?first\.logoUrl\?\.takeIf\s*\{[^}]+\}"
new_poster = """val validEp = groupedEpisodes.firstOrNull { !it.logoUrl.isNullOrBlank() && it.logoUrl.length > 5 && !it.logoUrl.equals("null", ignoreCase = true) }
            val cachePoster = PremiumContentSessionCache.getSeriesPoster(folderKey, title)
            val posterUrl = cachePoster?.takeIf { it.length > 5 && !it.equals("null", ignoreCase = true) }
                ?: validEp?.logoUrl
                ?: first.logoUrl?.takeIf { it.length > 5 && !it.equals("null", ignoreCase = true) }"""

content, count = re.subn(poster_pattern, new_poster, content, flags=re.DOTALL)
if count == 0: # Respaldo por si el código era ligeramente distinto
    poster_pattern_2 = r"val posterUrl = PremiumContentSessionCache.*?first\.logoUrl"
    content = re.sub(poster_pattern_2, new_poster, content, flags=re.DOTALL)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n✅ [ÉXITO] MODO DIOS Inyectado. Escudo Anti-Rebote y Filtro de Guiones activados.\n")
