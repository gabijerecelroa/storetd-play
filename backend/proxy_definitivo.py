import re

srv_path = 'src/server.js'
with open(srv_path, 'r', encoding='utf-8') as f:
    data_srv = f.read()

# Sacamos el proxy anterior
patron_proxy_viejo = r"// --- PROXY MAGMA OTT PARA LIVE TV ---[\s\S]*?(?=module\.exports =)"
data_limpia = re.sub(patron_proxy_viejo, "", data_srv)

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

        const resGen = await fetch(urlPeticion, { method: 'POST', headers, body: bodyData });
        const textoPuro = (await resGen.text()).trim();

        // 1. Intento: ¿Es un JSON?
        try {
            const json = JSON.parse(textoPuro);
            if (json.token) {
                return res.redirect(302, `${baseUrl}/stream/secure/${json.token}/${streamId}.m3u8`);
            }
        } catch(e) {} // Si falla, no pasa nada, seguimos abajo

        // 2. Intento: ¿Es el TOKEN EN TEXTO PLANO? (La clave que descubrió la IA)
        // Si tiene entre 5 y 40 caracteres y no tiene código HTML, es el hash seguro.
        if (textoPuro.length > 3 && textoPuro.length < 40 && !textoPuro.includes("<")) {
            const urlSegura = `${baseUrl}/stream/secure/${textoPuro}/${streamId}.m3u8`;
            return res.redirect(302, urlSegura);
        }

        // 3. Intento: ¿Es un M3U8 directo?
        if (textoPuro.includes("#EXTM3U")) {
             res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
             return res.send(textoPuro);
        }

        // Si llegó hasta acá, Magma nos bloqueó. Imprimimos el error para verlo en los logs.
        console.error("Magma devolvió algo inesperado:", textoPuro);
        return res.status(404).send('Contenido no disponible (Hash fallido)');
        
    } catch (e) {
        console.error("Proxy Error Critico:", e.message);
        res.status(500).send('Error en puente');
    }
});
"""

data_final = data_limpia + proxy_nuevo + "\nmodule.exports = app;\n"
with open(srv_path, 'w', encoding='utf-8') as f:
    f.write(data_final)

print("✅ Proxy Token Catcher instalado.")
