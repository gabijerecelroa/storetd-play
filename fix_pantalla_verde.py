import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

custom_selector_tv = """
            .setMediaCodecSelector(object : androidx.media3.exoplayer.mediacodec.MediaCodecSelector {
                override fun getDecoderInfos(
                    mimeType: String,
                    requireSecureDecoder: Boolean,
                    requireTunnelingDecoder: Boolean
                ): List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
                    val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mimeType, requireSecureDecoder, requireTunnelingDecoder)
                        .toMutableList()

                    // 🔥 MODO SEGURO PARA TV EN VIVO: Priorizar decodificadores por software (CPU)
                    // Evita pantallazos verdes y macroblocking en canales entrelazados (América, Telefe)
                    decoders.sortByDescending { it.name.startsWith("OMX.google.") || it.name.startsWith("c2.android.") || it.name.contains("sw", ignoreCase = true) }
                    
                    return decoders
                }
            })"""

if "MODO SEGURO PARA TV EN VIVO" not in content:
    content = content.replace(
        "val tvRenderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)\n            .setEnableDecoderFallback(true)",
        f"val tvRenderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)\n            .setEnableDecoderFallback(true){custom_selector_tv}"
    )

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

# Subimos automáticamente la versión a 1.6.70 (Code 105)
gradle_path = "android/app/build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as f:
    gradle = f.read()

gradle = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 105", gradle)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.6.70"', gradle)

with open(gradle_path, "w", encoding="utf-8") as f:
    f.write(gradle)

print("\n✅ [ÉXITO] MODO SEGURO DE VIDEO INYECTADO. Versión 1.6.70 (Code 105) lista para compilar.\n")
