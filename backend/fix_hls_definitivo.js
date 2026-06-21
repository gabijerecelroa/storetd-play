const fs = require('fs');

const files = ['src/server.js', 'src/playlistContent.js'];

files.forEach(file => {
    if (!fs.existsSync(file)) return;
    let code = fs.readFileSync(file, 'utf8');
    let original = code;

    // 1. Eliminar cualquier rastro de .ts por defecto y forzar .m3u8 (HLS)
    code = code.replace(/ext\s*=\s*['"]ts['"]/g, 'ext = "m3u8"');
    code = code.replace(/ext\s*\|\|\s*['"]ts['"]/g, 'ext || "m3u8"');
    code = code.replace(/\.ts([`'"])/g, '.m3u8$1');

    // 2. Inyectar el Rádar interceptor en el motor principal (server.js)
    if (file.includes('server.js') && !code.includes('RADAR DE REPRODUCCION EN VIVO')) {
        const radar = `
// 🔥 RADAR DE REPRODUCCION EN VIVO 🔥
app.use((req, res, next) => {
    if (req.url.includes('/live/') || req.url.includes('/magma-lite/live') || req.url.includes('/xtream-lite/live')) {
        console.log('\\n======================================================');
        console.log(' 📺 [PLAY DETECTADO] Tu App solicitó iniciar un canal:');
        console.log(' 👉 Ruta: ' + req.url.split('?')[0]);
        console.log(' ✅ Modo HLS Blindado (.m3u8) Activado con Éxito.');
        console.log('======================================================\\n');
    }
    next();
});
`;
        code = code.replace(/(const app = express\(\);)/, `$1\n${radar}`);
    }

    if (code !== original) {
        fs.writeFileSync(file, code);
        console.log('✅ [CIRUGÍA ÉXITOSA] HLS y Rádar inyectados en: ' + file);
    } else {
        console.log('⚠️ [AVISO] Sin cambios en: ' + file + ' (ya estaba preparado)');
    }
});
