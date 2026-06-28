import re

srv_path = 'src/server.js'
with open(srv_path, 'r', encoding='utf-8') as f:
    data_srv = f.read()

# Borramos los proxies viejos
patron_proxy = r"// --- PROXY MAGMA OTT PARA LIVE TV ---[\s\S]*?(?=module\.exports =)"
data_limpia = re.sub(patron_proxy, "", data_srv)

# Inyectamos el proxy basado en tu comando cURL exitoso
proxy_nuevo = """
// --- PROXY MAGMA OTT PARA LIVE TV ---
app.get('/api/magma-lite/live/:streamId', async (req, res) => {
    try {
        const streamId = req.params.streamId.replace('.m3u8', '');
        const baseUrl = 'http://tv.m3uts.xyz';

        const urlPeticion = `${baseUrl}/stream/gen/${streamId}`;
        
        // Exactamente los mismos datos que usaste en Termux
        const bodyData = new URLSearchParams({
            "id": streamId,
            "cast": "false",
            "device": "c0041021c5c95679",
            "code": ""
        });

        const resGen = await fetch(urlPeticion, { 
            method: 'POST', 
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 15; moto g84 5G)"
            },
            body: bodyData 
        });

        const token = (await resGen.text()).trim();

        // Si nos da el token de ~52 caracteres, armamos la URL segura
        if (resGen.status === 200 && token.length > 5) {
            const urlSegura = `${baseUrl}/stream/secure/${token}/${streamId}.m3u8`;
            return res.redirect(302, urlSegura);
        }

        console.error("Magma falló al generar token. Respuesta:", token);
        return res.status(404).send('Error al obtener llave de Magma');
        
    } catch (e) {
        console.error("Proxy Error Critico:", e.message);
        res.status(500).send('Error en puente interno');
    }
});
"""

data_final = data_limpia + proxy_nuevo + "\nmodule.exports = app;\n"
with open(srv_path, 'w', encoding='utf-8') as f:
    f.write(data_final)

print("✅ Proxy clonado del cURL instalado exitosamente.")
