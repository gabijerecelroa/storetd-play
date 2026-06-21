const fs = require('fs');

// 1. Extraer credenciales
const envData = fs.readFileSync('.env', 'utf8');
const SUPABASE_URL = envData.match(/SUPABASE_URL=([^ \n]+)/)[1].replace(/['"]/g, '');
let keyMatch = envData.match(/SUPABASE_SERVICE_ROLE_KEY=([^ \n]+)/) || envData.match(/SUPABASE_ANON_KEY=([^ \n]+)/);
const SUPABASE_KEY = keyMatch[1].replace(/['"]/g, '');

const LISTA_PREMIUM = 'https://gist.githubusercontent.com/gabijerecelroa/1beb318f81af17604a81a8c257297615/raw/lista.m3u';

async function vigilar() {
    try {
        const fetchFn = global.fetch;
        
        // Descargar solo los IDs y las URLs para no gastar memoria
        const res = await fetchFn(`${SUPABASE_URL}/rest/v1/clients?select=id,playlist_url`, {
            headers: { 'apikey': SUPABASE_KEY, 'Authorization': `Bearer ${SUPABASE_KEY}` }
        });
        
        if (!res.ok) return;
        const clientes = await res.json();
        
        // Buscar a los "rebeldes" (cualquiera que NO tenga tu lista exacta)
        const rebeldes = clientes.filter(c => c.playlist_url !== LISTA_PREMIUM);

        if (rebeldes.length > 0) {
            console.log(`[${new Date().toLocaleTimeString()}] 🚨 ¡Alerta! Detectados ${rebeldes.length} usuarios con lista incorrecta.`);
            
            // Extraer los IDs de los rebeldes
            const ids = rebeldes.map(c => c.id).join(',');
            
            // Inyectarles la Lista Premium a la fuerza
            await fetchFn(`${SUPABASE_URL}/rest/v1/clients?id=in.(${ids})`, {
                method: 'PATCH',
                headers: { 
                    'apikey': SUPABASE_KEY, 
                    'Authorization': `Bearer ${SUPABASE_KEY}`,
                    'Content-Type': 'application/json',
                    'Prefer': 'return=minimal'
                },
                body: JSON.stringify({ playlist_url: LISTA_PREMIUM })
            });
            console.log(`[${new Date().toLocaleTimeString()}] ✅ Todos corregidos y forzados a la Lista Premium.`);
        }
    } catch(e) {
        // El guardián guarda silencio si hay micro-cortes de internet
    }
}

console.log("\n=======================================================");
console.log(" 🛡️ EL GUARDIÁN DE LISTAS ESTÁ ACTIVO Y VIGILANDO 24/7 🛡️");
console.log("=======================================================\n");

// Ejecutar el primer escaneo de inmediato
vigilar();

// Dejarlo escaneando automáticamente cada 1 Minuto (60000 milisegundos)
setInterval(vigilar, 60000);
