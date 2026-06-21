import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Limpiamos cualquier rastro del TextureView viejo que enfermó a tu celular
content = re.sub(r"// 🔥 MODO TRACTOR: Fuerza TextureView\s*\n\s*surfaceType\s*=\s*androidx\.media3\.ui\.PlayerView\.SURFACE_TYPE_TEXTURE_VIEW", "", content)

# 2. Inyectamos el Búfer Dinámico Estilo Televizo
load_control_pattern = r"val loadControl\s*=\s*androidx\.media3\.exoplayer\.DefaultLoadControl\.Builder\(\)[\s\S]*?\.build\(\)"
televizo_buffer = """// 🚀 MOTOR BUFFER ESTILO TELEVIZO (Adaptable)
        val tempUiManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        val isLowRamBox = tempUiManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION || 
                          android.os.Build.MODEL.contains("Box", true) || android.os.Build.BRAND.contains("Dinax", true) ||
                          android.os.Build.MANUFACTURER.contains("Rockchip", true) || android.os.Build.VERSION.SDK_INT < 26

        val maxB = if (isLowRamBox) 45_000 else 120_000 // 45s Cajas Chinas vs 2 Minutos para Alta Gama (Televizo)
        val minB = if (isLowRamBox) 15_000 else 30_000
        val rebB = if (isLowRamBox) 3_000 else 5_000

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBufferDurationsMs(minB, maxB, 1_500, rebB)
            .build()"""

if "MOTOR BUFFER ESTILO TELEVIZO" not in content:
    content = re.sub(load_control_pattern, televizo_buffer, content)

# 3. Inyectamos el Detector Gráfico (Mata la pantalla verde en celulares y la negra en TVs)
detector_grafico = """
                    // 🧠 DETECTOR INTELIGENTE DE HARDWARE GRÁFICO
                    val uiManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
                    val isTvBox = uiManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION || 
                                  android.os.Build.MODEL.contains("Box", true) || 
                                  android.os.Build.BRAND.contains("Dinax", true) ||
                                  android.os.Build.MANUFACTURER.contains("Rockchip", true)

                    surfaceType = if (isTvBox) {
                        androidx.media3.ui.PlayerView.SURFACE_TYPE_TEXTURE_VIEW // Modo Tractor para Dinax
                    } else {
                        androidx.media3.ui.PlayerView.SURFACE_TYPE_SURFACE_VIEW // Modo Nativo Rápido para Celulares
                    }"""

if "DETECTOR INTELIGENTE DE HARDWARE GRÁFICO" not in content:
    content = re.sub(r"(PlayerView\s*\([^)]+\)\.apply\s*\{)", r"\1" + detector_grafico, content)

# 4. Inyectamos el Escudo Anti-Cortes (Live Catch-up) de Televizo
content = re.sub(
    r"MediaItem\.fromUri\(currentChannel\.streamUrl\)",
    """androidx.media3.common.MediaItem.Builder()
                    .setUri(currentChannel.streamUrl)
                    .setLiveConfiguration(
                        androidx.media3.common.MediaItem.LiveConfiguration.Builder()
                            .setMaxPlaybackSpeed(1.02f) // Acelera imperceptiblemente si se atrasa mucho para evitar cortes
                            .setTargetOffsetMs(10000)   // Escudo anti-cortes de 10 segundos de reserva
                            .build()
                    )
                    .build()""",
    content
)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

# 5. Actualizamos la versión a 1.6.70 (Code 105)
gradle_path = "android/app/build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as f:
    gradle = f.read()

gradle = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 105", gradle)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.6.70"', gradle)

with open(gradle_path, "w", encoding="utf-8") as f:
    f.write(gradle)

print("\n✅ [ÉXITO] MOTOR ESTILO TELEVIZO + CURA DE PANTALLA VERDE INSTALADOS (Versión 1.6.70).\n")
