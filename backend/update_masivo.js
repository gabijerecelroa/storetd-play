const fs = require('fs');

// 1. Extraer credenciales de forma segura
const envData = fs.readFileSync('.env', 'utf8');
const SUPABASE_URL = envData.match(/SUPABASE_URL=([^ \n]+)/)[1].replace(/['"]/g, '');
let keyMatch = envData.match(/SUPABASE_SERVICE_ROLE_KEY=([^ \n]+)/) || envData.match(/SUPABASE_ANON_KEY=([^ \n]+)/);
const SUPABASE_KEY = keyMatch[1].replace(/['"]/g, '');

const LISTA_PREMIUM = 'http://tv.m3uts.xyz/player_api.php?username=m&password=m';

async function run() {
    console.log("\n=======================================================");
    console.log(" 🚀 INICIANDO ACTUALIZACIÓN MASIVA DE CLIENTES 🚀");
    console.log("=======================================================\n");

    try {
        const fetchFn = global.fetch;
        console.log("➤ Inyectando la Lista Premium a toda la base de datos...");
        
        // 3. Hack: Actualizar TODOS los clientes con ID mayor a 0 (O sea, todos)
        const res = await fetchFn(`${SUPABASE_URL}/rest/v1/clients?id=gt.0`, {
            method: 'PATCH',
            headers: { 
                'apikey': SUPABASE_KEY, 
                'Authorization': `Bearer ${SUPABASE_KEY}`,
                'Content-Type': 'application/json',
                'Prefer': 'return=representation'
            },
            body: JSON.stringify({ playlist_url: LISTA_PREMIUM })
        });

        if(res.ok) {
            const data = await res.json();
            console.log(`✅ ¡OPERACIÓN EXITOSA! Se actualizaron ${data.length} clientes al instante.`);
            console.log(`   Nueva Lista Asignada: ${LISTA_PREMIUM}`);
        } else {
            const err = await res.text();
            console.log(`❌ Error del servidor: ${res.status} - ${err}`);
        }
    } catch(e) {
        console.log("❌ Error fatal:", e.message);
    }
    console.log("\n=======================================================\n");
}
run();
