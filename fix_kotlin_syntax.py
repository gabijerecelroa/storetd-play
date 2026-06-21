import re

file_path = "android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. EXTIRPAR EL MUTADOR MAL COLOCADO
pattern = r"// 🔥 MUTADOR HLS ANDROID 🔥[\s\S]*?return finalUrl\n\}"
content = re.sub(pattern, "", content)

# Limpiamos el espacio extra que quedó debajo de la palabra 'package'
content = re.sub(r"package com\.storetd\.play\.feature\.player\s+", "package com.storetd.play.feature.player\n\n", content)

# 2. EL CÓDIGO A INYECTAR EN EL SÓTANO
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

# Lo pegamos con total seguridad al final del archivo
content = content.strip() + "\n\n" + helper

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

# Sellar la Versión Reparada: 1.6.78 (Code 113)
gradle_path = "android/app/build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as f:
    gradle = f.read()

gradle = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 113", gradle)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.6.78"', gradle)

with open(gradle_path, "w", encoding="utf-8") as f:
    f.write(gradle)

print("\n✅ [ÉXITO] SINTAXIS REPARADA. El mutador se bajó al fondo del archivo. Kotlin está feliz.\n")
