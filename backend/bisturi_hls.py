import os, re

print("\n=======================================================")
print(" 🔪 INICIANDO CIRUGÍA HLS (TV EN VIVO) 🔪")
print("=======================================================\n")

sv_path = "src/server.js"
with open(sv_path, "r", encoding="utf-8") as f:
    sv = f.read()

middleware = """
// 🔥 BISTURÍ HLS: FORZAR .M3U8 SOLO EN TV EN VIVO 🔥
app.use((req, res, next) => {
    // Si la ruta incluye 'live' y pide un .ts, lo obligamos a usar .m3u8
    if (req.url && (req.url.includes('/live/') || req.url.includes('/magma-lite/live/')) && req.url.includes('.ts')) {
        req.url = req.url.replace(/\\.ts(\\?|$)/, '.m3u8$1');
    }
    next();
});
"""

if "BISTURÍ HLS" not in sv:
    # Inyectamos el middleware justo después de inicializar la app
    sv = sv.replace("const app = express();", "const app = express();\n" + middleware)
    with open(sv_path, "w", encoding="utf-8") as f:
        f.write(sv)
    print("✅ [ÉXITO] Escudo protector HLS (.m3u8) instalado.")
else:
    print("⚠️ El escudo HLS ya estaba instalado.")

print("\n=======================================================\n")
