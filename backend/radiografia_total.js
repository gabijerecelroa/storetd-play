const fs = require('fs');

async function escanear() {
    console.log("\n=======================================================");
    console.log(" 🩻 RADIOGRAFÍA TOTAL: ANALIZANDO EL ENLACE GITHUB 🩻");
    console.log("=======================================================\n");

    // Este es el enlace exacto que le inyectaste a Supabase a todos tus clientes
    const urlList = "https://gist.githubusercontent.com/gabijerecelroa/1beb318f81af17604a81a8c257297615/raw/lista.m3u";
    console.log(`➤ Descargando lista base: ${urlList}\n`);
    
    try {
        let fetchFn = global.fetch || (...args) => import('node-fetch').then(({default: f}) => f(...args));
        const res = await fetchFn(urlList);
        const text = await res.text();
        
        const lines = text.split('\n');
        let adultosCount = 0;
        let categorias = new Set();
        
        lines.forEach(line => {
            if (line.includes('group-title="')) {
                const match = line.match(/group-title="([^"]+)"/);
                if (match) {
                    categorias.add(match[1]);
                    const catName = match[1].toLowerCase();
                    if (catName.includes('adult') || catName.includes('xxx') || catName.includes('+18') || catName.includes('hot')) {
                        adultosCount++;
                    }
                }
            }
        });
        
        console.log("📺 CATEGORÍAS REALES ENCONTRADAS DENTRO DE TU GIST:");
        Array.from(categorias).forEach((c, i) => console.log(`   ${i+1}. ${c}`));
        
        console.log(`\n🔥 Canales candentes detectados en el texto: ${adultosCount}`);
        
        if (adultosCount === 0) {
            console.log("\n❌ DIAGNÓSTICO FATAL: ¡La lista M3U de tu GitHub NO TIENE canales de adultos!");
            console.log("   👉 El servidor es inocente. Los canales simplemente no existen en tu archivo curado.");
        } else {
            console.log("\n✅ DIAGNÓSTICO: Los canales SÍ están en el Gist. El problema sigue estando en el servidor.");
        }
        
    } catch(e) {
        console.log("❌ Error descargando la lista:", e.message);
    }
    console.log("\n=======================================================\n");
}
escanear();
