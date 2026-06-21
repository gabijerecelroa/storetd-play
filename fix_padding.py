path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/home/HomeScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Corregimos la sintaxis estricta de Kotlin
content = content.replace(
    "Modifier.padding(horizontal = 48.dp, bottom = 16.dp)",
    "Modifier.padding(start = 48.dp, end = 48.dp, bottom = 16.dp)"
)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ ¡Sintaxis de Padding corregida con precisión láser!")
