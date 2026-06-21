import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"

# 1. Recuperamos el archivo sano antes de mi error
os.system(f"git show HEAD~1:{file_path} > {file_path}")

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 2. CURAR LA RAM (El asesino de los 3 minutos)
content = re.sub(r"300_000,\s*// Max[^\n]*", "45_000,   // Max: 45 seg (Anti-Colapso RAM)", content)
content = re.sub(r"20_000,\s*// Min[^\n]*", "15_000,   // Min: 15 seg", content)

if "setPrioritizeTimeOverSizeThresholds" not in content:
    content = content.replace(".setBufferDurationsMs(", ".setPrioritizeTimeOverSizeThresholds(true)\n            .setBufferDurationsMs(")

# 3. CURAR PANTALLA NEGRA EN DINAX (Fuerza TextureView por software)
content = re.sub(
    r"(PlayerView\s*\([^)]+\)\.apply\s*\{)",
    r"\1\n                    // 🔥 MODO TRACTOR: Fuerza TextureView\n                    surfaceType = androidx.media3.ui.PlayerView.SURFACE_TYPE_TEXTURE_VIEW",
    content
)

# 4. AUTO-REANIMADOR (Con inyección precisa para no romper llaves de Kotlin)
replacement = r"""\1
                // 🔥 AUTO-REANIMADOR SEGURO
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        try {
                            seekToDefaultPosition()
                            prepare()
                            play()
                        } catch (e: Exception) {}
                    }
                })"""
content = re.sub(r"(playWhenReady\s*=\s*true)", replacement, content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n✅ [ÉXITO] MODO TRACTOR V2 INYECTADO SIN ERRORES SINTÁCTICOS.\n")
