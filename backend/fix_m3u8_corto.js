const fs = require('fs'), path = require('path');
function buscar(dir) {
  if (!fs.existsSync(dir)) return;
  fs.readdirSync(dir).forEach(f => {
    let p = path.join(dir, f);
    if (fs.statSync(p).isDirectory() && !p.includes('node_modules') && !p.includes('.git')) buscar(p);
    else if (f.includes('xtream.controller')) {
      let c = fs.readFileSync(p, 'utf8'), original = c;
      c = c.replace(/\$\{stream\.stream_id\}\.ts`/g, '${stream.stream_id}.m3u8`');
      c = c.replace(/\$\{stream\.stream_id\}`/g, '${stream.stream_id}.m3u8`');
      if (c !== original) { fs.writeFileSync(p, c); console.log('✅ M3U8 ACTIVADO EN: ' + p); }
    }
  });
}
console.log("🔎 Soltando al Sabueso en tu servidor...");
buscar('.');
