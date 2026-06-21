import os, re

file_path = "android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

helper = """
// 🔥 MUTADOR HLS ANDROID 🔥
fun forceHlsUrl(context: android.content.Context, originalUrl: String): String {
    var finalUrl = originalUrl
    try {
        if (!finalUrl.contains(".m3u8") && !finalUrl.contains("movie") && !finalUrl.contains("series")) {
            val clean = finalUrl.substringBefore("?").replace(".ts", "")
            val parts = clean.split("/")
            if (parts.size >= 4 && !finalUrl.contains("magma-lite") && !finalUrl.contains("xtream-lite")) {
                val id = parts.last()
                val pass = parts[parts.size - 2]
                val user = parts[parts.size - 3]
                var base = parts.dropLast(3).joinToString("/")
                if (base.endsWith("/live")) {
                    base = base.substring(0, base.length - 5)
                }
                finalUrl = "$base/live/$user/$pass/$id.m3u8"
            }
        }
    } catch (e: Exception) {}
    
    // 📡 RÁDAR LOG EN PANTALLA (Para auditoría del Administrador)
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        try {
            val showText = "📡 RÁDAR HLS: " + finalUrl.substringAfter("://").substringBefore("?")
            android.widget.Toast.makeText(context, showText, android.widget.Toast.LENGTH_LONG).show()
        } catch(e: Exception) {}
    }
    
    return finalUrl
}
"""

if "MUTADOR HLS ANDROID" not in content:
    content = content.replace("package com.storetd.play.feature.player", "package com.storetd.play.feature.player\n" + helper)

# Interceptamos el enlace justo antes de que ExoPlayer lo lea
content = re.sub(
    r"androidx\.media3\.common\.MediaItem\.fromUri\(\s*currentChannel\.streamUrl\s*\)",
    "androidx.media3.common.MediaItem.fromUri(forceHlsUrl(context, currentChannel.streamUrl))",
    content
)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

# Sellar la Versión de la Victoria: 1.6.77 (Code 112)
gradle_path = "android/app/build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as f:
    gradle = f.read()

gradle = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 112", gradle)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.6.77"', gradle)

with open(gradle_path, "w", encoding="utf-8") as f:
    f.write(gradle)

print("\n✅ [ÉXITO TOTAL] EL MUTADOR HLS Y EL RÁDAR DE PANTALLA FUERON INYECTADOS EN ANDROID.\n")
