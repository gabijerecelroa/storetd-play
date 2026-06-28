async function escanear() {
    console.log("\n🔍 INICIANDO RADIOGRAFÍA DEL CEREBRO DEL BACKEND...");
    try {
        console.log("⏳ Simulando activación de la app...");
        const authRes = await fetch('http://82.39.109.213/auth/activate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'User-Agent': 'Dalvik/2.1.0' },
            body: JSON.stringify({ customerName: "Gabriel", activationCode: "253698", deviceCode: "radiografia-x", appVersion: "1.6.83" })
        });
        const authData = await authRes.json();
        console.log(`🔑 URL asignada en tu base de datos: ${authData.playlistUrl}`);

        console.log("\n📺 Pidiendo lista de canales en vivo a tu servidor...");
        const liveRes = await fetch('http://82.39.109.213/api/content/live-group?code=253698&key=__all__&limit=3');
        const liveData = await liveRes.json();
        
        if (liveData.data && liveData.data.length > 0) {
            console.log(`\n💀 DIAGNÓSTICO: Tu backend está entregando estos canales:`);
            console.log(`1. ${liveData.data[0].name}`);
            console.log(`2. ${liveData.data[1] ? liveData.data[1].name : 'N/A'}`);
            
            // Analizar de dónde viene la respuesta
            if (liveData.data[0].name.includes("E$PN")) {
                console.log(`\n🚨 ¡CONFIRMADO! El servidor está infectado con la caché vieja.`);
            }
        } else {
            console.log(`\n⚠️ Tu backend devolvió una lista vacía.`);
        }
    } catch (e) {
        console.log(`💥 ERROR de conexión: ${e.message}`);
    }
}
escanear();
