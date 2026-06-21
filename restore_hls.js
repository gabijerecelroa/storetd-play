const fs = require('fs');
const files = ['backend/src/server.js', 'backend/src/playlistContent.js'];
files.forEach(file => {
    if (fs.existsSync(file)) {
        let code = fs.readFileSync(file, 'utf8');
        code = code.replace(/ext\s*=\s*['"]ts['"]/g, 'ext = "m3u8"');
        code = code.replace(/ext\s*\|\|\s*['"]ts['"]/g, 'ext || "m3u8"');
        code = code.replace(/\.ts([`'"])/g, '.m3u8$1');
        fs.writeFileSync(file, code);
    }
});
