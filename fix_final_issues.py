import os
import re

# 1. PLAYLISTCONTENT.JS
path_playlist = "backend/src/playlistContent.js"
with open(path_playlist, "r", encoding="utf-8") as f:
    content = f.read()

# Add fixImageUrl
if "function fixImageUrl" not in content:
    content = content.replace("function normalizeText", "function fixImageUrl(url) {\n  if (!url) return null;\n  const str = String(url).trim();\n  if (!str) return null;\n  if (str.startsWith('/')) {\n    return 'https://image.tmdb.org/t/p/w500' + str;\n  }\n  return str;\n}\n\nfunction normalizeText")

# Update xtreamCategoryMap
old_cat_map = """function xtreamCategoryMap(categories) {
  const map = new Map();

  (Array.isArray(categories) ? categories : []).forEach((cat) => {
    const id = String(cat.category_id || "").trim();
    const name = String(cat.category_name || "").trim();"""
new_cat_map = """function xtreamCategoryMap(categories) {
  const map = new Map();

  (Array.isArray(categories) ? categories : []).forEach((cat) => {
    const id = String(cat.category_id || cat.id || "").trim();
    const name = String(cat.category_name || cat.name || cat.title || "").trim();"""
content = content.replace(old_cat_map, new_cat_map)

# Replace poster assignments and category fallback
content = re.sub(
    r'const categoryIdStr = xtreamString\(row, "category_id"\);',
    'const categoryIdStr = xtreamString(row, "category_id", "category_ids", "id");',
    content
)
content = re.sub(
    r'xtreamCategoryName\(categoryMap, categoryId, row\.category_name \|\| "Sin Categoria"\);',
    'xtreamCategoryName(categoryMap, categoryId, row.category_name || row.name || row.title || "Sin Categoria");',
    content
)

# Apply fixImageUrl everywhere we get logos
content = re.sub(
    r'xtreamString\(row, "stream_icon", "cover", "image"\)',
    'fixImageUrl(xtreamString(row, "stream_icon", "cover", "image"))',
    content
)
content = re.sub(
    r'xtreamString\(row, "stream_icon", "cover", "movie_image", "poster", "icon", "image"\)',
    'fixImageUrl(xtreamString(row, "stream_icon", "cover", "movie_image", "poster", "icon", "image"))',
    content
)
content = re.sub(
    r'xtreamString\(row, "cover", "stream_icon", "movie_image", "poster", "icon", "image"\)',
    'fixImageUrl(xtreamString(row, "cover", "stream_icon", "movie_image", "poster", "icon", "image"))',
    content
)
content = re.sub(
    r'xtreamString\(row, "backdrop_path", "cover_big"\)',
    'fixImageUrl(xtreamString(row, "backdrop_path", "cover_big"))',
    content
)

with open(path_playlist, "w", encoding="utf-8") as f:
    f.write(content)

print("playlistContent.js patched")

# 2. SERVER.JS
path_server = "backend/src/server.js"
with open(path_server, "r", encoding="utf-8") as f:
    content = f.read()

