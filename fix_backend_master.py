import os, re

print("\n🚀 INSTALANDO BACKEND QUIRÚRGICO 🚀\n")

# 1. MÁQUINA DEL TIEMPO: Dejamos el servidor de fábrica y puro
os.system("git restore backend/src/server.js backend/src/playlistContent.js")

# 2. BISTURÍ: Forzamos .m3u8 SOLO para canales en vivo, salvando las Películas
pc_path = "backend/src/playlistContent.js"
with open(pc_path, "r", encoding="utf-8") as f: pc = f.read()
pc = pc.replace('xtreamLiveUrl(streamId, ext)', 'xtreamLiveUrl(streamId, "m3u8")')
with open(pc_path, "w", encoding="utf-8") as f: f.write(pc)

# 3. TMDB: Preguntamos la llave y la guardamos
env_path = "backend/.env"
try:
    with open(env_path, "r", encoding="utf-8") as f: env_content = f.read()
except: env_content = ""

if "TMDB_API_KEY" not in env_content:
    tmdb_key = input("🔑 Pega tu API KEY de TMDB aquí y presiona Enter:\n> ").strip()
    with open(env_path, "a", encoding="utf-8") as f: f.write(f"\nTMDB_API_KEY={tmdb_key}\n")

# 4. INYECCIÓN SEGURA DEL PROXY TMDB (Sin romper el Login)
sv_path = "backend/src/server.js"
with open(sv_path, "r", encoding="utf-8") as f: sv = f.read()

proxy = """
// 🔥 PROXY TMDB SEGURO 🔥
app.get('/api/tmdb/poster', async (req, res) => {
    try {
        const title = req.query.title;
        const fallback = 'https://via.placeholder.com/300x450/141414/ffffff?text=' + encodeURIComponent(title || 'Sin+Poster');
        if (!title) return res.redirect(fallback);
        const cleanTitle = title.replace(/\\[.*?\\]|\\(.*?\\)|\\b(FHD|HD|SD|4K|1080p|720p|LATINO|LAT|ESP|SUB|TV|VIVO|24\\/7)\\b/gi, '').trim().split('-')[0].trim();
        const tmdbKey = process.env.TMDB_API_KEY;
        if (!tmdbKey) return res.redirect(fallback);
        const url = `https://api.themoviedb.org/3/search/multi?api_key=${tmdbKey}&language=es-MX&query=${encodeURIComponent(cleanTitle)}`;
        let fetchFn = global.fetch || (...args) => import('node-fetch').then(({default: f}) => f(...args));
        const response = await fetchFn(url);
        const data = await response.json();
        if (data.results && data.results.length > 0) {
            const result = data.results.find(r => r.poster_path && r.media_type === 'tv') || data.results.find(r => r.poster_path) || data.results[0];
            if (result.poster_path) return res.redirect("https://image.tmdb.org/t/p/w500" + result.poster_path);
        }
        res.redirect(fallback);
    } catch (e) {
        res.redirect('https://via.placeholder.com/300x450/141414/ffffff?text=Error');
    }
});
// 🔥 FIN PROXY TMDB 🔥
"""
sv = sv.replace("const app = express();", "const app = express();\n" + proxy)
with open(sv_path, "w", encoding="utf-8") as f: f.write(sv)

print("✅ [ÉXITO] TV en Vivo es HLS, Películas Reparadas y TMDB Instalado.\n")
