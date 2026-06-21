const fs = require('fs');

// 1. Extraer credenciales secretas del .env
const envData = fs.readFileSync('.env', 'utf8');
const SUPABASE_URL = envData.match(/SUPABASE_URL=([^ \n]+)/)[1].replace(/['"]/g, '');
let keyMatch = envData.match(/SUPABASE_SERVICE_ROLE_KEY=([^ \n]+)/) || envData.match(/SUPABASE_ANON_KEY=([^ \n]+)/);
const SUPABASE_KEY = keyMatch[1].replace(/['"]/g, '');

async function req(path) {
    let fetchFn = global.fetch || (...args) => import('node-fetch').then(({default: f}) => f(...args));
    const res = await fetchFn(`${SUPABASE_URL}/rest/v1/${path}`, {
        headers: { 'apikey': SUPABASE_KEY, 'Authorization': `Bearer ${SUPABASE_KEY}` }
    });
    return res.json();
}

async function run() {
    console.log("\n=======================================================");
    console.log(" 🕵️‍♂️ HACKEANDO SUPABASE: LEYENDO BASE DE DATOS 🕵️‍♂️");
    console.log("=======================================================\n");

    try {
        const devices = await req('devices?select=*');
        const clients = await req('clients?select=*');
        
        if(devices.error) {
            console.log("❌ Error leyendo devices:", devices.error);
            return;
        }

        // Buscar cómo se llama la columna del código
        const codeField = devices[0].activation_code !== undefined ? 'activation_code' : (devices[0].activationCode !== undefined ? 'activationCode' : 'code');
        
        const goodD = devices.find(d => d[codeField] == '253698');
        const badD = devices.find(d => d[codeField] == '901177');

        console.log("➤ TU TV SANA [253698] (La de 718 Canales):");
        console.log(goodD);
        if (goodD && goodD.client_id && !clients.error) {
            console.log("   ↳ Datos del Cliente Asociado:");
            console.log(clients.find(c => c.id == goodD.client_id || c.uuid == goodD.client_id));
        }

        console.log("\n➤ TV ENFERMA [901177] (La de 3466 Canales):");
        console.log(badD);
        if (badD && badD.client_id && !clients.error) {
            console.log("   ↳ Datos del Cliente Asociado:");
            console.log(clients.find(c => c.id == badD.client_id || c.uuid == badD.client_id));
        }
        
        console.log("\n=======================================================");
        console.log("⚠️ PÉGAME ESTE RESULTADO EN EL CHAT Y TE DARÉ EL COMANDO FINAL.");

    } catch (e) {
        console.log("❌ Error fatal:", e.message);
    }
}
run();
