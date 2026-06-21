import re
import os

file_path = "src/controllers/xtream.controller.js"

if not os.path.exists(file_path):
    print("❌ Archivo backend no encontrado. Asegúrate de estar en la carpeta backend.")
    exit()

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

patched = False

# 1. CIRUGÍA MATEMÁTICA EXACTA (El método más seguro)
pattern = r"streamUrl:\s*`\$\{baseUrl\}(?:/live)?/\$\{([^}]+)\}/\$\{([^}]+)\}/\$\{stream\.stream_id\}(?:\.ts)?`"

if re.search(pattern, content):
    def replacer(match):
        u = match.group(1)
        p = match.group(2)
        return f"streamUrl: `${{baseUrl}}/live/${{{u}}}/${{{p}}}/${{stream.stream_id}}.m3u8`"
    
    new_content = re.sub(pattern, replacer, content)
    if new_content != content:
        content = new_content
        patched = True

# 2. PLAN B: INYECTOR DE CEREBRO (Si el código es diferente)
if not patched and "CEREBRO HLS" not in content:
    inyector = """
        // 🔥 CEREBRO HLS INYECTADO 🔥
        if (typeof channels !== 'undefined' && Array.isArray(channels)) {
            channels = channels.map(stream => {
                if (stream.stream_type === 'live' || stream.stream_type === 'created_live') {
                    if (stream.streamUrl && !stream.streamUrl.includes('.m3u8')) {
                        let url = stream.streamUrl.replace('.ts', '');
                        let parts = url.split('/');
                        let id = parts.pop();
                        let pwd = parts.pop();
                        let usr = parts.pop();
                        let base = parts.join('/');
                        if (base.endsWith('/live')) {
                            base = base.substring(0, base.length - 5);
                        }
                        stream.streamUrl = `${base}/live/${usr}/${pwd}/${id}.m3u8`;
                    }
                }
                return stream;
            });
        }
        res.json({ categories, channels });"""
    
    new_content = re.sub(r"res\.(?:status\(\d+\)\.)?json\(\{\s*(?:categories,\s*channels|channels,\s*categories)\s*\}\);", inyector, content)
    if new_content != content:
        content = new_content
        patched = True

if patched:
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("\n✅ [ÉXITO TOTAL] BACKEND MUTADO A FORMATO INMORTAL HLS (.m3u8).\n")
else:
    print("\n⚠️ [AVISO] EL BACKEND YA ESTABA PARCHEADO O TIENE UNA ESTRUCTURA DIFERENTE.\n")
