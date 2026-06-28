async function escanearEnlace() {
    console.log("\n🔍 RADIOGRAFÍA: ANALIZANDO EL ENLACE DE REPRODUCCIÓN...");
    try {
        const res = await fetch('http://127.0.0.1/api/content/live-group?code=253698&key=__all__&limit=5');
        const data = await res.json();

        if (data.data && data.data.length > 0) {
            const canal = data.data[0];
            console.log(`\n📺 CANAL DE PRUEBA: ${canal.name}`);
            console.log(`🔗 URL EXACTA QUE LA APP INTENTA REPRODUCIR:`);
            console.log(`👉 ${canal.url}`);

            console.log(`\n📡 GOLPEANDO LA PUERTA DEL SERVIDOR DE VIDEO...`);
            const streamRes = await fetch(canal.url, {
                method: 'HEAD',
                headers: {
                    "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 15; moto g84 5G Build/V1TCS35H.88-20-1-6-1)"
                }
            });
            console.log(`✅ RESPUESTA DEL SERVIDOR: ${streamRes.status} ${streamRes.statusText}`);
        } else {
            console.log("⚠️ No se encontraron canales en la base de datos.");
        }
    } catch (e) {
        console.log(`💥 ERROR CRÍTICO: ${e.message}`);
    }
}
escanearEnlace();
