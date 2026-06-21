import re
import os

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

if not os.path.exists(file_path):
    print("❌ No se encontró el archivo LiveTvScreen.kt")
    exit(1)

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

print("🔍 Aplicando parche de pósters de series...")

# 1. Asegurar que el data class tenga posterUrl
if "val posterUrl: String? = null" not in content:
    content = re.sub(
        r'(val episodes: List<Channel>\s*\))',
        r'\1\n    val posterUrl: String? = null',
        content
    )
    print("✅ [1] posterUrl añadido al data class SeriesFolder")
else:
    print("⚠️ [1] posterUrl ya existía en el data class")

# 2. Actualizar el mapeo desde lite
if "posterUrl = lite.posterUrl" not in content:
    content = re.sub(
        r'(logoUrl\s*=\s*lite\.posterUrl,)',
        r'\1\n                                        posterUrl = lite.posterUrl,',
        content
    )
    print("✅ [2] Mapeo actualizado con posterUrl")
else:
    print("⚠️ [2] Mapeo ya estaba actualizado")

# 3. Actualizar la UI (AsyncImage / Coil)
content = re.sub(
    r'model\s*=\s*folder\.logoUrl',
    'model = folder.posterUrl ?: folder.logoUrl',
    content
)
print("✅ [3] UI actualizada para usar posterUrl")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n🚀 PARCHE APLICADO CORRECTAMENTE\n")
