const fs = require('fs');

const envData = fs.readFileSync('.env', 'utf8');
const SUPABASE_URL = envData.match(/SUPABASE_URL=([^ \n]+)/)[1].replace(/['"]/g, '');
let keyMatch = envData.match(/SUPABASE_SERVICE_ROLE_KEY=([^ \n]+)/) || envData.match(/SUPABASE_ANON_KEY=([^ \n]+)/);
const SUPABASE_KEY = keyMatch[1].replace(/['"]/g, '');

async function onlineNow() {
    try {
        const fetchFn = global.fetch;
        const resDev = await fetchFn(`${SUPABASE_URL}/rest/v1/devices?select=*`, {
            headers: { 'apikey': SUPABASE_KEY, 'Authorization': `Bearer ${SUPABASE_KEY}` }
        });
        const resCli = await fetchFn(`${SUPABASE_URL}/rest/v1/clients?select=id,customer_name`, {
            headers: { 'apikey': SUPABASE_KEY, 'Authorization': `Bearer ${SUPABASE_KEY}` }
        });

        const devices = await resDev.json();
        const clients = await resCli.json();

        // Calculamos 15 minutos hacia atrás
        const quinceMinutosAtras = new Date(Date.now() - 15 * 60 * 1000).toISOString();
        
        // Filtramos los que dieron señales de vida recientemente
        const online = devices.filter(d => d.last_seen_at && d.last_seen_at >= quinceMinutosAtras);

        // Ordenamos del más reciente al más antiguo
        online.sort((a, b) => new Date(b.last_seen_at) - new Date(a.last_seen_at));

        console.log("\n=======================================================");
        console.log(` 🟢 USUARIOS CONECTADOS AHORA MISMO (Últimos 15 min) 🟢`);
        console.log("=======================================================\n");

        if (online.length === 0) {
            console.log("➤ La red está en silencio. No hay clientes activos en este exacto momento.");
        } else {
            console.log(`➤ Total Online Ahora: ${online.length} televisores/celulares activos\n`);
            online.forEach((d, i) => {
                const client = clients.find(c => c.id == d.client_id || c.uuid == d.client_id) || {};
                const name = client.customer_name || 'Desconocido';
                
                // Formateamos la hora a la zona horaria de Argentina
                const lastSeen = new Date(d.last_seen_at).toLocaleTimeString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' });
                
                // Detectar el nombre del campo del código
                const code = d.activation_code || d.activationCode || d.code || 'N/A';
                
                console.log(`  ${(i+1).toString().padStart(2, ' ')}. [ Última acción: ${lastSeen} ] 📺 ${name.padEnd(15)} | Código: ${code}`);
            });
        }
        console.log("\n=======================================================\n");
    } catch (e) {
        console.log("❌ Error en el radar táctico:", e.message);
    }
}
onlineNow();
