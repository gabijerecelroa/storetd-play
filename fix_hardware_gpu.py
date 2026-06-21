import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. BISTURÍ DE PRECISIÓN: Extirpamos el Decodificador por Software (El asesino de Telefe FHD)
bad_code = """
            .setMediaCodecSelector(object : androidx.media3.exoplayer.mediacodec.MediaCodecSelector {
                override fun getDecoderInfos(
                    mimeType: String,
                    requireSecureDecoder: Boolean,
                    requireTunnelingDecoder: Boolean
                ): List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
                    val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mimeType, requireSecureDecoder, requireTunnelingDecoder)
                        .toMutableList()

                    // 🔥 MODO TELEVIZO: Prioriza procesador de software (Cura pantalla verde y congelamientos)
                    decoders.sortByDescending { it.name.startsWith("OMX.google.") || it.name.startsWith("c2.android.") || it.name.contains("sw", ignoreCase = true) }
                    return decoders
                }
            })"""

if bad_code in content:
    content = content.replace(bad_code, "")
else:
    # Respaldo por si el formato cambió ligeramente
    content = re.sub(r"\s*\.setMediaCodecSelector\([\s\S]*?return decoders\s*\}\s*\}\)", "", content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

# 2. SELLAR LA VERSIÓN DE LA VICTORIA: 1.6.72 (Code 107)
gradle_path = "android/app/build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as f:
    gradle = f.read()

gradle = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 107", gradle)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.6.72"', gradle)

with open(gradle_path, "w", encoding="utf-8") as f:
    f.write(gradle)

print("\n✅ [ÉXITO] PROCESADOR LIBERADO. EL PODER DE LA TARJETA GRÁFICA FUE RESTAURADO AL 100%.\n")
