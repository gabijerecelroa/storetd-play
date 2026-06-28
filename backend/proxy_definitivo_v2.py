import re

srv_path = 'src/server.js'
with open(srv_path, 'r', encoding='utf-8') as f:
    data_srv = f.read()

# Borramos cualquier proxy anterior
patron_proxy = r"// --- PROXY MAGMA OTT PARA LIVE TV ---[\s\S]*?(?=module\.exports =)"
data_limpia = re.sub(patron_proxy, "", data_srv)

proxy_nuevo = """
// --- PROXY MAGMA OTT PARA LIVE TV ---
app.get('/api/magma-lite/live/:streamId', async (req, res) => {
    try {
        const streamId = req.params.streamId.replace('.m3u8', '');
        const urlPeticion = `http://tv.m3uts.xyz/stream/gen/${streamId}`;
        
        // Ejecutamos el POST exacto que funciona en tu cURL
        const resGen = await fetch(urlPeticion, { 
            method: 'POST', 
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 15; moto g84 5G)"
            },
            body: new URLSearchParams({
                "id": streamId,
                "cast": "false",
                "device": "c0041021c5c95679",
                "code": ""
            })
        });

        const urlFinal = (await resGen.text()).trim();

        // Si Magma nos devuelve una URL, redirigimos a la app
        if (resGen.status === 200 && urlFinal.startsWith("http")) {
            return res.redirect(302, urlFinal);
        }

        console.error("Magma falló:", urlFinal);
        return res.status(404).send('Error de autenticación Magma');
        
    } catch (e) {
        res.status(500).send('Error en puente');
    }
});
"""

data_final = data_limpia + proxy_nuevo + "\nmodule.exports = app;\n"
with open(srv_path, 'w', encoding='utf-8') as f:
    f.write(data_final)

print("✅ Proxy de alta precisión instalado.")
