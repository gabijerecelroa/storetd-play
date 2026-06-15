require('dotenv').config();
const { createClient } = require('@supabase/supabase-js');

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

async function buscarFantasma() {
    console.log("🔍 Conectando a Supabase y buscando el servidor viejo...");
    const posiblesTablas = ['settings', 'config', 'app_settings', 'panel_settings', 'system_config', 'providers'];
    
    for (let tabla of posiblesTablas) {
        let { data, error } = await supabase.from(tabla).select('*').limit(5);
        if (!error && data && data.length > 0) {
            console.log(`\n✅ ¡Tabla encontrada!: [${tabla}]`);
            console.log(JSON.stringify(data, null, 2));
        }
    }
    console.log("\n🛑 Fin del escaneo.");
}

buscarFantasma();
