import re, os

print("\n🚀 INICIANDO EL PROYECTO NETFLIX (MODO SEGURO) 🚀\n")

# ==========================================
# 1. BACKEND: RUTA PASIVA DE TMDB (100% SEGURA)
# ==========================================
tmdb_key = input("🔑 Pega tu API KEY de TMDB aquí y presiona Enter:\n> ").strip()

env_path = "backend/.env"
try:
    with open(env_path, "r", encoding="utf-8") as f: env_content = f.read()
except:
    env_content = ""

if "TMDB_API_KEY" not in env_content:
    with open(env_path, "a", encoding="utf-8") as f: f.write(f"\nTMDB_API_KEY={tmdb_key}\n")
else:
    env_content = re.sub(r"TMDB_API_KEY=.*", f"TMDB_API_KEY={tmdb_key}", env_content)
with open(env_path, "w", encoding="utf-8") as f: f.write(env_content)

server_path = "backend/src/server.js"
if os.path.exists(server_path):
    with open(server_path, "r", encoding="utf-8") as f: code = f.read()

    proxy = """
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
    if "PROXY TMDB POSTERS (SEGURO)" not in code:
        code = code.replace("const app = express();", "const app = express();\n" + proxy)
        with open(server_path, "w", encoding="utf-8") as f: f.write(code)
        print("✅ Ruta TMDB instalada sin interceptores.")

# ==========================================
# 2. ANDROID: SHUFFLE Y DESBLOQUEO DE SERIES
# ==========================================
hs_path = "android/app/src/main/java/com/storetd/play/feature/home/HomeScreen.kt"
with open(hs_path, "r", encoding="utf-8") as f: hs = f.read()

# Barajamos películas y quitamos el filtro que oculta series sin foto
hs = re.sub(r"(peliculasVistas\s*=\s*OptimizedContentApi.*?\.filter\s*\{[^}]*\}\s*)\.take\(", r"\1.shuffled().take(", hs)
hs = re.sub(r"(estrenos\s*=\s*OptimizedContentApi.*?\.filter\s*\{[^}]*\}\s*)\.take\(", r"\1.shuffled().take(", hs)
hs = re.sub(r"seriesDestacadas\s*=\s*sCats\.filter\s*\{[^}]*\}\.take\(\d+\)", r"seriesDestacadas = sCats.shuffled().take(20)", hs)
hs = hs.replace(".shuffled().shuffled()", ".shuffled()")

# ==========================================
# 3. ANDROID: DISEÑO DE TARJETAS NETFLIX
# ==========================================
def find_closing_brace(text, start_index):
    count = 0
    for i in range(start_index, len(text)):
        if text[i] == '{': count += 1
        elif text[i] == '}':
            count -= 1
            if count == 0: return i
    return -1

def replace_composable(text, func_name, new_code):
    pattern = r"@Composable\s*fun " + func_name + r"\b[^{]*\{"
    match = re.search(pattern, text)
    if match:
        start = match.start()
        comp_idx = text.rfind("@Composable", 0, start)
        if comp_idx != -1 and start - comp_idx < 30: start = comp_idx
        brace_start = match.end() - 1
        end = find_closing_brace(text, brace_start)
        if end != -1: return text[:start] + new_code + text[end+1:]
    return text

movie_card = """@Composable
fun MovieCard(name: String, logoUrl: String?, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "scale")

    val finalLogo = if (logoUrl.isNullOrBlank() || logoUrl == "-" || logoUrl.lowercase().contains("default")) {
        "http://82.39.109.213:5000/api/tmdb/poster?title=${android.net.Uri.encode(name)}"
    } else {
        logoUrl
    }

    Box(
        modifier = Modifier
            .width(140.dp)
            .height(210.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (isFocused) 1f else 0f)
            .clip(RoundedCornerShape(10.dp))
            .border(3.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .background(Color(0xFF18181B))
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .clickable { onClick() }
    ) {
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(finalLogo)
                .crossfade(true)
                .build(),
            contentDescription = name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Degradado y Texto Netflix
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f), Color.Black)
                    )
                )
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Text(
                text = name ?: "",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 14.sp
            )
        }
    }
}"""

landscape_card = """@Composable
fun LandscapeCard(name: String, logoUrl: String?, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "scale")

    val finalLogo = if (logoUrl.isNullOrBlank() || logoUrl == "-" || logoUrl.lowercase().contains("default")) {
        "http://82.39.109.213:5000/api/tmdb/poster?title=${android.net.Uri.encode(name)}"
    } else {
        logoUrl
    }

    Box(
        modifier = Modifier
            .width(220.dp)
            .height(124.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (isFocused) 1f else 0f)
            .clip(RoundedCornerShape(10.dp))
            .border(3.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .background(Color(0xFF18181B))
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .clickable { onClick() }
    ) {
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(finalLogo)
                .crossfade(true)
                .build(),
            contentDescription = name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Degradado y Texto Netflix
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f), Color.Black)
                    )
                )
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Text(
                text = name ?: "",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}"""

hs = replace_composable(hs, "MovieCard", movie_card)
hs = replace_composable(hs, "LandscapeCard", landscape_card)
with open(hs_path, "w", encoding="utf-8") as f: f.write(hs)

# ==========================================
# 4. ACTUALIZAR VERSIÓN A 1.6.83 (Code 118)
# ==========================================
gradle_path = "android/app/build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as f: gradle = f.read()
gradle = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 118", gradle)
gradle = re.sub(r'versionName\s*=\s*".*?"', 'versionName = "1.6.83"', gradle)
with open(gradle_path, "w", encoding="utf-8") as f: f.write(gradle)

print("\n✅ [PROYECTO NETFLIX SEGURO] Inyectado con éxito en Android. Listo para compilar.\n")
