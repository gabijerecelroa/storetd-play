const fs = require('fs');

console.log("\n=======================================================");
console.log(" ☢️ BOMBA NUCLEAR: DESTRUYENDO FILTROS DE ADULTOS ☢️");
console.log("=======================================================\n");

try {
    let code = fs.readFileSync('src/playlistContent.js', 'utf8');

    // 1. NEUTRALIZAR LOS AGENTES ENCUBIERTOS
    // Hacemos que las funciones devuelvan 'false' automáticamente para que nunca detecten nada.
    if (!code.includes('function isAdult(item) { return false;')) {
        code = code.replace('function isAdult(item) {', 'function isAdult(item) { return false;');
        console.log("✅ Agente 1 (isAdult) -> Neutralizado.");
    }
    
    if (!code.includes('function isAdultLiteEntry(entry) { return false;')) {
        code = code.replace('function isAdultLiteEntry(entry) {', 'function isAdultLiteEntry(entry) { return false;');
        console.log("✅ Agente 2 (isAdultLiteEntry) -> Neutralizado.");
    }

    // 2. REPARAR LA ADUANA (EL FRANCOTIRADOR V6)
    // Nos aseguramos de que las palabras candentes estén escritas correctamente.
    if (!code.includes('"adultos", "hot"')) {
        code = code.replace('"infantiles premium"', '"infantiles premium", "adultos", "hot", "xxx", "18+", "venus"');
        console.log("✅ Aduana VIP -> Puertas abiertas para canales candentes.");
    } else {
        console.log("✅ Aduana VIP -> Ya estaba configurada correctamente.");
    }

    // Guardamos los cambios estructurales
    fs.writeFileSync('src/playlistContent.js', code);
    console.log("\n🏆 [ÉXITO] El servidor ahora es 100% ciego. Todo el contenido pasará libremente.");

} catch(e) {
    console.log("❌ Error fatal:", e.message);
}
console.log("\n=======================================================\n");
