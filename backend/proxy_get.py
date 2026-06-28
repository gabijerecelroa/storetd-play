import re

srv_path = 'src/server.js'
with open(srv_path, 'r', encoding='utf-8') as f:
    data_srv = f.read()

# Borramos los intentos fallidos anteriores
patron_proxy_viejo = r"// --- PROXY MAGMA OTT PARA LIVE TV ---[\s\S]*?(?=module\.exports =)"
data_limpia = re.sub(patron_proxy_viejo, "", data_srv)

proxy_nuevo = """
// --- PROXY MAGMA OTT PARA LIVE TV ---
app.get('/api/magma-lite/live/:streamId', async (req, res) => {
    try {
        const streamId = req.params.streamId.replace('.m3u8', '');
        const baseUrl = 'http://tv.m3uts.xyz';

        const headers = {
            "User-Agent": "Magma Player/10",
            "X-App": "di",
            "X-Version": "10/1.0.9",
            "X-Did": "c0041021c5c95679",
            "X-Hash": "MVRUcQA5ddQ6Q7uvtD3Ms8ucj_Sj0SSzBNyfWBAeDrWPiwDugKt5m7OlmmsvMbJ4Gqc7qoaTbR47HgkHQ0kyHjk2Q20f5TMexj3o9gNRhmprUJmWXWpDQYyx-xAOEx1MV9R0m9Q-GYH2CqzKS_rIlpb0hge4Moy7FRomMTQpPK047WahnRTpbycnW517aYWIdb20KEZy9RVbHoVZ4gIwY19ZxfLB-QRXubBGyTPFkxLfrZh2cnh-AsdaNbkQKuBqbu0F1Ya-VaQb4tb1C2O3Er14lNrP-R9MnXbltt_yahHYND94F90kqLRacnURZP76e6r6d9xTzl3940FLneH-UpYdPxnuNc9C7S-cwYrs2DMHdNE5WWZ3s-FuesB9Mz25tZd0rIRGGb7dnjZY_FjAx08R3hzsywOLpGWUBT_4OCH051l21jTc29hXwiwj-1vo3eRbjUkgzXJlwvTBS2RAAld5NzPs6kFVjxSr729niVrf9j4WtOJnVQfAESCIFbNnDudLB4VdBeb2w58rTAGo-Q"
        };

        // LA REVELACION: Hacemos un GET directo como la app nativa, nada de POSTs.
        const urlPeticion = `${baseUrl}/live/m/m/${streamId}.m3u8`;

        const resGen = await fetch(urlPeticion, { 
            method: 'GET', 
            headers: headers, 
            redirect: 'manual' 
        });

        // Si Magma nos aprueba, tira un 302 hacia /stream/secure/
        if (resGen.status >= 300 && resGen.status < 400) {
            let location = resGen.headers.get('location');
            if (location && !location.startsWith('http')) {
                location = baseUrl + location;
            }
            return res.redirect(302, location);
        }

        // Si devuelve el video 200 directo
        if (resGen.status === 200) {
            const texto = await resGen.text();
            res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
            return res.send(texto);
        }

        // Si Magma rechaza el hash, vemos por qué
        const errText = await resGen.text();
        console.error(`Magma bloqueó el GET (Status: ${resGen.status}):`, errText.substring(0, 100));
        return res.status(resGen.status).send('Hash rechazado o expirado');
        
    } catch (e) {
        console.error("Proxy Error Critico:", e.message);
        res.status(500).send('Error en puente');
    }
});
"""

data_final = data_limpia + proxy_nuevo + "\nmodule.exports = app;\n"
with open(srv_path, 'w', encoding='utf-8') as f:
    f.write(data_final)

print("✅ Proxy GET Directo instalado.")
