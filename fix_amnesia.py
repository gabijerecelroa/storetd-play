import os

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Inyectamos la librería de memoria permanente
if "import androidx.compose.runtime.saveable.rememberSaveable" not in content:
    content = content.replace(
        "import androidx.compose.runtime.mutableStateOf",
        "import androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.saveable.rememberSaveable"
    )

# 2. Curamos la amnesia cambiando remember por rememberSaveable
variables = [
    "selectedSeriesKey", 
    "selectedSeriesGroup", 
    "selectedMovieCategoryKey", 
    "lastSeriesFocusKey", 
    "lastMovieCategoryFocusKey"
]

for var_name in variables:
    old_text = f"var {var_name} by remember(contentMode)"
    new_text = f"var {var_name} by rememberSaveable(contentMode)"
    content = content.replace(old_text, new_text)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n✅ [ÉXITO] Memoria permanente instalada. El botón Atrás ya no te sacará de las series.\n")
