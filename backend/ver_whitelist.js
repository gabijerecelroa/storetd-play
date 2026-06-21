const fs = require('fs');

try {
    const code = fs.readFileSync('src/playlistContent.js', 'utf8').split('\n');
    console.log("\n=======================================================");
    console.log(" 📋 REVISANDO LA LISTA VIP DE CATEGORÍAS 📋");
    console.log("=======================================================\n");
    
    // Extraemos el código exacto donde está el filtro VIP (Líneas 1145 a 1170)
    for (let i = 1145; i <= 1170; i++) {
        if(code[i] !== undefined) {
            console.log(`[Línea ${i+1}] ${code[i].trim()}`);
        }
    }
    console.log("\n=======================================================\n");
} catch (e) {
    console.log("❌ Error:", e.message);
}
