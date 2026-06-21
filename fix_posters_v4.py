import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

print("🔧 Aplicando parche V4 en el constructor real...")

# Inyectar posterUrl justo después de logoUrl en este constructor específico
old_constructor = r'''(logoUrl\s*=\s*posterUrl,\s*episodes\s*=\s*episodes)'''

new_constructor = r'''logoUrl = posterUrl,
            posterUrl = posterUrl,
            episodes = episodes'''

content = re.sub(old_constructor, new_constructor, content)

# Asegurar que la UI use posterUrl
content = re.sub(
    r'model\s*=\s*folder\.logoUrl',
    'model = folder.posterUrl ?: folder.logoUrl',
    content
)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ [V4] posterUrl inyectado en el constructor de SeriesFolder")
print("✅ [UI] Actualizado para usar posterUrl\n")
