import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. CURAR LA RAM (El asesino de los 3 minutos)
content = re.sub(r"300_000,\s*// Max[^\n]*\n", "45_000,   // Max: 45 seg (Anti-Colapso RAM para TV Boxes)\n", content)
content = re.sub(r"20_000,\s*// Min[^\n]*\n", "15_000,   // Min: 15 seg\n", content)

# Inyectar Prioridad de Tiempo (Evita congelamientos si la caja es lenta)
content = content.replace(".setBufferDurationsMs(", ".setPrioritizeTimeOverSizeThresholds(true)\n            .setBufferDurationsMs(")

# 2. CURAR PANTALLA NEGRA (El Códec de la Dinax)
if "SURFACE_TYPE_TEXTURE_VIEW" not in content:
    content = re.sub(
        r"(\bPlayerView\(context\)\.apply\s*\{)",
        r"\1\n                    // 🔥 MODO TRACTOR: Fuerza TextureView para evitar pantalla negra en Dinax/MXQ\n                    surfaceType = androidx.media3.ui.PlayerView.SURFACE_TYPE_TEXTURE_VIEW",
        content
    )

# 3. AUTO-REANIMADOR (Si el chip chino falla, revive el video solo)
if "val errorListener =" not in content:
    retry_code = """
        val errorListener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Choque eléctrico al reproductor si la TV Box falla
                player.seekToDefaultPosition()
                player.prepare()
                player.play()
            }
        }
        player.addListener(errorListener)"""
    content = content.replace("playWhenReady = true\n            }", f"playWhenReady = true\n            }}{retry_code}")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

# 4. AUTO-AUMENTAR VERSIÓN PARA OBLIGAR ACTUALIZACIÓN
gradle_path = "android/app/build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as f:
    gradle = f.read()

gradle = re.sub(r"versionCode\s*=\s*(\d+)", lambda m: f"versionCode = {int(m.group(1)) + 1}", gradle)
def bump_name(match):
    parts = match.group(1).split(".")
    parts[-1] = str(int(parts[-1]) + 1)
    return f'versionName = "{".".join(parts)}"'
gradle = re.sub(r'versionName\s*=\s*"([^"]+)"', bump_name, gradle)

with open(gradle_path, "w", encoding="utf-8") as f:
    f.write(gradle)

print("\n✅ [ÉXITO] MODO TRACTOR INYECTADO: RAM salvada, TextureView Forzado, Auto-Reanimador activo y Versión actualizada.\n")
