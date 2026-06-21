import re
with open("src/server.js", "r", encoding="utf-8") as f: code = f.read()

# 1. Extirpamos TODO el bloque tóxico que rompió el login
code = re.sub(r"// 🔥 PROXY TMDB POSTERS 🔥[\s\S]*?// 🔥 FIN PROXY TMDB 🔥\n*", "", code)

# 2. Inyectamos SOLO el buscador de imágenes (Sin interceptar el servidor globalmente)
safe_proxy = """
// 🔥 PROXY TMDB POSTERS (SEGURO) 🔥
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
code = code.replace("const app = express();", "const app = express();\n" + safe_proxy)

with open("src/server.js", "w", encoding="utf-8") as f: f.write(code)
print("\n✅ [SISTEMA RESCATADO] Interceptor eliminado. El Servidor vuelve a respirar.\n")
