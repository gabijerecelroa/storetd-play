import re
import os

print("\n🚀 INICIANDO PARCHE MAGMA OTT...")

# 1. PARCHEAR playlistContent.js
pl_path = 'src/playlistContent.js'
if os.path.exists(pl_path):
    with open(pl_path, 'r', encoding='utf-8') as f:
        data = f.read()

    # Reemplazamos la funcion xtreamLiveUrl entera por la nueva
    pattern = r"function xtreamLiveUrl\s*\([^)]*\)\s*\{[\s\S]*?\}"
    replacement = """function xtreamLiveUrl(playlistUrl, streamId, ext = "ts") {
  return "http://82.39.109.213/api/magma-lite/live/" + streamId + ".m3u8";
}"""
    
    if "api/magma-lite/live" not in data:
        new_data = re.sub(pattern, replacement, data)
        with open(pl_path, 'w', encoding='utf-8') as f:
            f.write(new_data)
        print("✅ src/playlistContent.js parcheado correctamente.")
    else:
        print("⚠️ src/playlistContent.js ya estaba parcheado.")
else:
    print(f"❌ Error: No se encontró {pl_path}")

# 2. PARCHEAR server.js (o app.js)
srv_path = 'src/server.js'
if not os.path.exists(srv_path):
    srv_path = 'src/app.js'

if os.path.exists(srv_path):
    with open(srv_path, 'r', encoding='utf-8') as f:
        data_srv = f.read()

    proxy_code = """
// --- PROXY MAGMA OTT PARA LIVE TV ---
app.get('/api/magma-lite/live/:streamId', async (req, res) => {
    try {
        const streamId = req.params.streamId.replace('.m3u8', '');
        const baseUrl = 'http://tv.m3uts.xyz';

        const headers = {
            "Content-Type": "application/json",
            "User-Agent": "Magma Player/10",
            "X-App": "di",
            "X-Version": "10/1.0.9",
            "X-Did": "c0041021c5c95679",
            "X-Hash": "MVRUcQA5ddQ6Q7uvtD3Ms8ucj_Sj0SSzBNyfWBAeDrWPiwDugKt5m7OlmmsvMbJ4Gqc7qoaTbR47HgkHQ0kyHjk2Q20f5TMexj3o9gNRhmprUJmWXWpDQYyx-xAOEx1MV9R0m9Q-GYH2CqzKS_rIlpb0hge4Moy7FRomMTQpPK047WahnRTpbycnW517aYWIdb20KEZy9RVbHoVZ4gIwY19ZxfLB-QRXubBGyTPFkxLfrZh2cnh-AsdaNbkQKuBqbu0F1Ya-VaQb4tb1C2O3Er14lNrP-R9MnXbltt_yahHYND94F90kqLRacnURZP76e6r6d9xTzl3940FLneH-UpYdPxnuNc9C7S-cwYrs2DMHdNE5WWZ3s-FuesB9Mz25tZd0rIRGGb7dnjZY_FjAx08R3hzsywOLpGWUBT_4OCH051l21jTc29hXwiwj-1vo3eRbjUkgzXJlwvTBS2RAAld5NzPs6kFVjxSr729niVrf9j4WtOJnVQfAESCIFbNnDudLB4VdBeb2w58rTAGo-Q"
        };

        const urlPeticion = `${baseUrl}/stream/gen/${streamId}`;
        const bodyData = JSON.stringify({ username: "m", password: "m" });

        const resGen = await fetch(urlPeticion, {
            method: 'POST',
            headers: headers,
            body: bodyData,
            redirect: 'manual'
        });

        if (resGen.status >= 300 && resGen.status < 400) {
            let location = resGen.headers.get('location');
            if (location && !location.startsWith('http')) {
                location = baseUrl + location;
            }
            return res.redirect(302, location);
        } else {
            return res.status(404).send('Error Magma Hash');
        }
    } catch (e) {
        console.error("Proxy Error:", e.message);
        res.status(500).send('Proxy Timeout');
    }
});
"""
    if "PROXY MAGMA OTT PARA LIVE TV" not in data_srv:
        # Inyectar el proxy justo antes del module.exports
        if "module.exports =" in data_srv:
            new_data_srv = data_srv.replace("module.exports =", proxy_code + "\nmodule.exports =")
        else:
            new_data_srv = data_srv + "\n" + proxy_code
            
        with open(srv_path, 'w', encoding='utf-8') as f:
            f.write(new_data_srv)
        print(f"✅ {srv_path} parcheado correctamente.")
    else:
        print(f"⚠️ {srv_path} ya tenía el proxy instalado.")
else:
    print("❌ Error: No se encontró server.js ni app.js")

print("🎉 MISION COMPLETADA.\n")
