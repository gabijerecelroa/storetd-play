import re

file_path = "android/app/src/main/java/com/storetd/play/feature/home/HomeScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Barajar Películas Populares y Estrenos
content = re.sub(r"(peliculasVistas\s*=\s*OptimizedContentApi.*?\.filter\s*\{[^}]*\}\s*)\.take\(", r"\1.shuffled().take(", content)
content = re.sub(r"(estrenos\s*=\s*OptimizedContentApi.*?\.filter\s*\{[^}]*\}\s*)\.take\(", r"\1.shuffled().take(", content)

# 2. Desbloquear las Series y barajarlas (Quitamos el filtro de imagen vacía)
content = re.sub(r"(seriesDestacadas\s*=\s*sCats)\.filter\s*\{[^}]*\}\.take\(", r"\1.shuffled().take(", content)

# Limpiar duplicados por si se ejecuta 2 veces
content = content.replace(".shuffled().shuffled()", ".shuffled()")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n✅ [ÉXITO] MODO ALEATORIO (SHUFFLE) Y CARRUSEL DE SERIES ACTIVADO.\n")
