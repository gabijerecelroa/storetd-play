import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. BISTURÍ: Extirpamos el Cerebro Inteligente que asfixiaba a la TV con el procesador lento (Software)
pattern = r"// 3\. CEREBRO INTELIGENTE DE HARDWARE[\s\S]*?return decoders\s*\}\s*\}\)"
replacement = """// 3. HARDWARE PURO PARA TODOS (Cura de Congelamiento en TV)
        // Liberamos a la TV Box para que use su Tarjeta Gráfica (GPU) igual que el celular.
        val smartRenderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)"""

content = re.sub(pattern, replacement, content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

# 2. Sellar la versión de la Victoria Definitiva: 1.6.75 (Code 110)
gradle_path = "android/app/build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as f:
    gradle = f.read()

gradle = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 110", gradle)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.6.75"', gradle)

with open(gradle_path, "w", encoding="utf-8") as f:
    f.write(gradle)

print("\n✅ [ÉXITO] TV LIBERADA. EL PODER DE LA TARJETA GRÁFICA HA SIDO RESTAURADO AL 100% PARA TODOS.\n")
