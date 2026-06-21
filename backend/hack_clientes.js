const fs = require('fs');

// 1. Extraer credenciales
const envData = fs.readFileSync('.env', 'utf8');
const SUPABASE_URL = envData.match(/SUPABASE_URL=([^ \n]+)/)[1].replace(/['"]/g, '');
let keyMatch = envData.match(/SUPABASE_SERVICE_ROLE_KEY=([^ \n]+)/) || envData.match(/SUPABASE_ANON_KEY=([^ \n]+)/);
const SUPABASE_KEY = keyMatch[1].replace(/['"]/g, '');

async function run() {
    const fetchFn = global.fetch;
    const req = async (path) => {
        const res = await fetchFn(`${SUPABASE_URL}/rest/v1/${path}`, {
            headers: { 'apikey': SUPABASE_KEY, 'Authorization': `Bearer ${SUPABASE_KEY}` }
        });
        return res.json();
    };

    console.log("\n=======================================================");
    console.log(" 🕵️‍♂️ HACKEANDO TABLA CLIENTES (SUPABASE) 🕵️‍♂️");
    console.log("=======================================================\n");

    try {
        const clients = await req('clients?select=*');
        if (clients.error) {
            console.log("❌ Error leyendo clients:", clients.error);
            return;
        }
        
        if (clients.length === 0) {
            console.log("⚠️ La tabla clients está vacía.");
            return;
        }

        // Buscar el campo donde se guarda el código
        const codeField = clients[0].activation_code !== undefined ? 'activation_code' : (clients[0].code !== undefined ? 'code' : null);
        
        if (!codeField) {
             console.log("⚠️ No se encontró la columna de código. Aquí tienes la estructura secreta del primer cliente:");
             console.log(clients[0]);
             return;
        }

        const goodC = clients.find(c => c[codeField] == '253698');
        const badC = clients.find(c => c[codeField] == '901177');

        console.log("➤ CLIENTE SANO [253698] (El que tiene 718 Canales):");
        console.log(goodC || "❌ No encontrado en tabla clients.");

        console.log("\n-------------------------------------------------------");
        console.log("➤ CLIENTE ENFERMO [901177] (El de 3466 Canales):");
        console.log(badC || "❌ No encontrado en tabla clients.");
        console.log("\n=======================================================");
    } catch (e) {
        console.log("❌ Error fatal:", e.message);
    }
}
run();