xtream_api_endpoint = """
app.get("/api/xtream/movie-sources", async (req, res) => {
  try {
    const activationCode = String(req.query.code || "").trim();
    const streamId = String(req.query.id || "").trim();
    const kind = String(req.query.kind || "").trim().toLowerCase();
    
    if (!activationCode || !streamId) return res.status(400).send("Faltan parámetros");

    const { getClientByActivationCode, fetchXtreamJson, xtreamConfig } = require("./playlistContent");
    const client = await getClientByActivationCode(activationCode);
    
    if (client && client.playlist_url) {
        const playlistUrl = client.playlist_url;
        let linksData;
        
        try {
            if (kind === "episode") {
                const seriesId = req.query.seriesId;
                const season = req.query.season;
                const episode = req.query.episode;
                const params = { episode_id: streamId };
                if (seriesId) params.serie = seriesId;
                if (season) params.season = season;
                if (episode) params.episode = episode;
                linksData = await fetchXtreamJson(playlistUrl, "get_episode_links", params);
            } else {
                linksData = await fetchXtreamJson(playlistUrl, "get_vod_links", { vod_id: streamId });
            }
            
            let items = [];
            if (typeof linksData === 'string' && linksData.startsWith("http")) {
                items.push({ id: "1", title: "Servidor Principal", streamUrl: linksData, quality: "Auto" });
            } else if (linksData?.links) {
                const links = Array.isArray(linksData.links) ? linksData.links : [linksData.links];
                links.forEach((url, i) => {
                    items.push({ id: String(i+1), title: `Servidor ${i+1}`, streamUrl: url, quality: "Auto" });
                });
            } else if (linksData?.url) {
                items.push({ id: "1", title: "Servidor Principal", streamUrl: linksData.url, quality: "Auto" });
            } else if (typeof linksData === 'object' && Object.keys(linksData).length > 0) {
                let idx = 1;
                for (const key of Object.keys(linksData)) {
                    const val = linksData[key];
                    if (typeof val === 'string' && val.startsWith('http')) {
                        items.push({ id: String(idx), title: `Servidor ${idx}`, streamUrl: val, quality: key });
                    } else if (val?.url) {
                        items.push({ id: String(idx), title: `Servidor ${idx}`, streamUrl: val.url, quality: key });
                    }
                    idx++;
                }
            }
            
            if (items.length === 0) {
                const { baseUrl, username, password } = xtreamConfig(playlistUrl);
                const fallbackUrl = kind === "episode" ? 
                    baseUrl + "/series/" + username + "/" + password + "/" + streamId + "." + (req.query.ext || "mp4") :
                    baseUrl + "/movie/" + username + "/" + password + "/" + streamId + "." + (req.query.ext || "mp4");
                
                items.push({ id: "1", title: "Servidor Directo", streamUrl: fallbackUrl, quality: "Auto" });
            }
            
            return res.json({ success: true, items });
        } catch (e) {
            console.error("Error obteniendo movie-sources de Xtream:", e.message);
            return res.json({ success: false, message: "No se pudieron cargar las fuentes." });
        }
    }
    return res.status(404).json({ success: false, message: "Cliente no encontrado" });
  } catch (err) {
    res.status(500).json({ success: false, message: "Error interno" });
  }
});
"""
if "/api/xtream/movie-sources" not in content:
    content = content.replace("app.get(\"/api/xtream/play/movie/:streamId\", async (req, res) => {", xtream_api_endpoint + "\napp.get(\"/api/xtream/play/movie/:streamId\", async (req, res) => {")

with open(path_server, "w", encoding="utf-8") as f:
    f.write(content)

print("server.js patched")

# 3. StoreTdPlayNavHost.kt
path_nav = "android/app/src/main/java/com/storetd/play/navigation/StoreTdPlayNavHost.kt"
with open(path_nav, "r", encoding="utf-8") as f:
    nav_content = f.read()

# Fix isMagmaVodEpisode so it goes to VodDetailScreen instead of direct play
nav_content = re.sub(
    r'val isMagmaVodEpisode = saved\.streamUrl\.contains\(\s*"/magma-lite/movie/",\s*ignoreCase = true\s*\)',
    'val isMagmaVodEpisode = saved.streamUrl.contains("/magma-lite/movie/", ignoreCase = true) || saved.streamUrl.contains("/api/xtream/play/", ignoreCase = true)',
    nav_content
)

with open(path_nav, "w", encoding="utf-8") as f:
    f.write(nav_content)

print("StoreTdPlayNavHost.kt patched")

# 4. OptimizedContentApi.kt
path_api = "android/app/src/main/java/com/storetd/play/core/network/OptimizedContentApi.kt"
with open(path_api, "r", encoding="utf-8") as f:
    api_content = f.read()

old_fun = """fun loadMagmaMovieSources(
        activationCode: String,
        streamId: String,
        kind: String = "",
        seriesId: String = "",
        season: String = "",
        episode: String = ""
    ): List<MovieSourceLite> {"""

new_fun = """fun loadMagmaMovieSources(
        activationCode: String,
        streamId: String,
        kind: String = "",
        seriesId: String = "",
        season: String = "",
        episode: String = "",
        streamUrl: String = ""
    ): List<MovieSourceLite> {"""
api_content = api_content.replace(old_fun, new_fun)

old_url = 'val requestUrl = "$base/api/magma-lite/movie-sources?$params&fresh=1&t=${System.currentTimeMillis()}"'
new_url = """val requestUrl = if (streamUrl.contains("/api/xtream/play/", ignoreCase = true)) {
            "$base/api/xtream/movie-sources?$params&fresh=1&t=${System.currentTimeMillis()}"
        } else {
            "$base/api/magma-lite/movie-sources?$params&fresh=1&t=${System.currentTimeMillis()}"
        }"""
