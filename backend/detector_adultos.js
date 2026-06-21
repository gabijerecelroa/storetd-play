const pc = require('./src/playlistContent');

async function investigar() {
    console.log("\n=======================================================");
    console.log(" 🕵️‍♂️ DETECTOR DE MENTIRAS: CATEGORÍAS REALES 🕵️‍♂️");
    console.log("=======================================================\n");

    try {
        // Usamos a tu usuario premium para la prueba
        const code = '253698'; 
        
        console.log("➤ Simulando que la TV pide la lista con el botón de Adultos ENCENDIDO...\n");
        
        // Obtenemos la lista cruda del servidor
        const payload = await pc.getLitePayload({ activationCode: code, section: 'live' });
        
        if (!payload || !payload.categories) {
            console.log("❌ Error: No se pudo obtener la lista de Xtream.");
            return;
        }

        // Aplicamos el filtro dejando pasar adultos (true)
        const listaFinal = pc.filterPayloadAdultContent(payload, true);
        
        // Extraemos solo los nombres de las categorías
        const nombresCategorias = listaFinal.categories.map(c => c.title || c.group);
        
        console.log("📺 CATEGORÍAS QUE EL SERVIDOR ESTÁ DEJANDO PASAR:");
        nombresCategorias.forEach((cat, i) => console.log(`   ${i+1}. ${cat}`));
        
        console.log("\n-------------------------------------------------------");
        const tieneAdultos = nombresCategorias.some(c => c.toLowerCase().includes('adult'));
        
        if (tieneAdultos) {
            console.log("✅ DIAGNÓSTICO: ¡El servidor ESTÁ PERFECTO! El Francotirador los dejó pasar.");
            console.log("   👉 CULPABLE: Tu aplicación de Android no le está avisando al servidor cuando enciendes el botón.");
        } else {
            console.log("❌ DIAGNÓSTICO: ¡El servidor está matando la categoría!");
            console.log("   👉 CULPABLE: El Francotirador (Lista VIP) no tiene la palabra 'adultos' permitida.");
        }
    } catch (e) {
        console.log("❌ Error fatal en el detector:", e.message);
    }
    console.log("\n=======================================================\n");
}

investigar();
