import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Limpiamos cualquier acelerador de video previo que asfixie la red
content = re.sub(
    r"androidx\.media3\.common\.MediaItem\.Builder\(\)[\s\S]*?\.setUri\(currentChannel\.streamUrl\)[\s\S]*?\.setLiveConfiguration\([\s\S]*?\.build\(\)\s*\)\s*\.build\(\)",
    "androidx.media3.common.MediaItem.fromUri(currentChannel.streamUrl)",
    content
)

# 2. Inyectamos el ADN Híbrido de Televizo
iptv_factory = """// 🔥 MOTOR IPTV PROFESIONAL (ESTILO TELEVIZO) 🔥
        // 1. EXTRACTORES TOLERANTES (Evita que la imagen se congele por errores de la antena)
        val iptvExtractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )

        // 2. CAMUFLAJE VLC (Atraviesa el Firewall de Xtream Codes para evitar el "Cargando..." eterno)
        val iptvDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.9 LibVLC/3.0.9") 
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val iptvMediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, iptvExtractorsFactory)
            .setDataSourceFactory(iptvDataSourceFactory)

        // 3. DECODIFICADOR INTELIGENTE (El secreto de tus fotos)
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
                        // 📺 TV: "Prefiero Software" (Exactamente igual que Televizo en tu foto)
                        decoders.sortBy { if (it.hardwareAccelerated) 1 else 0 }
                    } else {
                        // 📱 CELULAR: "Prefiero Hardware" (Máxima potencia gráfica para no trabarse)
                        decoders.sortBy { if (it.hardwareAccelerated) 0 else 1 }
                    }
                    return decoders
                }
            })

        // 4. BÚFER "NORMAL" (Igual que Televizo)
        val normalLoadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
            .build()

        androidx.media3.exoplayer.ExoPlayer.Builder(context, smartRenderersFactory)
            .setMediaSourceFactory(iptvMediaSourceFactory)
            .setLoadControl(normalLoadControl)"""

# Sustituimos la configuración de Exoplayer actual por la de Televizo
pattern_builder = r"androidx\.media3\.exoplayer\.ExoPlayer\.Builder\(\s*context[^\)]*\)[\s\S]*?(?=\.build\(\))"
if "MOTOR IPTV PROFESIONAL" not in content:
    content = re.sub(pattern_builder, iptv_factory + "\n            ", content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

# 3. Sellar la versión suprema: 1.6.73 (Code 108)
gradle_path = "android/app/build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as f:
    gradle = f.read()

gradle = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 108", gradle)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.6.73"', gradle)

with open(gradle_path, "w", encoding="utf-8") as f:
    f.write(gradle)

print("\n✅ [ÉXITO] ADN DE TELEVIZO INSTALADO: Prefiero Software en TV, Buffer Normal y Extractores VLC.\n")
