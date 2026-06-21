require('dotenv').config();
const { createClient } = require('@supabase/supabase-js');

const url = process.env.SUPABASE_URL;
const key = process.env.SUPABASE_SERVICE_ROLE_KEY;

const supabase = createClient(url, key);

async function nukeCache() {
    console.log("\n💣 Conectando a Supabase...");
    console.log("🧨 Dinamitando la tabla 'playlist_cache' completa...");
    
    // .neq borra todo lo que NO coincida. Es el truco maestro para borrar la tabla entera de golpe.
    const { error } = await supabase.from('playlist_cache').delete().neq('section', 'BOMBA_NUCLEAR');
    
    if (error) {
        console.error("❌ Falla en la detonación:", error.message);
    } else {
        console.log("✅ [ÉXITO] ¡SUPABASE HA SIDO VACIADO!");
        console.log("✅ El servidor ahora está obligado a usar la Aduana VIP y a arreglar los pósters.\n");
    }
    process.exit(0);
}
nukeCache();
