import os

print("\n=======================================================")
print(" 🧬 INYECTANDO HLS DIRECTO EN EL ADN DEL SERVIDOR 🧬")
print("=======================================================\n")

# 1. Quitamos el bisturí viejo del server.js para evitar conflictos
sv_path = "src/server.js"
with open(sv_path, "r", encoding="utf-8") as f: sv = f.read()
if "BISTURÍ HLS" in sv:
    import re
    sv = re.sub(r'// 🔥 BISTURÍ HLS.*?next\(\);\n\}\);\n', '', sv, flags=re.DOTALL)
    with open(sv_path, "w", encoding="utf-8") as f: f.write(sv)
    print("🧹 Bisturí anterior removido.")

# 2. Inyectamos .m3u8 directamente en el generador de listas (playlistContent.js)
pc_path = "src/playlistContent.js"
with open(pc_path, "r", encoding="utf-8") as f: pc = f.read()

# Forzamos que las extensiones por defecto sean m3u8 para Live
pc = pc.replace("ext = 'ts'", "ext = 'm3u8'")
pc = pc.replace('ext || "ts"', 'ext || "m3u8"')
pc = pc.replace('ext="ts"', 'ext="m3u8"')

with open(pc_path, "w", encoding="utf-8") as f: f.write(pc)
print("✅ [ÉXITO] El servidor ahora despachará los canales en Vivo nativamente en .m3u8.")
print("\n=======================================================\n")
