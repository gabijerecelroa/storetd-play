import re, os

tmdb_key = input("\n🔑 Pega tu API KEY de TMDB aquí y presiona Enter:\n> ").strip()

env_path = ".env"
try:
    with open(env_path, "r", encoding="utf-8") as f: env_content = f.read()
except:
    env_content = ""

if "TMDB_API_KEY" not in env_content:
    with open(env_path, "a", encoding="utf-8") as f: f.write(f"\nTMDB_API_KEY={tmdb_key}\n")
else:
    env_content = re.sub(r"TMDB_API_KEY=.*", f"TMDB_API_KEY={tmdb_key}", env_content)
with open(env_path, "w", encoding="utf-8") as f: f.write(env_content)

server_path = "src/server.js"
with open(server_path, "r", encoding="utf-8") as f: code = f.read()

proxy = """
// 🔥 PROXY TMDB POSTERS 🔥
app.get('/api/tmdb/poster', async (req, res) => {
    try {
        const title = req.query.title;
        const fallback = 'https://via.placeholder.com/300x450/1a1a1a/ffffff?text=' + encodeURIComponent(title || 'Sin+Poster');
        if (!title) return res.redirect(fallback);
        
        const cleanTitle = title.replace(/\\[.*?\\]|\\(.*?\\)|\\b(FHD|HD|SD|4K|1080p|720p|LATINO|LAT|ESP|SUB)\\b/gi, '').trim().split('-')[0].trim();
        const tmdbKey = process.env.TMDB_API_KEY;
        if (!tmdbKey) return res.redirect(fallback);
        
        const url = `https://api.themoviedb.org/3/search/multi?api_key=${tmdbKey}&language=es-MX&query=${encodeURIComponent(cleanTitle)}`;
        let fetchFn = global.fetch || (...args) => import('node-fetch').then(({default: f}) => f(...args));
        const response = await fetchFn(url);
        const data = await response.json();
        
        if (data.results && data.results.length > 0) {
            const result = data.results.find(r => r.poster_path) || data.results[0];
            if (result.poster_path) return res.redirect("https://image.tmdb.org/t/p/w500" + result.poster_path);
        }
        res.redirect(fallback);
    } catch (e) {
        res.redirect('https://via.placeholder.com/300x450/1a1a1a/ffffff?text=Error');
    }
});

app.use((req, res, next) => {
    const originalJson = res.json;
    res.json = function(body) {
        if (body && (Array.isArray(body.channels) || Array.isArray(body.folders))) {
            const items = body.channels || body.folders;
            const publicBase = req.protocol + '://' + req.get('host');
            items.forEach(ch => {
                if (!ch.logoUrl || ch.logoUrl === "-" || ch.logoUrl.trim() === "" || ch.logoUrl.includes('default')) {
                    ch.logoUrl = `${publicBase}/api/tmdb/poster?title=${encodeURIComponent(ch.name || ch.title || "")}`;
                    if (ch.posterUrl !== undefined) ch.posterUrl = ch.logoUrl;
                }
            });
        }
        return originalJson.call(this, body);
    };
    next();
});
// 🔥 FIN PROXY TMDB 🔥
"""

if "PROXY TMDB POSTERS" not in code:
    code = code.replace("const app = express();", "const app = express();\n" + proxy)
    with open(server_path, "w", encoding="utf-8") as f: f.write(code)
    print("✅ Proxy TMDB instalado en el backend con éxito.")
else:
    print("⚠️ El Proxy TMDB ya estaba instalado.")
