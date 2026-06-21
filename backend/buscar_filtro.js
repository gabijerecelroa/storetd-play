const fs = require('fs');

try {
    const code = fs.readFileSync('src/playlistContent.js', 'utf8');
    const lines = code.split('\n');
    
    console.log("\n=======================================================");
    console.log(" 🕵️‍♂️ ESCÁNER DE FILTROS: BUSCANDO EL BLOQUEO 🕵️‍♂️");
    console.log("=======================================================\n");
    
    let encontrados = 0;
    lines.forEach((line, i) => {
        const str = line.toLowerCase();
        if (str.includes('filter') || str.includes('adult') || str.includes('xxx') || str.includes('categor')) {
            console.log(`[Línea ${i+1}] -> ${line.trim()}`);
            encontrados++;
        }
    });
    
    if (encontrados === 0) {
        console.log("➤ No se encontraron filtros evidentes en playlistContent.js.");
        console.log("➤ Es posible que el filtro esté en la Base de Datos o que la lista M3U no tenga la categoría.");
    }
    
    console.log("\n=======================================================\n");
} catch (e) {
    console.log("❌ Error leyendo el archivo:", e.message);
}
