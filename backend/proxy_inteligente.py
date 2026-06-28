import re

srv_path = 'src/server.js'
with open(srv_path, 'r', encoding='utf-8') as f:
    data_srv = f.read()

# Buscamos el proxy viejo y lo sacamos
patron_proxy_viejo = r"// --- PROXY MAGMA OTT PARA LIVE TV ---[\s\S]*?(?=module\.exports =)"
data_limpia = re.sub(patron_proxy_viejo, "", data_srv)

# Escribimos el proxy inteligente basado en el PCAP
proxy_nuevo = """
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

        // 1. Hacemos el POST (Ya no buscamos un redirect manual)
        const resGen = await fetch(urlPeticion, {
            method: 'POST',
            headers: headers,
            body: bodyData
        });

        const contentType = resGen.headers.get("content-type");
        
        // 2. Analizamos qué nos devuelve Magma
        if (contentType && contentType.includes("application/json")) {
            const data = await resGen.json();
            // Si nos da un JSON con el token, armamos la URL segura
            if (data && data.token) {
                const urlSegura = `${baseUrl}/stream/secure/${data.token}/${streamId}.m3u8`;
                return res.redirect(302, urlSegura);
            }
        } 
        
        // 3. Plan B: Si nos devuelve el m3u8 directamente como texto
        const texto = await resGen.text();
        if (texto.includes("#EXTM3U") || texto.includes("stream/secure")) {
             // Devolvemos el texto directo a tu app, haciendo un "pasamanos"
             res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
             return res.send(texto);
        }

        return res.status(404).send('No se pudo generar la URL segura de Magma');
        
    } catch (e) {
        console.error("Proxy Error:", e.message);
        res.status(500).send('Proxy Timeout');
    }
});
"""

data_final = data_limpia + proxy_nuevo + "\nmodule.exports = app;\n"

with open(srv_path, 'w', encoding='utf-8') as f:
    f.write(data_final)

print("✅ Proxy Inteligente instalado en server.js")
