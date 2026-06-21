const fs = require('fs');

console.log("\n=======================================================");
console.log(" 🔞 OPERACIÓN ADULTOS: FRANCOTIRADOR + INTERRUPTOR 🔞");
console.log("=======================================================\n");

// --- PASO 1: MODIFICAR EL FRANCOTIRADOR (playlistContent.js) ---
let pc = fs.readFileSync('src/playlistContent.js', 'utf8');
if (!pc.includes('"adultos", "xxx"')) {
    // Reemplazamos la última palabra para meter las de adultos
    pc = pc.replace('"infantiles premium"', '"infantiles premium", "adultos", "xxx", "hot", "18+", "venus"');
    fs.writeFileSync('src/playlistContent.js', pc);
    console.log("✅ [1/2] Aduana superada: Adultos agregados a la Lista VIP.");
} else {
    console.log("⚠️ [1/2] Las categorías hot ya estaban en la Lista VIP.");
}

// --- PASO 2: CONECTAR EL INTERRUPTOR DE LA APP (server.js) ---
let sv = fs.readFileSync('src/server.js', 'utf8');
if (!sv.includes('req.query.includeAdult')) {
    // 2.1 Importar la función que filtra
    sv = sv.replace("clearCache } = require('./playlistContent');", "clearCache, filterPayloadAdultContent } = require('./playlistContent');");
    
    // 2.2 Inyectar en la ruta /content
    const oldContent = "const content = await getContent({ activationCode: code });";
    const newContent = `const includeAdult = req.query.includeAdult === 'true' || req.query.includeAdult === '1';
        const content = await getContent({ activationCode: code });
        if (content && content.status === 'success') {
            content.data = filterPayloadAdultContent(content.data, includeAdult);
        }`;
    sv = sv.replace(oldContent, newContent);
    
    // 2.3 Inyectar en las rutas Lite (live, movies, series-folders)
    const rutasLite = ['live', 'movies', 'series-folders'];
    rutasLite.forEach(sec => {
        const regex = new RegExp(`const payload = await getLitePayload\\({ activationCode: code, section: '${sec}' }\\);\\s*if \\(payload\\) return res\\.json\\({ status: 'success', data: payload }\\);`, 'g');
        const nuevo = `const includeAdult = req.query.includeAdult === 'true' || req.query.includeAdult === '1';
        const payload = await getLitePayload({ activationCode: code, section: '${sec}' });
        if (payload) {
            const filteredPayload = filterPayloadAdultContent(payload, includeAdult);
            return res.json({ status: 'success', data: filteredPayload });
        }`;
        sv = sv.replace(regex, nuevo);
    });

    fs.writeFileSync('src/server.js', sv);
    console.log("✅ [2/2] Interruptor conectado: El servidor ahora lee el botón de la TV.");
} else {
    console.log("⚠️ [2/2] El interruptor ya estaba conectado en el servidor.");
}

console.log("\n=======================================================\n");
