const http = require('http');
const codes = ['253698', '901177', '669911'];

console.log("\n📡 PROBANDO EXTRACCIÓN DE VIDEO EN DPLATINO 📡");
codes.forEach(code => {
    http.get(`http://localhost:5000/magma-lite/live/454926.m3u8?code=${code}`, (res) => {
        console.log(`\n➤ CÓDIGO [ ${code} ] -> HTTP ${res.statusCode}`);
        if(res.statusCode === 302 || res.statusCode === 301) {
            console.log(`   ✅ ÉXITO: El servidor extrajo el video de Dplatino y está listo para reproducir.`);
        } else {
            console.log(`   ❌ ERROR: El servidor rechazó el video.`);
            console.log(`   💡 Motivo: O el código no está asignado a Dplatino en Supabase, o está inactivo.`);
        }
    }).on('error', (e) => console.log(`   ❌ Falla local: ${e.message}`));
});
