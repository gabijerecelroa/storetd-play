import re
import os

file_path = "src/server.js"
if not os.path.exists(file_path):
    print("❌ Archivo src/server.js no encontrado.")
    exit()

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

injector = """
    // 🔥 AUDITORIA Y CONVERSION HLS 🔥
    let finalUrl = streamUrl;
    if (finalUrl && !finalUrl.includes('.m3u8') && !finalUrl.includes('movie') && !finalUrl.includes('series')) {
        try {
            let parts = finalUrl.split('/');
            let id = parts.pop().replace('.ts', '');
            let pass = parts.pop();
            let user = parts.pop();
            let base = parts.join('/');
            if(base.endsWith('/live')) base = base.substring(0, base.length - 5);
            
            finalUrl = `${base}/live/${user}/${pass}/${id}.m3u8`;
            
            console.log('\\n======================================================');
            console.log(' 📺 [AUDITORIA LIVE TV] ¡LA APP DIO PLAY A UN CANAL!');
            console.log(' ✅ Enlace HLS entregado: ' + finalUrl.replace(pass, '****'));
            console.log('======================================================\\n');
        } catch(e) {}
    }
    lines.push(finalUrl);
"""

if "AUDITORIA Y CONVERSION HLS" not in content:
    # Hacemos la cirugía exacta en la línea 4334
    new_content = content.replace("lines.push(streamUrl);", injector)
    if new_content != content:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(new_content)
        print("\\n✅ [ÉXITO TOTAL] CEREBRO HLS Y RÁDAR DE AUDITORÍA INYECTADOS EN EL NÚCLEO.\\n")
    else:
        print("\\n❌ [ERROR] No se encontró la línea 'lines.push(streamUrl);' en server.js\\n")
else:
    print("\\n⚠️ [AVISO] El Rádar HLS ya estaba instalado en tu servidor.\\n")