api_content = api_content.replace(old_url, new_url)

with open(path_api, "w", encoding="utf-8") as f:
    f.write(api_content)

print("OptimizedContentApi.kt patched")

# 5. VodDetailScreen.kt
path_vod = "android/app/src/main/java/com/storetd/play/feature/vod/VodDetailScreen.kt"
with open(path_vod, "r", encoding="utf-8") as f:
    vod_content = f.read()

vod_content = re.sub(
    r'private fun isMagmaMovieUrl\(url: String\): Boolean \{\s*return url\.contains\("/magma-lite/movie/", ignoreCase = true\)\s*\}',
    'private fun isMagmaMovieUrl(url: String): Boolean { return url.contains("/magma-lite/movie/", ignoreCase = true) || url.contains("/api/xtream/play/", ignoreCase = true) }',
    vod_content
)

vod_content = re.sub(
    r'private fun extractMagmaMovieStreamId\(url: String\): String\? \{[\s\S]*?\}',
    '''private fun extractMagmaMovieStreamId(url: String): String? {
    val magma = Regex("/magma-lite/movie/([0-9]+)\\\\.m3u8", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
    if (magma != null) return magma
    val xtreamMovie = Regex("/api/xtream/play/movie/([0-9]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
    if (xtreamMovie != null) return xtreamMovie
    return Regex("/api/xtream/play/series/([0-9]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
}''',
    vod_content
)

# Restore dialog logic and uncomment loadMagmaMovieSources
old_play = """        // MAGMA_FRESH_SOURCE_SELECTOR_START
        isLoadingSources = true
        showSourceDialog = false // OCULTAMOS EL MENÚ PARA HACERLO SILENCIOSO

        scope.launch {
            val account = LocalAccount.getAccount(context)
            val activationCode = account.activationCode.trim()

            val loadedSources = withContext(Dispatchers.IO) {
                emptyList<Nothing>() /* OptimizedContentApi.loadMagmaMovieSources(
                    activationCode = activationCode,
                    streamId = streamId,
                    kind = extractUrlQueryParam(streamUrl, "kind"),
                    seriesId = extractUrlQueryParam(streamUrl, "seriesId"),
                    season = extractUrlQueryParam(streamUrl, "season"),
                    episode = extractUrlQueryParam(streamUrl, "episode")
                ) */
            }

            if (true) {
                // 🔥 EL TOQUE MAESTRO: AUTO-PLAY INMEDIATO
                onPlay(streamUrl)
            } else {"""

new_play = """        // MAGMA_FRESH_SOURCE_SELECTOR_START
        isLoadingSources = true
        showSourceDialog = false

        scope.launch {
            val account = LocalAccount.getAccount(context)
            val activationCode = account.activationCode.trim()

            val loadedSources = withContext(Dispatchers.IO) {
                OptimizedContentApi.loadMagmaMovieSources(
                    activationCode = activationCode,
                    streamId = streamId,
                    kind = extractUrlQueryParam(streamUrl, "kind"),
                    seriesId = extractUrlQueryParam(streamUrl, "seriesId"),
                    season = extractUrlQueryParam(streamUrl, "season"),
                    episode = extractUrlQueryParam(streamUrl, "episode"),
                    streamUrl = streamUrl
                )
            }

            movieSources = loadedSources
            if (loadedSources.isEmpty()) {"""

vod_content = vod_content.replace(old_play, new_play)

dialog_code = """
    if (showSourceDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = {
                Text(
                    text = "Elegí una fuente",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (isLandscape) 420.dp else 560.dp)
                        .verticalScroll(sourceDialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sourceMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    movieSources.forEach { source ->
                        Button(
                            onClick = {
                                showSourceDialog = false
                                onPlay(source.streamUrl)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE50914),
                                contentColor = Color.White
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = source.title,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = listOf(source.subtitle, source.quality, source.language)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.82f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSourceDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
"""

if "if (showSourceDialog) {" not in vod_content:
    vod_content = vod_content.replace(
        "Box(\n        modifier = Modifier\n            .fillMaxSize()",
        dialog_code + "\n    Box(\n        modifier = Modifier\n            .fillMaxSize()"
    )

with open(path_vod, "w", encoding="utf-8") as f:
    f.write(vod_content)

print("VodDetailScreen.kt patched")
