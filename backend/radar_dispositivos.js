const fs = require('fs');

// Extraer credenciales
const envData = fs.readFileSync('.env', 'utf8');
const SUPABASE_URL = envData.match(/SUPABASE_URL=([^ \n]+)/)[1].replace(/['"]/g, '');
let keyMatch = envData.match(/SUPABASE_SERVICE_ROLE_KEY=([^ \n]+)/) || envData.match(/SUPABASE_ANON_KEY=([^ \n]+)/);
const SUPABASE_KEY = keyMatch[1].replace(/['"]/g, '');

async function escanear() {
    try {
        const res = await fetch(`${SUPABASE_URL}/rest/v1/devices?select=id,blocked,last_seen_at`, {
            headers: { 'apikey': SUPABASE_KEY, 'Authorization': `Bearer ${SUPABASE_KEY}` }
        });
        
        const devices = await res.json();
        
        const total = devices.length;
        const activos = devices.filter(d => d.blocked === false).length;
        const bloqueados = total - activos;
        
        // Calcular cuántos se conectaron hoy
        const hoy = new Date().toISOString().split('T')[0];
        const onlineHoy = devices.filter(d => d.last_seen_at && d.last_seen_at.startsWith(hoy)).length;

        console.log("\n=======================================================");
        console.log(" 📡 RADAR DE DISPOSITIVOS: STORETD PLAY 📡");
        console.log("=======================================================\n");
        console.log(`➤ Total de Televisores/Celulares registrados: ${total}`);
        console.log(`➤ Dispositivos Autorizados (Verdes):          ${activos}`);
        console.log(`➤ Dispositivos Bloqueados (Rojos):            ${bloqueados}`);
        console.log(`-------------------------------------------------------`);
        console.log(`🔥 Dispositivos que se conectaron HOY:        ${onlineHoy}`);
        console.log("\n=======================================================\n");

    } catch (error) {
        console.log("❌ Error en el radar:", error.message);
    }
}
escanear();
