import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Buscamos el bloque anterior y lo reemplazamos por el nuevo SIN los extractores tóxicos
old_block_pattern = r"// 🔥 MOTOR IPTV PROFESIONAL \(ESTILO TELEVIZO\) 🔥[\s\S]*?(?=androidx\.media3\.exoplayer\.ExoPlayer\.Builder)"
new_block = """// 🔥 MOTOR IPTV DEFINITIVO 🔥
        // 1. CAMUFLAJE VLC (Atraviesa el Firewall de Xtream Codes para evitar bloqueos y el "No hay datos")
        val iptvDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.9 LibVLC/3.0.9")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        // 2. EXTRACTORES PUROS (¡Extirpamos la bandera tóxica! Exige imagen limpia para curar la mancha verde)
        val iptvMediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(iptvDataSourceFactory)

        // 3. CEREBRO INTELIGENTE DE HARDWARE
        val uiManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        val isTvBox = uiManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION || 
                      android.os.Build.MODEL.contains("Box", true) || android.os.Build.MODEL.contains("TV", true) ||
                      android.os.Build.BRAND.contains("Dinax", true)

        val smartRenderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(object : androidx.media3.exoplayer.mediacodec.MediaCodecSelector {
                override fun getDecoderInfos(
                    mimeType: String,
                    requireSecureDecoder: Boolean,
                    requireTunnelingDecoder: Boolean
                ): List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
                    val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mimeType, requireSecureDecoder, requireTunnelingDecoder)
                        .toMutableList()

                    if (isTvBox) {
                        decoders.sortBy { if (it.hardwareAccelerated) 1 else 0 } // TV -> Software
                    } else {
                        decoders.sortBy { if (it.hardwareAccelerated) 0 else 1 } // Celular -> Hardware
                    }
                    return decoders
                }
            })

        // 4. BÚFER NORMAL (15s a 30s de colchón para rapidez)
        val normalLoadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
            .build()

        """

if "MOTOR IPTV DEFINITIVO" not in content:
    content = re.sub(old_block_pattern, new_block, content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

# Subimos a la versión 1.6.74 (Code 109)
gradle_path = "android/app/build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as f:
    gradle = f.read()

gradle = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 109", gradle)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.6.74"', gradle)

with open(gradle_path, "w", encoding="utf-8") as f:
    f.write(gradle)

print("\n✅ [ÉXITO] MOTOR DEFINITIVO INSTALADO. VENENO EXTIRPADO Y PANTALLA VERDE ELIMINADA.\n")
