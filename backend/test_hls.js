const http = require('http');

// Usamos a tu usuario sano para la prueba
const code = '253698'; 

// Le pedimos a propósito el formato pesado (.ts) para ver si el servidor lo muta
const urlTS = `http://localhost:5000/magma-lite/live/454926.ts?code=${code}`;

console.log("\n=======================================================");
console.log(" 📡 SIMULADOR DE TV: PRUEBA DE MUTACIÓN HLS 📡");
console.log("=======================================================\n");
console.log(`➤ ENVIANDO PETICIÓN: GET ${urlTS}\n`);

http.get(urlTS, (res) => {
    console.log(`[RESPUESTA DEL SERVIDOR] -> Código HTTP ${res.statusCode}`);
    
    if (res.statusCode === 302 || res.statusCode === 301 || res.statusCode === 307) {
        const urlFinal = res.headers.location;
        console.log(`✅ ¡INTERCEPCIÓN EXITOSA! El servidor desvió el tráfico hacia:`);
        console.log(`🔗 ${urlFinal}\n`);
        
        if (urlFinal.includes('.m3u8')) {
            console.log(`🏆 DIAGNÓSTICO PERFECTO: El bisturí funciona. Entraste pidiendo '.ts' y el servidor te entregó '.m3u8'. ¡Tienes 60 FPS garantizados!`);
        } else {
            console.log(`❌ DIAGNÓSTICO FALLIDO: El enlace final sigue siendo pesado. Algo bloqueó el bisturí.`);
        }
    } else {
        console.log(`⚠️ El servidor no hizo una redirección al proveedor (Status: ${res.statusCode}).`);
    }
    console.log("\n=======================================================\n");
}).on('error', (e) => console.log(`❌ Falla de conexión local: ${e.message}`));
