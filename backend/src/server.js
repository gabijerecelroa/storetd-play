
(() => {
  try {
    const fsEnv = require("fs");
    const pathEnv = require("path");
    const envPath = pathEnv.join(__dirname, "..", ".env");

    if (fsEnv.existsSync(envPath)) {
      for (const line of fsEnv.readFileSync(envPath, "utf8").split(/\r?\n/)) {
        const m = line.match(/^\s*([A-Za-z0-9_]+)\s*=\s*(.*)\s*$/);
        if (!m) continue;
        process.env[m[1]] = m[2].trim().replace(/^['"]|['"]$/g, "");
      }
    }
  } catch (error) {
    console.error("No se pudo cargar .env:", error.message);
  }
})();

const express = require("express");
const cors = require("cors");
const path = require("path");
const crypto = require("crypto");
const { supabase, isDatabaseConfigured } = require("./db");
const { getAppConfig, updateAppConfig } = require("./appConfig");
const {
  refreshContentCacheForClient,
  getCachedContentSection,
  getSeriesFoldersLite,
  getSeriesFolderByKey,
  getMovieCategoriesLite,
  getMovieCategoryByKey,
  searchContentItems,
  filterPayloadAdultContent,
  buildSmartoneXtreamM3u
} = require("./playlistContent");

const app = express();



// 🔥 BOMBA DE CACHÉ GLOBAL (ACTUALIZACIÓN FORZADA) 🔥
app.use((req, res, next) => {
    if(req.query) {
        req.query.autoRefresh = '1'; // Obliga al servidor a dar la lista nueva
        req.query.force = 'true';
    }
    next();
});

const compression = require('compression');
app.use(compression());

app.use((req, res, next) => {
    if (req.url.includes("/api/content/")) {
        const oldSend = res.send;
        res.send = function(data) {
            try {
                if (typeof data === "string") {
                    let json = JSON.parse(data);
                    if (json && json.items && Array.isArray(json.items)) {
                        
                        // 1. EL MACHETEADOR (Cortamos de raíz a 40 para matar el temblor y la lentitud)
                        
                        
                        // 2. EL CLONADOR (Forzamos la imagen para que aparezcan los pósters de series)
                        json.items = json.items.map(item => {
                            let img = item.cover || item.logoUrl || item.stream_icon || item.icon || item.poster || ""; if(img.startsWith("http://image.tmdb.org")) img = img.replace("http://", "https://");
                            item.cover = img;
                            item.poster = img;
                            item.logoUrl = img;
                            return item;
                        });
                    }
                    arguments[0] = JSON.stringify(json);
                }
            } catch(e) {}
            oldSend.apply(res, arguments);
        };
    }
    next();
});

app.use((req, res, next) => { if(req.url.includes("live-group")) { req.url = req.url.replace("live-group", "live"); } next(); });


app.use(express.json({ limit: "50mb" }));
app.use(express.urlencoded({ extended: true, limit: "50mb" }));
const port = process.env.PORT || 3000;
const adminKey = process.env.ADMIN_KEY || "admin1234";

app.use(cors());

app.use((req, res, next) => {
  console.log("[REQ]", new Date().toISOString(), req.ip, req.method, req.originalUrl, req.headers["user-agent"] || "");
  next();
});

app.use(express.json({ limit: "1mb" }));
app.use(express.static(path.join(__dirname, "..", "public")));

// MAGMA_CLIENT_DEFAULTS_START
// Desde que StoreTD Play usa Magma global, ningún cliente nuevo necesita playlist M3U externa.
// Este middleware fuerza la fuente Magma en altas/ediciones desde admin/reseller.
function storetdForceMagmaClientDefaults(req, res, next) {
  try {
    const method = String(req.method || "").toUpperCase();
    const path = String(req.path || "");

    const isClientWrite =
      ["POST", "PUT", "PATCH"].includes(method) &&
      (
        path === "/admin/api/clients" ||
        path.startsWith("/admin/api/clients/") ||
        path === "/reseller/api/clients" ||
        path.startsWith("/reseller/api/clients/")
      );

    if (isClientWrite) {
      req.body = req.body || {};

      req.body.playlistUrl = "magma://global";
      req.body.playlist_url = "magma://global";
      req.body.playlist = "magma://global";
      req.body.m3uUrl = "magma://global";
      req.body.m3u_url = "magma://global";

      req.body.epgUrl = "";
      req.body.epg_url = "";
    }
  } catch (_) {
    // No bloquea la creación del cliente.
  }

  next();
}

app.use(storetdForceMagmaClientDefaults);
// MAGMA_CLIENT_DEFAULTS_END


// MAGMA_LIVE_LITE_START
function magmaLiteBaseUrl() {
  return String(process.env.MAGMA_BASE_URL || "http://tv.m3uts.xyz").replace(/\/+$/, "");
}

function magmaLiteUser() {
  return String(process.env.MAGMA_USER || "m").trim();
}

function magmaLitePass() {
  return String(process.env.MAGMA_PASS || "m").trim();
}

function magmaLiteDeviceId() {
  return String(process.env.MAGMA_DEVICE_ID || "c0041021c5c95679").trim();
}

function magmaLitePublicBaseUrl(req) {
  return String(
    process.env.MAGMA_PUBLIC_BASE_URL ||
    `${req.protocol}://${req.get("host")}`
  ).replace(/\/+$/, "");
}

function magmaLiteHeaders() {
  const hash = String(process.env.MAGMA_HASH || "").trim();

  const headers = {
    "X-App": "di",
    "X-Version": "10/1.0.9",
    "X-Did": magmaLiteDeviceId(),
    "User-Agent": "Magma Player/10"
  };

  if (hash) {
    headers["X-Hash"] = hash;
  }

  return headers;
}

function magmaLiteAllowedCodes() {
  return String(process.env.MAGMA_LIVE_CODES || "")
    .split(",")
    .map((item) => normalizeCode(item))
    .filter(Boolean);
}

function magmaLiteIsEnabledForCode(code, client) {
  const activationCode = normalizeCode(code);
  const allowed = magmaLiteAllowedCodes();

  if (allowed.includes("*")) return true;
  if (allowed.includes(activationCode)) return true;

  const playlistUrl = String(client?.playlist_url || "").trim().toLowerCase();
  return playlistUrl.startsWith("magma://");
}

async function magmaLiteGetClient(code) {
  const activationCode = normalizeCode(code);

  if (!activationCode) {
    return { ok: false, status: 400, message: "Falta código de activación." };
  }

  const { data, error } = await supabase
    .from("clients")
    .select("activation_code,status,expires_at,playlist_url")
    .eq("activation_code", activationCode)
    .maybeSingle();

  if (error) throw error;

  if (!data) {
    return { ok: false, status: 404, message: "Cliente no encontrado." };
  }

  if (String(data.status || "").toLowerCase() !== "activa") {
    return { ok: false, status: 403, message: "Cuenta no activa." };
  }

  if (isExpired(data.expires_at)) {
    return { ok: false, status: 403, message: "Cuenta vencida." };
  }

  return {
    ok: true,
    activationCode,
    client: data
  };
}

async function magmaLiteFetchJson(action) {
  const safeAction = String(action || "").trim();

  if (!safeAction) {
    throw new Error("Acción Magma vacía.");
  }

  const url =
    `${magmaLiteBaseUrl()}/player_api.php?username=${encodeURIComponent(magmaLiteUser())}` +
    `&password=${encodeURIComponent(magmaLitePass())}` +
    `&action=${encodeURIComponent(safeAction)}`;

  const response = await fetch(url, {
    headers: {
      "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 15; moto g84 5G Build/V1TC35H.88-20-1-6)",
      "Accept": "application/json, text/plain, */*",
      "Cache-Control": "no-cache",
      "Pragma": "no-cache"
    }
  });

  const text = await response.text();

  if (!response.ok) {
    throw new Error(`Magma catálogo HTTP ${response.status}: ${text.slice(0, 160)}`);
  }

  if (!text.trim()) {
    throw new Error(`Magma catálogo vacío para ${safeAction}.`);
  }

  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(`Magma catálogo JSON inválido para ${safeAction}: ${text.slice(0, 160)}`);
  }
}

// MAGMA_LIVE_GEN_CACHE_START
const magmaLiteLiveUrlCache = new Map();
const magmaLiteLiveUrlPending = new Map();

const MAGMA_LIVE_URL_CACHE_TTL_MS = Number(process.env.MAGMA_LIVE_URL_CACHE_TTL_MS || 45000);
const MAGMA_LIVE_URL_STALE_TTL_MS = Number(process.env.MAGMA_LIVE_URL_STALE_TTL_MS || 300000);

function magmaLiteDelay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function magmaLiteRetryAfterMs(text) {
  const raw = String(text || "");

  try {
    const parsed = JSON.parse(raw);
    const message = String(parsed.message || "");
    const match = message.match(/retry in\s+([0-9]+)\s+seconds?/i);
    if (match) return Math.max(1000, Number(match[1]) * 1000);
  } catch (_) {}

  const match = raw.match(/retry in\s+([0-9]+)\s+seconds?/i);
  if (match) return Math.max(1000, Number(match[1]) * 1000);

  return 1500;
}

async function magmaLiteGenerateLiveUrlRaw(id) {
  const body = new URLSearchParams({
    id,
    cast: "false",
    device: magmaLiteDeviceId(),
    code: ""
  });

  const response = await fetch(`${magmaLiteBaseUrl()}/stream/gen/${id}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 15; StoreTD Play)"
    },
    body
  });

  const text = String(await response.text() || "").trim();

  if (!response.ok) {
    const error = new Error(`Magma stream/gen HTTP ${response.status}: ${text.slice(0, 160)}`);
    error.status = response.status;
    error.body = text;
    throw error;
  }

  if (!/^https?:\/\/.+\.m3u8/i.test(text)) {
    const error = new Error(`Respuesta Magma inválida: ${text.slice(0, 160)}`);
    error.status = 502;
    error.body = text;
    throw error;
  }

  return text;
}

async function magmaLiteGenerateLiveUrl(streamId) {
  const id = String(streamId || "").replace(/[^0-9]/g, "");

  if (!id) {
    throw new Error("streamId inválido.");
  }

  const now = Date.now();
  const cached = magmaLiteLiveUrlCache.get(id);

  if (cached && cached.url && now - cached.updatedAt < MAGMA_LIVE_URL_CACHE_TTL_MS) {
    return cached.url;
  }

  const pending = magmaLiteLiveUrlPending.get(id);
  if (pending) {
    return pending;
  }

  const promise = (async () => {
    try {
      const url = await magmaLiteGenerateLiveUrlRaw(id);

      magmaLiteLiveUrlCache.set(id, {
        url,
        updatedAt: Date.now()
      });

      return url;
    } catch (error) {
      const retryMs = magmaLiteRetryAfterMs(error.body || error.message || "");

      if (
        error.status === 429 &&
        cached &&
        cached.url &&
        Date.now() - cached.updatedAt < MAGMA_LIVE_URL_STALE_TTL_MS
      ) {
        console.warn(`Magma stream/gen 429 para ${id}; usando URL cacheada. Retry sugerido: ${retryMs}ms`);
        return cached.url;
      }

      if (error.status === 429 && retryMs > 0 && retryMs <= 3000) {
        await magmaLiteDelay(retryMs);

        const url = await magmaLiteGenerateLiveUrlRaw(id);

        magmaLiteLiveUrlCache.set(id, {
          url,
          updatedAt: Date.now()
        });

        return url;
      }

      throw error;
    } finally {
      magmaLiteLiveUrlPending.delete(id);
    }
  })();

  magmaLiteLiveUrlPending.set(id, promise);

  return promise;
}
// MAGMA_LIVE_GEN_CACHE_END

// Catálogo dinámico TV Magma.
// No guarda canales en playlist_cache.
// Solo responde para códigos habilitados en MAGMA_LIVE_CODES.
app.get("/api/content/live", async (req, res, next) => {
  if (!isDatabaseConfigured()) return next();

  try {
    const code = normalizeCode(req.query.code || req.query.activationCode);
    const valid = await magmaLiteGetClient(code);

    if (!valid.ok) {
      return next();
    }

    if (!magmaLiteIsEnabledForCode(valid.activationCode, valid.client)) {
      return next();
    }

    const [categories, streams] = await Promise.all([
      magmaLiteFetchJson("get_live_categories"),
      magmaLiteFetchJson("get_live_streams")
    ]);

    const categoryMap = new Map(
      (Array.isArray(categories) ? categories : []).map((cat) => [
        String(cat.category_id),
        String(cat.category_name || "Sin categoría")
      ])
    );

    const publicBase = magmaLitePublicBaseUrl(req);

    const items = (Array.isArray(streams) ? streams : [])
      .map((item) => {
        const streamId = item.stream_id || item.license;
        const group = categoryMap.get(String(item.category_id)) || "Sin categoría";

        return {
          name: String(item.name || "Canal").trim(),
          group,
          tvgId: String(item.epg_channel_id || ""),
          logoUrl: String(item.stream_icon || item.thumbnail || ""),
          streamUrl: `${publicBase}/magma-lite/live/${streamId}.m3u8?code=${encodeURIComponent(valid.activationCode)}`,
          type: "live",
          source: "magma-lite",
          streamId
        };
      })
      .filter((item) => item.name && item.streamUrl && item.streamId);

    const groups = [
      "Todos",
      ...Array.from(new Set(items.map((item) => item.group))).sort()
    ];

    res.setHeader("Cache-Control", "no-store");

    return res.json({
      success: true,
      fromCache: false,
      noServerCache: true,
      source: "magma-live-dynamic",
      section: "live",
      activationCode: valid.activationCode,
      itemCount: items.length,
      groups,
      items
    });
  } catch (error) {
    console.error("Magma live catalog error:", error);
    return res.status(500).json({
      success: false,
      message: "No se pudo leer TV en vivo Magma.",
      error: error.message
    });
  }
});

// Playlist liviana.
// El VPS genera el link actualizado y devuelve el .m3u8.
// Los segmentos .ts quedan directos al proveedor, no pasan por el VPS.
app.get("/magma-lite/live/:streamId.m3u8", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const code = normalizeCode(req.query.code);
    const valid = await magmaLiteGetClient(code);

    if (!valid.ok) {
      return res.status(valid.status).json({
        success: false,
        message: valid.message
      });
    }

    if (!magmaLiteIsEnabledForCode(valid.activationCode, valid.client)) {
      return res.status(403).json({
        success: false,
        message: "Magma Live no habilitado para este cliente."
      });
    }

    const streamId = String(req.params.streamId || "").replace(/[^0-9]/g, "");
    const secureUrl = await magmaLiteGenerateLiveUrl(streamId);

    const response = await fetch(secureUrl, {
      headers: magmaLiteHeaders()
    });

    const text = await response.text();

    if (!response.ok) {
      return res.status(response.status).send(text);
    }

    res.setHeader("Content-Type", "application/vnd.apple.mpegurl");
    res.setHeader("Cache-Control", "no-store");
    res.send(text);
  } catch (error) {
    console.error("Magma lite playlist error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo generar playlist Magma.",
      error: error.message
    });
  }
});


async function magmaLiteFetchJsonOptional(action, fallback = []) {
  try {
    return await magmaLiteFetchJson(action);
  } catch (error) {
    console.warn(`Magma optional catalog failed for ${action}:`, error.message);
    return fallback;
  }
}


function magmaLiteVodHostLabel(value) {
  try {
    const host = new URL(String(value || "")).hostname.replace(/^www\./, "").toLowerCase();

    if (host.includes("vidhide")) return "Vidhide";
    if (host.includes("streamwish")) return "Streamwish";
    if (host.includes("bysejikuar")) return "Servidor 2";
    if (host.includes("josephseveralconcern")) return "Servidor 4";
    if (host.includes("do7go")) return "Servidor 5";

    return "Servidor externo";
  } catch (_) {
    return "Servidor externo";
  }
}

function magmaLiteVodLanguageLabel(value) {
  const text = String(value || "").trim().toLowerCase();

  if (!text) return "";
  if (text.includes("spanish") || text.includes("latino") || text.includes("español") || text.includes("espanol")) {
    return "Latino";
  }
  if (text.includes("english") || text.includes("ingles") || text.includes("inglés")) {
    return "Inglés";
  }

  return text.charAt(0).toUpperCase() + text.slice(1);
}

function magmaLiteNormalizeVodLinks(raw) {
  if (Array.isArray(raw)) return raw;

  if (raw && typeof raw === "object") {
    for (const key of ["items", "links", "sources", "data", "result"]) {
      if (Array.isArray(raw[key])) return raw[key];
    }
  }

  return [];
}

async function magmaLiteFetchVodLinks(vodId) {
  const cleanId = String(vodId || "").replace(/[^0-9]/g, "");

  if (!cleanId) return [];

  const url =
    `${magmaLiteBaseUrl()}/player_api.php?username=${encodeURIComponent(magmaLiteUser())}` +
    `&password=${encodeURIComponent(magmaLitePass())}` +
    `&action=get_vod_links&vod_id=${encodeURIComponent(cleanId)}`;

  const response = await fetch(url, {
    headers: {
      "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 15; moto g84 5G Build/V1TC35H.88-20-1-6)",
      "Accept": "application/json",
      "Accept-Encoding": "gzip"
    }
  });

  const text = await response.text();

  if (!response.ok || !text.trim()) {
    console.warn("get_vod_links vacío/no OK:", cleanId, response.status, text.slice(0, 120));
    return [];
  }

  try {
    return magmaLiteNormalizeVodLinks(JSON.parse(text));
  } catch (error) {
    console.warn("get_vod_links JSON inválido:", cleanId, error.message, text.slice(0, 120));
    return [];
  }
}



function magmaLiteMovieCategoryTitle(key) {
  const titles = {
    "1": "Drama",
    "2": "Familia",
    "3": "Animación",
    "4": "Comedia",
    "5": "Fantasía",
    "6": "Acción",
    "7": "Aventura",
    "8": "Ciencia ficción",
    "10": "Crimen",
    "11": "Misterio",
    "12": "Terror",
    "13": "Romance",
    "15": "Historia",
    "17": "Música",
    "53": "Suspenso",
    "55": "Western",
    "56": "Documental",
    "62": "Guerra",
    "64": "Barbie",
    "66": "Superhéroes",
    "67": "Mundo Marvel",
    "68": "DC / Superhéroes",
    "69": "Sagas infantiles",
    "71": "Thriller",
    "73": "Asiáticas",
    "401": "Top",
    "500": "Lo más relevante",
    "1000": "Películas"
  };

  return titles[String(key)] || `Categoría ${key}`;
}


// MAGMA_MOVIES_LITE_START
function magmaLiteImageUrl(value, size = "w500") {
  const text = String(value || "").trim();

  if (!text) return "";
  if (/^https?:\/\//i.test(text)) return text;
  if (text.startsWith("/")) return `https://image.tmdb.org/t/p/${size}${text}`;

  return text;
}

function magmaLiteMovieCategoryName(categoryMap, categoriesIds) {
  const first = String(categoriesIds || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean)[0];

  return categoryMap.get(String(first)) || "Películas";
}

function magmaLiteMovieMatchesCategory(item, key) {
  const cleanKey = String(key || "").trim();

  if (!cleanKey || cleanKey === "all" || cleanKey === "todos") return true;

  return String(item.categories_ids || item.category_id || "")
    .split(",")
    .map((value) => value.trim())
    .includes(cleanKey);
}

// Categorías livianas de películas Magma.
// No guarda películas pesadas ni segmentos en el VPS.

// MAGMA_MOVIES_FLAT_ROUTE_START
// Fuerza /api/content/movies a responder Magma y evita fallback a lista vieja.
app.get("/api/content/movies", async (req, res, next) => {
  if (!isDatabaseConfigured()) return next();

  try {
    const code = normalizeCode(req.query.code || req.query.activationCode);
    const valid = await magmaLiteGetClient(code);

    if (!valid.ok) return next();

    if (!magmaLiteIsEnabledForCode(valid.activationCode, valid.client)) {
      return next();
    }

    const [categories, streams] = await Promise.all([
      magmaLiteFetchJsonOptional("get_vod_categories", []),
      magmaLiteFetchJsonOptional("get_vod_streams", [])
    ]);

    const categoryMap = new Map(
      (Array.isArray(categories) ? categories : []).map((cat) => [
        String(cat.category_id),
        String(cat.category_name || "Películas")
      ])
    );

    const publicBase = magmaLitePublicBaseUrl(req);

    const items = (Array.isArray(streams) ? streams : [])
      .map((item) => {
        const streamId = item.stream_id || item.movie_id || item.id || item.license;
        const group = magmaLiteMovieCategoryName(categoryMap, item.categories_ids || item.category_id);
        const release = String(item.release || item.releaseDate || "").trim();

        return {
          name: String(item.name || "Película").trim(),
          group,
          tvgId: "",
          logoUrl: magmaLiteImageUrl(item.stream_icon || item.cover || item.poster_path, "w500"),
          posterUrl: magmaLiteImageUrl(item.stream_icon || item.cover || item.poster_path, "w500"),
          backdropUrl: magmaLiteImageUrl(item.backdrop || item.backdrop_path, "w780"),
          streamUrl: `${publicBase}/magma-lite/movie/${streamId}.m3u8?code=${encodeURIComponent(valid.activationCode)}`,
          type: "movie",
          source: "magma-lite",
          streamId,
          release,
          rating: item.rating_5based || item.rating || 0
        };
      })
      .filter((item) => item.name && item.streamUrl && item.streamId);

    const groups = [
      "Todos",
      ...Array.from(new Set(items.map((item) => item.group))).sort()
    ];

    res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");

    return res.json({
      success: true,
      fromCache: false,
      noServerCache: true,
      source: "magma-movies-dynamic-flat",
      section: "movies",
      activationCode: valid.activationCode,
      itemCount: items.length,
      groups,
      items
    });
  } catch (error) {
    console.error("Magma flat movies error:", error);
    return res.status(500).json({
      success: false,
      message: "No se pudieron leer películas Magma.",
      error: error.message
    });
  }
});
// MAGMA_MOVIES_FLAT_ROUTE_END

app.get("/api/content/movie-categories-lite", async (req, res, next) => {
  if (!isDatabaseConfigured()) return next();

  try {
    const code = normalizeCode(req.query.code || req.query.activationCode);
    const valid = await magmaLiteGetClient(code);

    if (!valid.ok) return next();

    if (!magmaLiteIsEnabledForCode(valid.activationCode, valid.client)) {
      return next();
    }

    const [categories, streams] = await Promise.all([
      magmaLiteFetchJsonOptional("get_vod_categories", []),
      magmaLiteFetchJsonOptional("get_vod_streams", [])
    ]);

    const streamsArray = Array.isArray(streams) ? streams : [];

    const counts = new Map();

    for (const movie of streamsArray) {
      const ids = String(movie.categories_ids || movie.category_id || "")
        .split(",")
        .map((value) => value.trim())
        .filter(Boolean);

      for (const id of ids) {
        counts.set(id, (counts.get(id) || 0) + 1);
      }
    }

    const categoryRows = (Array.isArray(categories) && categories.length > 0)
      ? categories
      : Array.from(counts.keys()).map((key) => ({
          category_id: key,
          category_name: magmaLiteMovieCategoryTitle(key)
        }));

    const items = categoryRows
      .map((cat) => {
        const key = String(cat.category_id || "").trim();
        const title = String(cat.category_name || "Películas").trim() || "Películas";

        return {
          key,
          title,
          itemCount: counts.get(key) || 0,
          posterUrl: "",
          backdropUrl: "",
          source: "magma-lite"
        };
      })
      .filter((item) => item.key && item.itemCount > 0);

    const totalCount = streamsArray.length;

    res.setHeader("Cache-Control", "no-store");
    return res.json({
      success: true,
      source: "magma-movies-dynamic",
      noServerCache: true,
      activationCode: valid.activationCode,
      itemCount: items.length,
      totalCount,
      items
    });
  } catch (error) {
    console.error("Magma movie categories error:", error);
    return res.status(500).json({
      success: false,
      message: "No se pudieron leer categorías de películas Magma.",
      error: error.message
    });
  }
});

// Películas de una categoría Magma.
app.get("/api/content/movie-category", async (req, res, next) => {
  if (!isDatabaseConfigured()) return next();

  try {
    const code = normalizeCode(req.query.code || req.query.activationCode);
    const key = String(req.query.key || req.query.category || req.query.categoryId || "all").trim();

    const valid = await magmaLiteGetClient(code);

    if (!valid.ok) return next();

    if (!magmaLiteIsEnabledForCode(valid.activationCode, valid.client)) {
      return next();
    }

    const [categories, streams] = await Promise.all([
      magmaLiteFetchJsonOptional("get_vod_categories", []),
      magmaLiteFetchJsonOptional("get_vod_streams", [])
    ]);

    const categoryMap = new Map(
      (Array.isArray(categories) ? categories : []).map((cat) => [
        String(cat.category_id),
        String(cat.category_name || "Películas")
      ])
    );

    const publicBase = magmaLitePublicBaseUrl(req);

    const items = (Array.isArray(streams) ? streams : [])
      .filter((item) => magmaLiteMovieMatchesCategory(item, key))
      .map((item) => {
        const streamId = item.stream_id || item.movie_id || item.id || item.license;
        const group = magmaLiteMovieCategoryName(categoryMap, item.categories_ids || item.category_id);
        const release = String(item.release || item.releaseDate || "").trim();

        return {
          name: String(item.name || "Película").trim(),
          group,
          tvgId: "",
          logoUrl: magmaLiteImageUrl(item.stream_icon || item.cover || item.poster_path, "w500"),
          posterUrl: magmaLiteImageUrl(item.stream_icon || item.cover || item.poster_path, "w500"),
          backdropUrl: magmaLiteImageUrl(item.backdrop || item.backdrop_path, "w780"),
          streamUrl: `${publicBase}/magma-lite/movie/${streamId}.m3u8?code=${encodeURIComponent(valid.activationCode)}`,
          type: "movie",
          source: "magma-lite",
          streamId,
          release,
          rating: item.rating_5based || item.rating || 0
        };
      })
      .filter((item) => item.name && item.streamUrl && item.streamId);

    res.setHeader("Cache-Control", "no-store");
    return res.json({
      success: true,
      source: "magma-movies-dynamic",
      noServerCache: true,
      section: "movies",
      activationCode: valid.activationCode,
      key,
      itemCount: items.length,
      items
    });
  } catch (error) {
    console.error("Magma movie category error:", error);
    return res.status(500).json({
      success: false,
      message: "No se pudieron leer películas Magma.",
      error: error.message
    });
  }
});

// Selector de fuentes para películas.
// Por ahora entrega fuente principal. Luego se pueden agregar alternativas.


// MAGMA_SOURCE_FILTER_START
function magmaLiteSourceHost(value) {
  try {
    return new URL(String(value || "")).hostname.replace(/^www\./, "").toLowerCase();
  } catch (_) {
    return "";
  }
}

function magmaLiteSourceProvider(hostValue) {
  const host = String(hostValue || "").toLowerCase();

  if (host.includes("vidhide")) {
    return { key: "vidhide", label: "Vidhide", priority: 0 };
  }

  if (host.includes("streamwish")) {
    return { key: "streamwish", label: "Streamwish", priority: 1 };
  }

  if (host.includes("filelions")) {
    return { key: "filelions", label: "Filelions", priority: 2 };
  }

  if (host.includes("streamtape")) {
    return { key: "streamtape", label: "Streamtape", priority: 3 };
  }

  if (host.includes("wolfstream")) {
    return { key: "wolfstream", label: "Wolfstream", priority: 4 };
  }

  if (host.includes("do7go")) {
    return { key: "do7go", label: "Servidor 5", priority: 5 };
  }

  if (host.includes("bysejikuar")) {
    return { key: "bysejikuar", label: "Servidor 2", priority: 6 };
  }

  if (host.includes("zpjid")) {
    return { key: "zpjid", label: "Servidor 3", priority: 7 };
  }

  if (host.includes("josephseveralconcern")) {
    return { key: "josephseveralconcern", label: "Servidor 4", priority: 8 };
  }

  return { key: `other:${host}`, label: "Servidor externo", priority: 50 };
}

function magmaLiteSourceIsBad(item) {
  const url = String(item?.streamUrl || item?.url || "").trim().toLowerCase();
  const host = magmaLiteSourceHost(url);

  if (!url.startsWith("http://") && !url.startsWith("https://")) return true;

  const badWords = [
    "doubleclick",
    "googlesyndication",
    "adsterra",
    "popads",
    "onclick",
    "profitablerate",
    "1xbet",
    "casino",
    "porn",
    "adult",
    "notification",
    "pushads"
  ];

  return badWords.some((word) => host.includes(word) || url.includes(word));
}

function magmaLiteSourceIsSpanish(item) {
  const language = String(item?.language || "").trim().toLowerCase();

  return language.includes("latino") ||
    language.includes("spanish") ||
    language.includes("español") ||
    language.includes("espanol") ||
    language.includes("castellano");
}

function magmaLiteSourceLanguageScore(item) {
  const language = String(item?.language || "").trim().toLowerCase();

  if (
    language.includes("latino") ||
    language.includes("spanish") ||
    language.includes("español") ||
    language.includes("espanol") ||
    language.includes("castellano")
  ) {
    return -100;
  }

  if (!language) return -10;

  if (
    language.includes("english") ||
    language.includes("inglés") ||
    language.includes("ingles")
  ) {
    return 30;
  }

  return 0;
}

function magmaLiteSourceQualityScore(item) {
  const quality = String(item?.quality || item?.resolution || "").trim().toLowerCase();

  if (quality.includes("4k")) return -12;
  if (quality.includes("1080") || quality.includes("fhd")) return -10;
  if (quality.includes("720") || quality.includes("hd")) return -8;
  if (quality.includes("cam")) return 20;

  return 0;
}

function magmaLiteSourceScore(item) {
  const host = magmaLiteSourceHost(item?.streamUrl || item?.url || "");
  const provider = magmaLiteSourceProvider(host);

  return provider.priority * 100 +
    magmaLiteSourceLanguageScore(item) +
    magmaLiteSourceQualityScore(item);
}

function magmaLitePrepareVisibleSource(item, index) {
  const host = magmaLiteSourceHost(item?.streamUrl || item?.url || "");
  const provider = magmaLiteSourceProvider(host);

  const subtitle = String(item?.subtitle || "").trim();
  const lowerSubtitle = subtitle.toLowerCase();

  const isGenericSubtitle =
    !subtitle ||
    lowerSubtitle === "servidor externo" ||
    /^servidor\s+[0-9]+$/i.test(subtitle);

  return {
    ...item,
    title: `Servidor ${index + 1}`,
    subtitle: isGenericSubtitle ? provider.label : subtitle
  };
}


function magmaLiteSourceIdNumber(item) {
  const value = Number(item?.id || 0);
  return Number.isFinite(value) ? value : 0;
}

function magmaLiteSourceQualityRank(item) {
  const quality = String(item?.quality || item?.resolution || "").trim().toLowerCase();

  if (quality.includes("4k")) return 5;
  if (quality.includes("1080") || quality.includes("fhd")) return 4;
  if (quality.includes("720") || quality.includes("hd")) return 3;
  if (quality.includes("cam")) return 0;

  return 1;
}

function magmaLiteIsBetterSource(candidate, current) {
  if (!current) return true;

  const candidateQuality = magmaLiteSourceQualityRank(candidate);
  const currentQuality = magmaLiteSourceQualityRank(current);

  if (candidateQuality !== currentQuality) {
    return candidateQuality > currentQuality;
  }

  const candidateSpanish = magmaLiteSourceIsSpanish(candidate);
  const currentSpanish = magmaLiteSourceIsSpanish(current);

  if (candidateSpanish !== currentSpanish) {
    return candidateSpanish;
  }

  const candidateId = magmaLiteSourceIdNumber(candidate);
  const currentId = magmaLiteSourceIdNumber(current);

  if (candidateId !== currentId) {
    return candidateId > currentId;
  }

  return magmaLiteSourceScore(candidate) < magmaLiteSourceScore(current);
}

function magmaLiteFilterPlayableSources(items) {
  const raw = Array.isArray(items) ? items : [];

  const clean = [];
  const seenUrls = new Set();

  for (const item of raw) {
    if (magmaLiteSourceIsBad(item)) continue;

    const url = String(item?.streamUrl || item?.url || "").trim();

    if (!url || seenUrls.has(url)) continue;

    seenUrls.add(url);
    clean.push(item);
  }

  if (clean.length === 0) return [];

  // 1) Si hay HD/FHD/4K, descartamos CAM y calidades viejas.
  const maxQualityRank = Math.max(...clean.map(magmaLiteSourceQualityRank));
  let filtered = maxQualityRank >= 2
    ? clean.filter((item) => magmaLiteSourceQualityRank(item) === maxQualityRank)
    : clean;

  // 2) Si hay Latino/Español, usamos solo Latino/Español.
  const spanishSources = filtered.filter(magmaLiteSourceIsSpanish);
  if (spanishSources.length > 0) {
    filtered = spanishSources;
  }

  // 3) Bysejikuar queda como respaldo. Si hay suficientes alternativas, se oculta.
  const withoutBysejikuar = filtered.filter((item) => {
    const host = magmaLiteSourceHost(item?.streamUrl || item?.url || "");
    return magmaLiteSourceProvider(host).key !== "bysejikuar";
  });

  if (withoutBysejikuar.length >= 2) {
    filtered = withoutBysejikuar;
  }

  // 4) Un solo link por proveedor, pero eligiendo el más nuevo y mejor.
  const bestByProvider = new Map();

  for (const item of filtered) {
    const host = magmaLiteSourceHost(item?.streamUrl || item?.url || "");
    const provider = magmaLiteSourceProvider(host);
    const key = provider.key || host || String(item?.id || "");

    const current = bestByProvider.get(key);

    if (magmaLiteIsBetterSource(item, current)) {
      bestByProvider.set(key, item);
    }
  }

  filtered = Array.from(bestByProvider.values());

  // 5) Si tenemos proveedores conocidos, ocultamos dominios raros.
  const known = filtered.filter((item) => {
    const host = magmaLiteSourceHost(item?.streamUrl || item?.url || "");
    const provider = magmaLiteSourceProvider(host);
    return !String(provider.key || "").startsWith("other:");
  });

  if (known.length >= 2) {
    filtered = known;
  }

  filtered.sort((a, b) => magmaLiteSourceScore(a) - magmaLiteSourceScore(b));

  const hasVidhide = filtered.some((item) => {
    const host = magmaLiteSourceHost(item?.streamUrl || item?.url || "");
    return magmaLiteSourceProvider(host).key === "vidhide";
  });

  const defaultMax = hasVidhide ? 4 : 5;
  const maxSources = Number(process.env.MAGMA_MAX_VOD_SOURCES || defaultMax);
  const safeMax = Math.max(1, Math.min(8, maxSources));

  return filtered
    .slice(0, safeMax)
    .map((item, index) => magmaLitePrepareVisibleSource(item, index));
}
// MAGMA_SOURCE_FILTER_END

app.get("/api/magma-lite/movie-sources", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const code = normalizeCode(req.query.code);
    const valid = await magmaLiteGetClient(code);

    if (!valid.ok) {
      return res.status(valid.status).json({
        success: false,
        message: valid.message
      });
    }

    if (!magmaLiteIsEnabledForCode(valid.activationCode, valid.client)) {
      return res.status(403).json({
        success: false,
        message: "Contenido no habilitado para este cliente."
      });
    }

    const streamId = String(req.query.id || req.query.streamId || "")
      .replace(/[^0-9]/g, "");

    const kind = String(req.query.kind || "")
      .trim()
      .toLowerCase();

    const seriesId = String(req.query.seriesId || req.query.series_id || req.query.serie || "")
      .replace(/[^0-9]/g, "");

    const season = String(req.query.season || "")
      .replace(/[^0-9]/g, "");

    const episode = String(req.query.episode || req.query.episodeNum || "")
      .replace(/[^0-9]/g, "");

    let rawLinks = [];
    let sourceName = "vod-links";

    if (kind === "episode" && seriesId && season && episode) {
      rawLinks = await magmaSeriesFetchJson("get_episode_links", {
        serie: seriesId,
        season,
        episode
      });

      sourceName = "episode-links";
    } else {
      if (!streamId) {
        return res.status(400).json({
          success: false,
          message: "Falta id."
        });
      }

      rawLinks = await magmaSeriesFetchJson("get_vod_links", {
        vod_id: streamId
      });

      sourceName = "vod-links";
    }

    const links = Array.isArray(rawLinks) ? rawLinks : [];

    const items = links
      .map((item, index) => {
        const url = String(item.url || item.streamUrl || "").trim();

        return {
          id: String(item.id || `${streamId || seriesId}-${season}-${episode}-${index + 1}`),
          title: `Servidor ${index + 1}`,
          subtitle: magmaLiteVodHostLabel(url),
          quality: String(item.quality || item.resolution || "Auto").trim() || "Auto",
          language: magmaLiteVodLanguageLabel(item.language),
          streamUrl: url,
          type: "external"
        };
      })
      .filter((item) => item.streamUrl);

    res.setHeader("Cache-Control", "no-store");

        const visibleItems = magmaLiteFilterPlayableSources(items);

return res.json({
      success: true,
      source: sourceName,
      available: items.length > 0,
      streamId,
      kind,
      seriesId,
      season,
      episode,
      itemCount: visibleItems.length,
      rawItemCount: items.length,
      hiddenItemCount: Math.max(0, items.length - visibleItems.length),
      items: visibleItems
    });
  } catch (error) {
    console.error("Magma movie/episode sources error:", error);

    res.status(500).json({
      success: false,
      message: "No se pudieron obtener fuentes.",
      error: error.message
    });
  }
});


// Playlist liviana para película.
// El VPS genera el link actualizado y devuelve el .m3u8.
// Los segmentos .ts van directo al proveedor.
app.get("/magma-lite/movie/:streamId.m3u8", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const code = normalizeCode(req.query.code);
    const valid = await magmaLiteGetClient(code);

    if (!valid.ok) {
      return res.status(valid.status).json({
        success: false,
        message: valid.message
      });
    }

    if (!magmaLiteIsEnabledForCode(valid.activationCode, valid.client)) {
      return res.status(403).json({
        success: false,
        message: "Magma Movies no habilitado para este cliente."
      });
    }

    const streamId = String(req.params.streamId || "").replace(/[^0-9]/g, "");

    if (!streamId) {
      return res.status(400).json({
        success: false,
        message: "ID de película inválido."
      });
    }

    const secureUrl = await magmaLiteGenerateLiveUrl(streamId);

    const response = await fetch(secureUrl, {
      headers: magmaLiteHeaders()
    });

    const text = await response.text();

    if (!response.ok) {
      return res.status(response.status).send(text);
    }

    res.setHeader("Content-Type", "application/vnd.apple.mpegurl");
    res.setHeader("Cache-Control", "no-store");
    res.send(text);
  } catch (error) {
    console.error("Magma movie playlist error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo generar playlist de película Magma.",
      error: error.message
    });
  }
});


// MAGMA_SERIES_LITE_START
function magmaSeriesCatalogHeaders() {
  return {
    "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 15; StoreTD Play)",
    "Accept": "application/json,text/plain,*/*",
    "Accept-Encoding": "gzip"
  };
}

function magmaSeriesImageUrl(value, size = "w500") {
  const text = String(value || "").trim();

  if (!text || text === "-") return "";

  if (/^https?:\/\//i.test(text)) return text;

  if (text.startsWith("/")) {
    return `https://image.tmdb.org/t/p/${size}${text}`;
  }

  return text;
}

async function magmaSeriesFetchJson(action, extra = {}) {
  const base = magmaLiteBaseUrl();
  const params = new URLSearchParams();

  params.set("username", magmaLiteUser());
  params.set("password", magmaLitePass());
  params.set("action", action);

  Object.entries(extra || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      params.set(key, String(value));
    }
  });

  const url = `${base}/player_api.php?${params.toString()}`;
  const response = await fetch(url, {
    headers: magmaSeriesCatalogHeaders()
  });

  const text = await response.text();

  if (!response.ok) {
    throw new Error(`Magma series HTTP ${response.status}: ${text.slice(0, 180)}`);
  }

  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(`Respuesta Magma series inválida: ${text.slice(0, 180)}`);
  }
}

function magmaBuildSeriesCategoryMap(categories) {
  const map = new Map();

  (Array.isArray(categories) ? categories : []).forEach((cat) => {
    const id = String(cat.category_id || "").trim();
    const name = String(cat.category_name || "").trim();

    if (id && name) {
      map.set(id, name);
    }
  });

  return map;
}

function magmaBuildSeriesEpisodes({ info, seriesId, code, req }) {
  const publicBase = magmaLitePublicBaseUrl(req);
  const seriesTitle = String(
    info?.info?.name ||
    info?.info?.title ||
    `Serie ${seriesId}`
  ).trim();

  const seriesPoster = magmaSeriesImageUrl(
    info?.info?.cover ||
    info?.info?.movie_image ||
    "",
    "w500"
  );

  const episodesRoot = info?.episodes || {};
  const items = [];

  Object.keys(episodesRoot)
    .sort((a, b) => Number(a) - Number(b))
    .forEach((seasonKey) => {
      const episodes = Array.isArray(episodesRoot[seasonKey])
        ? episodesRoot[seasonKey]
        : [];

      episodes.forEach((ep) => {
        const episodeId = String(ep.id || ep.episode_id || ep.stream_id || "").trim();
        if (!episodeId) return;

        const season = Number(ep.season || ep.info?.season || seasonKey || 0);
        const episode = Number(ep.episode_num || ep.episode || 0);
        const title = String(ep.title || ep.name || `Episodio ${episode || ""}`).trim();

        const seasonLabel = season > 0 ? `T${String(season).padStart(2, "0")}` : "T--";
        const episodeLabel = episode > 0 ? `E${String(episode).padStart(2, "0")}` : "E--";
        const cleanName = `${seasonLabel}${episodeLabel} - ${title}`;

        const image = magmaSeriesImageUrl(
          ep.info?.movie_image ||
          ep.movie_image ||
          ep.cover ||
          "",
          "w500"
        ) || seriesPoster;

        items.push({
          id: episodeId,
          name: cleanName,
          title,
          group: `Series · ${seriesTitle}`,
          tvgId: episodeId,
          logoUrl: image,
          posterUrl: image,
          backdropUrl: magmaSeriesImageUrl(info?.info?.backdrop || "", "w780"),
          streamUrl: `${publicBase}/magma-lite/movie/${episodeId}.m3u8?code=${encodeURIComponent(code)}&kind=episode&seriesId=${encodeURIComponent(seriesId)}&season=${encodeURIComponent(season)}&episode=${encodeURIComponent(episode)}`,
          type: "series",
          source: "magma-lite",
          seriesId,
          streamId: episodeId,
          episodeId,
          season,
          episode,
          plot: String(ep.info?.plot || ep.plot || ""),
          rating: Number(ep.info?.rating || ep.rating || 0),
          duration: ep.info?.duration_secs || ep.info?.duration || ep.duration || 0
        });
      });
    });

  items.sort((a, b) => {
    return Number(a.season || 0) - Number(b.season || 0)
      || Number(a.episode || 0) - Number(b.episode || 0)
      || String(a.name).localeCompare(String(b.name));
  });

  return {
    title: seriesTitle,
    posterUrl: seriesPoster,
    backdropUrl: magmaSeriesImageUrl(info?.info?.backdrop || "", "w780"),
    plot: String(info?.info?.plot || ""),
    items
  };
}

// Catálogo liviano de series Magma.
// Se inserta antes de las rutas híbridas para que los códigos Magma usen este catálogo.
app.get("/api/content/series-folders-lite", async (req, res, next) => {
  if (!isDatabaseConfigured()) return next();

  try {
    const code = normalizeCode(req.query.code || req.query.activationCode);
    const valid = await magmaLiteGetClient(code);

    if (!valid.ok) {
      return next();
    }

    if (!magmaLiteIsEnabledForCode(valid.activationCode, valid.client)) {
      return next();
    }

    const [categories, series] = await Promise.all([
      magmaSeriesFetchJson("get_series_categories"),
      magmaSeriesFetchJson("get_series")
    ]);

    const categoryMap = magmaBuildSeriesCategoryMap(categories);

    const folders = (Array.isArray(series) ? series : [])
      .map((item) => {
        const seriesId = String(item.series_id || item.id || "").trim();
        if (!seriesId) return null;

        const categoryId = String(item.category_id || "").trim();
        const categoriesText = String(item.categories || "").trim();
        const firstCategory = categoriesText.split(",").map((x) => x.trim()).filter(Boolean)[0];
        const group = categoryMap.get(categoryId) || categoryMap.get(firstCategory) || "Series";

        return {
          key: seriesId,
          title: String(item.name || `Serie ${seriesId}`).trim(),
          name: String(item.name || `Serie ${seriesId}`).trim(),
          group,
          category: group,
          logoUrl: magmaSeriesImageUrl(item.cover || item.stream_icon || "", "w500"),
          posterUrl: magmaSeriesImageUrl(item.cover || item.stream_icon || "", "w500"),
          backdropUrl: magmaSeriesImageUrl(item.backdrop_path || item.backdrop || "", "w780"),
          seriesId,
          itemCount: Number(item.episode_count || item.itemCount || item.episodes || 1),
          release: String(item.releaseDate || item.release || ""),
          rating: Number(item.rating_5based || item.rating || 0),
          plot: String(item.plot || ""),
          type: "series-folder",
          source: "magma-lite"
        };
      })
      .filter(Boolean)
      .sort((a, b) => {
        return String(a.group).localeCompare(String(b.group))
          || String(a.title).localeCompare(String(b.title));
      });

    res.setHeader("Cache-Control", "no-store");

    return res.json({
      success: true,
      fromCache: false,
      noServerCache: true,
      source: "magma-series-dynamic",
      section: "series-folders-lite",
      mode: "magma-series-folders",
      activationCode: valid.activationCode,
      folderCount: folders.length,
      itemCount: folders.length,
      folders
    });
  } catch (error) {
    console.error("Magma series folders error:", error);
    return res.status(500).json({
      success: false,
      message: "No se pudieron leer series Magma.",
      error: error.message
    });
  }
});

// Episodios de una serie Magma.
app.get("/api/content/series-folder", async (req, res, next) => {
  if (!isDatabaseConfigured()) return next();

  try {
    const code = normalizeCode(req.query.code || req.query.activationCode);
    const key = String(req.query.key || "").trim();
    const valid = await magmaLiteGetClient(code);

    if (!valid.ok) {
      return next();
    }

    if (!magmaLiteIsEnabledForCode(valid.activationCode, valid.client)) {
      return next();
    }

    const seriesId = key.replace(/^series-/, "").trim();

    if (!seriesId) {
      return res.status(400).json({
        success: false,
        message: "Falta key de serie."
      });
    }

    const info = await magmaSeriesFetchJson("get_series_info", {
      series_id: seriesId
    });

    const built = magmaBuildSeriesEpisodes({
      info,
      seriesId,
      code: valid.activationCode,
      req
    });

    const folder = {
      key: seriesId,
      title: built.title,
      name: built.title,
      group: "Series",
      category: "Series",
      logoUrl: built.posterUrl,
      posterUrl: built.posterUrl,
      backdropUrl: built.backdropUrl,
      seriesId,
      itemCount: built.items.length,
      plot: built.plot,
      type: "series-folder",
      source: "magma-lite"
    };

    res.setHeader("Cache-Control", "no-store");

    return res.json({
      success: true,
      fromCache: false,
      noServerCache: true,
      source: "magma-series-dynamic",
      section: "series-folder",
      mode: "magma-series-episodes",
      activationCode: valid.activationCode,
      key: seriesId,
      seriesId,
      title: built.title,
      folder,
      itemCount: built.items.length,
      groups: ["Todos", built.title],
      items: built.items
    });
  } catch (error) {
    console.error("Magma series folder error:", error);
    return res.status(500).json({
      success: false,
      message: "No se pudieron leer capítulos Magma.",
      error: error.message
    });
  }
});
// MAGMA_SERIES_LITE_END


// MAGMA_MOVIES_LITE_END


// MAGMA_LIVE_LITE_END



function requireDb(res) {
  if (!isDatabaseConfigured()) {
    res.status(500).json({
      success: false,
      message: "Base de datos no configurada. Revisa SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY."
    });
    return false;
  }
  return true;
}

function normalizeCode(code) {
  return String(code || "").trim().toUpperCase();
}

function isExpired(expiresAt) {
  if (!expiresAt) return false;
  const today = new Date().toISOString().slice(0, 10);
  return String(expiresAt).slice(0, 10) < today;
}

function nowIso() {
  return new Date().toISOString();
}

const fs = require("fs");

const magmaCatalogVersionFile = path.join(__dirname, "..", "data", "magma-live-catalog-version.json");

function readMagmaLiveCatalogVersion() {
  try {
    const raw = fs.readFileSync(magmaCatalogVersionFile, "utf8");
    const parsed = JSON.parse(raw);
    return {
      version: Number(parsed.version || 0),
      updatedAt: parsed.updatedAt || null,
      reason: parsed.reason || ""
    };
  } catch {
    return {
      version: 0,
      updatedAt: null,
      reason: ""
    };
  }
}

function writeMagmaLiveCatalogVersion(reason = "manual") {
  fs.mkdirSync(path.dirname(magmaCatalogVersionFile), { recursive: true });

  const payload = {
    version: Date.now(),
    updatedAt: new Date().toISOString(),
    reason
  };

  fs.writeFileSync(magmaCatalogVersionFile, JSON.stringify(payload, null, 2));
  return payload;
}


function requireAdmin(req, res, next) {
  const key =
    req.headers["x-admin-key"] ||
    req.query.key ||
    req.body?.adminKey;

  if (key !== adminKey) {
    return res.status(401).json({
      success: false,
      message: "No autorizado."
    });
  }

  next();
}

function maskUrl(value) {
  const text = String(value || "");
  if (!text) return "";

  try {
    const url = new URL(text);
    return url.origin + "/***";
  } catch {
    if (text.length <= 18) return "***";
    return text.slice(0, 10) + "***" + text.slice(-6);
  }
}

function dbClientToApi(row) {
  return {
    customerName: row.customer_name || "",
    customerPhone: row.customer_phone || "",
    activationCode: row.activation_code || "",
    status: row.status || "Activa",
    expiresAt: row.expires_at || "",
    maxDevices: Number(row.max_devices || 1),
    playlistUrl: row.playlist_url || "",
    epgUrl: row.epg_url || "",
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };
}

function apiClientToDb(input, fixedCode) {
  return {
    customer_name: String(input.customerName || "").trim(),
    customer_phone: String(input.customerPhone || "").replace(/[^0-9]/g, "").trim(),
    activation_code: normalizeCode(fixedCode || input.activationCode),
    status: String(input.status || "Activa").trim(),
    expires_at: String(input.expiresAt || "").trim() || null,
    max_devices: Number(input.maxDevices || 1),
    playlist_url: String(input.playlistUrl || "").trim(),
    epg_url: String(input.epgUrl || "").trim(),
    updated_at: nowIso()
  };
}




function addMonthsToDateString(dateString, months) {
  const count = Math.max(1, Math.min(Number(months || 1), 24));
  const today = new Date();
  const baseText = String(dateString || "").slice(0, 10);
  const base = baseText && !isExpired(baseText)
    ? new Date(`${baseText}T00:00:00.000Z`)
    : new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()));

  base.setUTCMonth(base.getUTCMonth() + count);
  return base.toISOString().slice(0, 10);
}

function cleanResellerMonths(value) {
  const months = Number(value || 1);
  if (!Number.isFinite(months)) return 1;
  return Math.max(1, Math.min(Math.floor(months), 24));
}

function generateResellerAccessKey() {
  return crypto.randomBytes(18).toString("hex");
}

async function generateUniqueActivationCode() {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const code = String(crypto.randomInt(100000, 999999));

    const { data, error } = await supabase
      .from("clients")
      .select("activation_code")
      .eq("activation_code", code)
      .maybeSingle();

    if (error) throw error;
    if (!data) return code;
  }

  throw new Error("No se pudo generar código único.");
}

function resellerClientToApi(row) {
  return {
    customerName: row.customer_name || "",
    customerPhone: row.customer_phone || "",
    activationCode: row.activation_code || "",
    status: row.status || "Activa",
    expiresAt: row.expires_at || "",
    maxDevices: Number(row.max_devices || 1),
    deviceCount: Number(row.device_count || 0),
    createdAt: row.created_at || "",
    updatedAt: row.updated_at || ""
  };
}

function dbResellerToApi(row) {
  return {
    id: row.id,
    name: row.name || "",
    username: row.username || "",
    accessKey: row.access_key || "",
    credits: Number(row.credits || 0),
    active: Boolean(row.active),
    createdAt: row.created_at || "",
    updatedAt: row.updated_at || ""
  };
}

async function requireReseller(req, res, next) {
  if (!requireDb(res)) return;

  try {
    const key =
      req.headers["x-reseller-key"] ||
      req.query.key ||
      req.body?.resellerKey;

    if (!key) {
      return res.status(401).json({
        success: false,
        message: "Falta clave de revendedor."
      });
    }

    const { data, error } = await supabase
      .from("resellers")
      .select("*")
      .eq("access_key", String(key).trim())
      .eq("active", true)
      .maybeSingle();

    if (error) throw error;

    if (!data) {
      return res.status(401).json({
        success: false,
        message: "Revendedor no autorizado."
      });
    }

    req.reseller = data;
    next();
  } catch (error) {
    console.error("Reseller auth error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo validar revendedor.",
      error: error.message
    });
  }
}

function resellerDefaultPlaylistUrl() {
  return process.env.RESELLER_DEFAULT_PLAYLIST_URL ||
    process.env.GITHUB_GIST_RAW_URL ||
    "";
}

function resellerDefaultEpgUrl() {
  return process.env.RESELLER_DEFAULT_EPG_URL || "";
}


function streamUrlHash(value) {
  return crypto
    .createHash("sha256")
    .update(String(value || "").trim())
    .digest("hex");
}

function isBrokenLinkProblem(problemType, playerError) {
  const text = String(`${problemType || ""} ${playerError || ""}`)
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();

  return text.includes("enlace caido") ||
    text.includes("contenido no disponible") ||
    text.includes("source error") ||
    text.includes("source") ||
    text.includes("404") ||
    text.includes("403") ||
    text.includes("not found");
}

async function saveBrokenLinkReport(body = {}) {
  try {
    if (!supabase) return;

    const activationCode = normalizeCode(body.activationCode);
    const streamUrl = String(body.streamUrl || "").trim();

    if (!activationCode || !streamUrl) return;

    const hash = streamUrlHash(streamUrl);
    const now = new Date().toISOString();

    const row = {
      activation_code: activationCode,
      stream_url_hash: hash,
      stream_url_masked: maskUrl(streamUrl),
      channel_name: String(body.channelName || "").trim(),
      category: String(body.category || "").trim(),
      problem_type: String(body.problemType || "").trim(),
      player_error: String(body.playerError || "").trim(),
      last_reported_at: now,
      status: "Pendiente"
    };

    const { data: existing, error: existingError } = await supabase
      .from("broken_links")
      .select("id, report_count")
      .eq("activation_code", activationCode)
      .eq("stream_url_hash", hash)
      .maybeSingle();

    if (existingError) throw existingError;

    if (existing) {
      const { error } = await supabase
        .from("broken_links")
        .update({
          ...row,
          report_count: Number(existing.report_count || 1) + 1
        })
        .eq("id", existing.id);

      if (error) throw error;
      return;
    }

    const { error } = await supabase
      .from("broken_links")
      .insert({
        ...row,
        first_reported_at: now,
        report_count: 1
      });

    if (error) throw error;
  } catch (error) {
    console.error("Broken link global save error:", error);
  }
}


function dbReportToApi(row) {
  return {
    id: row.id,
    createdAt: row.created_at,
    status: row.status,
    channelName: row.channel_name,
    category: row.category,
    problemType: row.problem_type,
    streamUrlMasked: row.stream_url_masked,
    customerName: row.customer_name,
    activationCode: row.activation_code,
    deviceCode: row.device_code,
    appVersion: row.app_version,
    androidVersion: row.android_version,
    deviceModel: row.device_model,
    playerError: row.player_error,
    internalComment: row.internal_comment
  };
}

function groupReportsByChannel(reports) {
  const map = new Map();

  for (const report of reports) {
    const key = `${report.channelName || "Sin nombre"}|${report.category || ""}`;

    if (!map.has(key)) {
      map.set(key, {
        channelName: report.channelName || "Sin nombre",
        category: report.category || "",
        total: 0,
        pending: 0,
        lastReportedAt: report.createdAt,
        statuses: {}
      });
    }

    const item = map.get(key);
    item.total += 1;

    if (report.status === "Pendiente") {
      item.pending += 1;
    }

    item.statuses[report.status] = (item.statuses[report.status] || 0) + 1;

    if (report.createdAt > item.lastReportedAt) {
      item.lastReportedAt = report.createdAt;
    }
  }

  return Array.from(map.values()).sort((a, b) => b.total - a.total);
}

async function getDeviceRows(activationCode) {
  const { data, error } = await supabase
    .from("devices")
    .select("*")
    .eq("activation_code", activationCode)
    .order("last_seen_at", { ascending: false });

  if (error) throw error;
  return data || [];
}

function buildDeviceInfoUpdate(input = {}) {
  return {
    device_name: String(input.deviceName || "").trim(),
    manufacturer: String(input.manufacturer || "").trim(),
    model: String(input.model || "").trim(),
    brand: String(input.brand || "").trim(),
    android_version: String(input.androidVersion || "").trim(),
    sdk_int: Number(input.sdkInt || 0),
    platform: String(input.platform || "android").trim() || "android"
  };
}

function dbDeviceToApi(row) {
  return {
    id: row.id,
    activationCode: row.activation_code || "",
    deviceCode: row.device_code || "",
    appVersion: row.app_version || "",
    createdAt: row.created_at || "",
    lastSeenAt: row.last_seen_at || "",
    blocked: Boolean(row.blocked),
    nickname: row.nickname || "",
    blockedReason: row.blocked_reason || "",
    deviceName: row.device_name || "",
    manufacturer: row.manufacturer || "",
    model: row.model || "",
    brand: row.brand || "",
    androidVersion: row.android_version || "",
    sdkInt: Number(row.sdk_int || 0),
    platform: row.platform || "android"
  };
}

app.get("/", (req, res) => {
  if (req.query.health !== "1") return res.redirect("/admin");

  res.json({
    name: "StoreTD Play Backend",
    status: "ok",
    version: "2.0.0",
    database: isDatabaseConfigured() ? "supabase" : "not_configured"
  });
});

app.get("/health", async (req, res) => {
  if (!requireDb(res)) return;

  const [{ count: clientsCount }, { count: reportsCount }] = await Promise.all([
    supabase.from("clients").select("*", { count: "exact", head: true }),
    supabase.from("reports").select("*", { count: "exact", head: true })
  ]);

  res.json({
    status: "ok",
    clients: clientsCount || 0,
    reports: reportsCount || 0,
    version: "2.0.0",
    database: "supabase"
  });
});



app.get("/reseller", (req, res) => {
  res.sendFile(path.join(__dirname, "..", "public", "reseller.html"));
});

app.get("/admin/resellers", (req, res) => {
  res.sendFile(path.join(__dirname, "..", "public", "resellers.html"));
});

app.get("/admin/api/resellers", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const { data, error } = await supabase
      .from("resellers")
      .select("*")
      .order("created_at", { ascending: false });

    if (error) throw error;

    res.json({
      success: true,
      resellers: (data || []).map(dbResellerToApi)
    });
  } catch (error) {
    console.error("Admin resellers list error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudieron cargar revendedores.",
      error: error.message
    });
  }
});

app.post("/admin/api/resellers", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const name = String(req.body?.name || "").trim();
    const username = String(req.body?.username || "").trim();
    const credits = Math.max(0, Number(req.body?.credits || 0));
    const accessKey = String(req.body?.accessKey || "").trim() || generateResellerAccessKey();

    if (!name) {
      return res.status(400).json({
        success: false,
        message: "Falta nombre del revendedor."
      });
    }

    const { data, error } = await supabase
      .from("resellers")
      .insert({
        name,
        username: username || null,
        access_key: accessKey,
        credits,
        active: true,
        updated_at: nowIso()
      })
      .select()
      .single();

    if (error) throw error;

    if (credits > 0) {
      await supabase.from("reseller_credit_movements").insert({
        reseller_id: data.id,
        amount: credits,
        reason: "admin_initial_credit",
        note: "Créditos iniciales"
      });
    }

    res.json({
      success: true,
      message: "Revendedor creado.",
      reseller: dbResellerToApi(data)
    });
  } catch (error) {
    console.error("Admin create reseller error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo crear revendedor.",
      error: error.message
    });
  }
});

app.put("/admin/api/resellers/:id", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const id = String(req.params.id || "").trim();

    const updates = {
      updated_at: nowIso()
    };

    if (typeof req.body?.name !== "undefined") {
      updates.name = String(req.body.name || "").trim();
    }

    if (typeof req.body?.username !== "undefined") {
      updates.username = String(req.body.username || "").trim() || null;
    }

    if (typeof req.body?.active !== "undefined") {
      updates.active = Boolean(req.body.active);
    }

    const { data, error } = await supabase
      .from("resellers")
      .update(updates)
      .eq("id", id)
      .select()
      .maybeSingle();

    if (error) throw error;

    if (!data) {
      return res.status(404).json({
        success: false,
        message: "Revendedor no encontrado."
      });
    }

    res.json({
      success: true,
      reseller: dbResellerToApi(data)
    });
  } catch (error) {
    console.error("Admin update reseller error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo actualizar revendedor.",
      error: error.message
    });
  }
});

app.post("/admin/api/resellers/:id/credits", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const id = String(req.params.id || "").trim();
    const amount = Number(req.body?.amount || 0);
    const note = String(req.body?.note || "").trim();

    if (!Number.isFinite(amount) || amount === 0) {
      return res.status(400).json({
        success: false,
        message: "Cantidad inválida."
      });
    }

    const { data: reseller, error: resellerError } = await supabase
      .from("resellers")
      .select("*")
      .eq("id", id)
      .maybeSingle();

    if (resellerError) throw resellerError;

    if (!reseller) {
      return res.status(404).json({
        success: false,
        message: "Revendedor no encontrado."
      });
    }

    const nextCredits = Math.max(0, Number(reseller.credits || 0) + amount);

    const { data, error } = await supabase
      .from("resellers")
      .update({
        credits: nextCredits,
        updated_at: nowIso()
      })
      .eq("id", id)
      .select()
      .single();

    if (error) throw error;

    await supabase.from("reseller_credit_movements").insert({
      reseller_id: id,
      amount,
      reason: "admin_credit_adjustment",
      note
    });

    res.json({
      success: true,
      message: "Créditos actualizados.",
      reseller: dbResellerToApi(data)
    });
  } catch (error) {
    console.error("Admin reseller credits error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudieron actualizar créditos.",
      error: error.message
    });
  }
});

app.get("/admin/api/reseller-requests", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const { data, error } = await supabase
      .from("reseller_requests")
      .select("*, resellers(name, username)")
      .order("created_at", { ascending: false })
      .limit(500);

    if (error) throw error;

    res.json({
      success: true,
      requests: (data || []).map((item) => ({
        id: item.id,
        resellerName: item.resellers?.name || "",
        resellerUsername: item.resellers?.username || "",
        activationCode: item.activation_code || "",
        customerName: item.customer_name || "",
        requestType: item.request_type || "",
        contentTitle: item.content_title || "",
        message: item.message || "",
        status: item.status || "Pendiente",
        adminNote: item.admin_note || "",
        createdAt: item.created_at || ""
      }))
    });
  } catch (error) {
    console.error("Admin reseller requests error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudieron cargar pedidos.",
      error: error.message
    });
  }
});

app.put("/admin/api/reseller-requests/:id", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const id = String(req.params.id || "").trim();

    const { data, error } = await supabase
      .from("reseller_requests")
      .update({
        status: String(req.body?.status || "Pendiente"),
        admin_note: String(req.body?.adminNote || ""),
        updated_at: nowIso()
      })
      .eq("id", id)
      .select()
      .maybeSingle();

    if (error) throw error;

    res.json({
      success: true,
      request: data
    });
  } catch (error) {
    console.error("Admin update reseller request error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo actualizar pedido.",
      error: error.message
    });
  }
});

app.get("/reseller/api/me", requireReseller, async (req, res) => {
  res.json({
    success: true,
    reseller: dbResellerToApi(req.reseller)
  });
});


app.get("/reseller/api/credit-movements", requireReseller, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const { data, error } = await supabase
      .from("reseller_credit_movements")
      .select("*")
      .eq("reseller_id", req.reseller.id)
      .order("created_at", { ascending: false })
      .limit(300);

    if (error) throw error;

    res.json({
      success: true,
      movements: (data || []).map((item) => ({
        id: item.id,
        amount: Number(item.amount || 0),
        reason: item.reason || "",
        activationCode: item.activation_code || "",
        note: item.note || "",
        createdAt: item.created_at || ""
      }))
    });
  } catch (error) {
    console.error("Reseller credit movements error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo cargar historial de créditos.",
      error: error.message
    });
  }
});

app.get("/reseller/api/clients", requireReseller, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const { data, error } = await supabase
      .from("clients")
      .select("*")
      .eq("reseller_id", req.reseller.id)
      .order("created_at", { ascending: false });

    if (error) throw error;

    const clients = [];

    for (const row of data || []) {
      const code = normalizeCode(row.activation_code);
      const devices = await getDeviceRows(code);

      clients.push({
        ...resellerClientToApi(row),
        activationCode: code,
        deviceCount: devices.length,
        deviceLimit: Number(row.max_devices || 6),
        devices: devices.map(dbDeviceToApi)
      });
    }

    res.json({
      success: true,
      clients
    });
  } catch (error) {
    console.error("Reseller clients error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudieron cargar clientes.",
      error: error.message
    });
  }
});


function enqueueXtreamSyncForClient(activationCode) {
  const code = normalizeCode(activationCode);

  if (!code) return;

  try {
    const { spawn } = require("child_process");
    const fs = require("fs");
    const path = require("path");

    const backendRoot = path.join(__dirname, "..");
    const logsDir = path.join(backendRoot, "logs");
    fs.mkdirSync(logsDir, { recursive: true });

    const logFile = path.join(logsDir, `sync_xtream_${code}.log`);
    const out = fs.openSync(logFile, "a");

    fs.writeSync(
      out,
      `\n============================================\nAUTO SYNC NUEVO CLIENTE: ${code}\nFecha: ${new Date().toISOString()}\n============================================\n`
    );

    const script = path.join(backendRoot, "scripts", "sync_xtream_movies_series.js");

    const child = spawn(process.execPath, [script, code], {
      cwd: backendRoot,
      detached: true,
      stdio: ["ignore", out, out]
    });

    child.unref();

    console.log("Auto sync iniciado para cliente:", code);
  } catch (error) {
    console.error("No se pudo iniciar auto sync para cliente:", code, error);
  }
}


app.post("/reseller/api/clients", requireReseller, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const months = cleanResellerMonths(req.body?.months || 1);
    const reseller = req.reseller;
    const playlistUrl = resellerDefaultPlaylistUrl();
    const epgUrl = resellerDefaultEpgUrl();

    if (!playlistUrl) {
      return res.status(500).json({
        success: false,
        message: "Lista por defecto de revendedor no configurada."
      });
    }

    if (Number(reseller.credits || 0) < months) {
      return res.status(400).json({
        success: false,
        message: "Créditos insuficientes."
      });
    }

    const activationCode = normalizeCode(req.body?.activationCode || await generateUniqueActivationCode());
    const expiresAt = addMonthsToDateString("", months);

    const nextCredits = Number(reseller.credits || 0) - months;

    const { data: creditData, error: creditError } = await supabase
      .from("resellers")
      .update({
        credits: nextCredits,
        updated_at: nowIso()
      })
      .eq("id", reseller.id)
      .gte("credits", months)
      .select()
      .maybeSingle();

    if (creditError) throw creditError;

    if (!creditData) {
      return res.status(400).json({
        success: false,
        message: "Créditos insuficientes."
      });
    }

    const clientPayload = {
      ...apiClientToDb({
        customerName: req.body?.customerName || "Cliente",
        customerPhone: req.body?.customerPhone || "",
        activationCode,
        status: "Activa",
        expiresAt,
        maxDevices: 6,
        playlistUrl,
        epgUrl
      }),
      reseller_id: reseller.id,
      created_at: nowIso()
    };

    const { data: client, error: clientError } = await supabase
      .from("clients")
      .insert(clientPayload)
      .select()
      .single();

    if (clientError) {
      await supabase
        .from("resellers")
        .update({
          credits: Number(creditData.credits || 0) + months,
          updated_at: nowIso()
        })
        .eq("id", reseller.id);

      throw clientError;
    }

    await supabase.from("reseller_credit_movements").insert({
      reseller_id: reseller.id,
      amount: -months,
      reason: "client_created",
      activation_code: activationCode,
      note: `${months} mes(es)`
    });

    enqueueXtreamSyncForClient(activationCode);

    res.json({
      success: true,
      message: "Cliente creado.",
      credits: nextCredits,
      client: resellerClientToApi(client)
    });
  } catch (error) {
    console.error("Reseller create client error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo crear cliente.",
      error: error.message
    });
  }
});


app.put("/reseller/api/clients/:code/phone", requireReseller, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const code = normalizeCode(req.params.code);
    const customerPhone = String(req.body?.customerPhone || "")
      .replace(/[^0-9]/g, "")
      .trim();

    const { data, error } = await supabase
      .from("clients")
      .update({
        customer_phone: customerPhone,
        updated_at: nowIso()
      })
      .eq("activation_code", code)
      .eq("reseller_id", req.reseller.id)
      .select()
      .maybeSingle();

    if (error) throw error;

    if (!data) {
      return res.status(404).json({
        success: false,
        message: "Cliente no encontrado."
      });
    }

    res.json({
      success: true,
      message: "Teléfono actualizado.",
      client: resellerClientToApi(data)
    });
  } catch (error) {
    console.error("Reseller update client phone error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo actualizar teléfono.",
      error: error.message
    });
  }
});


app.post("/reseller/api/clients/:code/renew", requireReseller, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const code = normalizeCode(req.params.code);
    const months = cleanResellerMonths(req.body?.months || 1);
    const reseller = req.reseller;

    if (Number(reseller.credits || 0) < months) {
      return res.status(400).json({
        success: false,
        message: "Créditos insuficientes."
      });
    }

    const { data: client, error: clientError } = await supabase
      .from("clients")
      .select("*")
      .eq("activation_code", code)
      .eq("reseller_id", reseller.id)
      .maybeSingle();

    if (clientError) throw clientError;

    if (!client) {
      return res.status(404).json({
        success: false,
        message: "Cliente no encontrado."
      });
    }

    const expiresAt = addMonthsToDateString(client.expires_at, months);
    const nextCredits = Number(reseller.credits || 0) - months;

    const { data: creditData, error: creditError } = await supabase
      .from("resellers")
      .update({
        credits: nextCredits,
        updated_at: nowIso()
      })
      .eq("id", reseller.id)
      .gte("credits", months)
      .select()
      .maybeSingle();

    if (creditError) throw creditError;

    if (!creditData) {
      return res.status(400).json({
        success: false,
        message: "Créditos insuficientes."
      });
    }

    const { data: updated, error: updateError } = await supabase
      .from("clients")
      .update({
        expires_at: expiresAt,
        status: "Activa",
        updated_at: nowIso()
      })
      .eq("activation_code", code)
      .eq("reseller_id", reseller.id)
      .select()
      .single();

    if (updateError) {
      await supabase
        .from("resellers")
        .update({
          credits: Number(creditData.credits || 0) + months,
          updated_at: nowIso()
        })
        .eq("id", reseller.id);

      throw updateError;
    }

    await supabase.from("reseller_credit_movements").insert({
      reseller_id: reseller.id,
      amount: -months,
      reason: "client_renewed",
      activation_code: code,
      note: `${months} mes(es)`
    });

    res.json({
      success: true,
      message: "Cliente renovado.",
      credits: nextCredits,
      client: resellerClientToApi(updated)
    });
  } catch (error) {
    console.error("Reseller renew client error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo renovar cliente.",
      error: error.message
    });
  }
});


app.get("/reseller/api/requests", requireReseller, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const { data, error } = await supabase
      .from("reseller_requests")
      .select("*")
      .eq("reseller_id", req.reseller.id)
      .order("created_at", { ascending: false })
      .limit(300);

    if (error) throw error;

    res.json({
      success: true,
      requests: (data || []).map((item) => ({
        id: item.id,
        activationCode: item.activation_code || "",
        customerName: item.customer_name || "",
        requestType: item.request_type || "",
        contentTitle: item.content_title || "",
        message: item.message || "",
        status: item.status || "Pendiente",
        adminNote: item.admin_note || "",
        createdAt: item.created_at || "",
        updatedAt: item.updated_at || ""
      }))
    });
  } catch (error) {
    console.error("Reseller own requests error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudieron cargar tus pedidos.",
      error: error.message
    });
  }
});

app.post("/reseller/api/requests", requireReseller, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const requestType = String(req.body?.requestType || "").trim();
    const message = String(req.body?.message || "").trim();

    if (!requestType || !message) {
      return res.status(400).json({
        success: false,
        message: "Falta tipo o mensaje."
      });
    }

    const { data, error } = await supabase
      .from("reseller_requests")
      .insert({
        reseller_id: req.reseller.id,
        activation_code: normalizeCode(req.body?.activationCode || ""),
        customer_name: String(req.body?.customerName || "").trim(),
        request_type: requestType,
        content_title: String(req.body?.contentTitle || "").trim(),
        message,
        status: "Pendiente",
        updated_at: nowIso()
      })
      .select()
      .single();

    if (error) throw error;

    res.json({
      success: true,
      message: "Pedido enviado.",
      request: data
    });
  } catch (error) {
    console.error("Reseller request error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo enviar pedido.",
      error: error.message
    });
  }
});




app.get(["/smartone.m3u", "/smartone-final.m3u", "/smartone-v2.m3u"], async (req, res) => {
  try {
    const useXtreamSmartone =
      String(process.env.CONTENT_SOURCE_MODE || "").trim().toLowerCase() === "xtream" ||
      String(process.env.SMARTONE_SOURCE_MODE || "").trim().toLowerCase() === "xtream";

    if (useXtreamSmartone) {
      const xtreamM3u = await buildSmartoneXtreamM3u();

      if (!xtreamM3u) {
        throw new Error("Smartone Xtream no disponible.");
      }

      res.setHeader("Content-Type", "audio/x-mpegurl; charset=utf-8");
      res.setHeader("Content-Disposition", 'inline; filename="smartone.m3u"');
      res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0, s-maxage=0");
      res.setHeader("Pragma", "no-cache");
      res.setHeader("Expires", "0");
      res.setHeader("Surrogate-Control", "no-store");
      res.setHeader("X-StoreTD-Smartone-Mode", xtreamM3u.sourceMode || "xtream-live-movies");
      res.setHeader("X-StoreTD-Smartone-Generated-At", xtreamM3u.generatedAt);
      res.setHeader("X-StoreTD-Smartone-Live", String(xtreamM3u.counts?.live || 0));
      res.setHeader("X-StoreTD-Smartone-Movies", String(xtreamM3u.counts?.movies || 0));
      res.setHeader("X-StoreTD-Smartone-Series", String(xtreamM3u.counts?.series || 0));
      res.setHeader("X-StoreTD-Smartone-Series-Folders", String(xtreamM3u.counts?.seriesFolders || 0));
      res.setHeader("X-StoreTD-Smartone-Series-Limited", String(xtreamM3u.counts?.seriesLimited || false));
      res.setHeader("X-StoreTD-Smartone-Series-Max", String(xtreamM3u.counts?.seriesMaxEpisodes || 0));

      return res.send(xtreamM3u.content);
    }

    const gistConfig = requireGistConfig(res);
    if (!gistConfig) return;

    const rawUrl = `${gistConfig.rawUrl}?t=${Date.now()}`;

    const response = await fetch(rawUrl, {
      headers: {
        "User-Agent": "StoreTD-Play-Smartone-Live-Normalizer",
        "Cache-Control": "no-cache"
      }
    });

    if (!response.ok) {
      throw new Error(`No se pudo descargar lista principal. HTTP ${response.status}`);
    }

    const original = await response.text();
    const normalized = normalizeM3uOrderForSmartone(original);

    res.setHeader("Content-Type", "audio/x-mpegurl; charset=utf-8");
    res.setHeader("Content-Disposition", 'inline; filename="smartone.m3u"');
    res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0, s-maxage=0");
    res.setHeader("Pragma", "no-cache");
    res.setHeader("Expires", "0");
    res.setHeader("Surrogate-Control", "no-store");
    res.setHeader("X-StoreTD-Smartone-Mode", "live-normalized");
    res.setHeader("X-StoreTD-Smartone-Generated-At", new Date().toISOString());
    res.setHeader("X-StoreTD-Smartone-Live", String(normalized.counts?.live || 0));
    res.setHeader("X-StoreTD-Smartone-Movies", String(normalized.counts?.movies || 0));
    res.setHeader("X-StoreTD-Smartone-Series", String(normalized.counts?.series || 0));

    res.send(normalized.content);
  } catch (error) {
    console.error("Smartone proxy error:", error);
    res.status(500).send(`#EXTM3U\n# Error: ${error.message}\n`);
  }
});




app.get("/api/magma-live/catalog-version", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const code = normalizeCode(req.query.code);

    if (!code) {
      return res.status(400).json({
        success: false,
        message: "Falta código."
      });
    }

    const { data: client, error } = await supabase
      .from("clients")
      .select("*")
      .eq("activation_code", code)
      .maybeSingle();

    if (error) throw error;

    if (!client) {
      return res.status(404).json({
        success: false,
        message: "Código no encontrado."
      });
    }

    if (isExpired(client.expires_at)) {
      return res.status(403).json({
        success: false,
        message: "Código vencido."
      });
    }

    if (!magmaLiteIsEnabledForCode(code, client)) {
      return res.json({
        success: true,
        enabled: false,
        version: 0,
        updatedAt: null,
        reason: ""
      });
    }

    const current = readMagmaLiveCatalogVersion();

    res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    res.json({
      success: true,
      enabled: true,
      version: current.version,
      updatedAt: current.updatedAt,
      reason: current.reason
    });
  } catch (error) {
    console.error("Magma live catalog-version error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo consultar versión de catálogo.",
      error: error.message
    });
  }
});

app.post("/api/admin/magma-live/refresh-catalog-version", requireAdmin, (req, res) => {
  try {
    const reason = String(req.body?.reason || req.query.reason || "manual").trim() || "manual";
    const payload = writeMagmaLiveCatalogVersion(reason);

    res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    res.json({
      success: true,
      message: "Catálogo Magma Live marcado para actualización.",
      ...payload
    });
  } catch (error) {
    console.error("Magma live refresh-catalog-version error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo marcar actualización manual.",
      error: error.message
    });
  }
});


app.get("/api/app-update", (req, res) => {
  const currentVersionCode = Number(req.query.versionCode || 0);

  const latestVersionCode = Number(process.env.APP_LATEST_VERSION_CODE || 8);
  const latestVersionName = process.env.APP_LATEST_VERSION_NAME || "1.0.7";
  const apkUrl = process.env.APP_LATEST_APK_URL || "";
  const forceUpdate = String(process.env.APP_FORCE_UPDATE || "0") === "1";
  const changelog = process.env.APP_UPDATE_CHANGELOG ||
    "Mejoras de estabilidad, reproducción y correcciones generales.";

  const updateAvailable =
    latestVersionCode > 0 &&
    currentVersionCode > 0 &&
    latestVersionCode > currentVersionCode &&
    apkUrl.trim() !== "";

  res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
  res.json({
    success: true,
    updateAvailable,
    latestVersionCode,
    latestVersionName,
    apkUrl,
    forceUpdate,
    changelog
  });
});



app.get("/admin", (req, res) => {
  res.sendFile(path.join(__dirname, "..", "public", "admin.html"));
});



async function logDeviceEvent({
  activationCode = "",
  deviceCode = "",
  eventType = "",
  message = "",
  metadata = {}
}) {
  try {
    if (!supabase) return;

    await supabase
      .from("device_events")
      .insert({
        activation_code: normalizeCode(activationCode),
        device_code: String(deviceCode || ""),
        event_type: String(eventType || ""),
        message: String(message || ""),
        metadata: metadata || {}
      });
  } catch (error) {
    console.error("Device audit log error:", error);
  }
}

function dbDeviceEventToApi(row) {
  return {
    id: row.id,
    activationCode: row.activation_code || "",
    deviceCode: row.device_code || "",
    eventType: row.event_type || "",
    message: row.message || "",
    metadata: row.metadata || {},
    createdAt: row.created_at || ""
  };
}


app.post("/auth/status", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const { activationCode, deviceCode } = req.body || {};
    const normalizedCode = normalizeCode(activationCode);

    if (!normalizedCode) {
      return res.status(400).json({
        success: false,
        allowed: false,
        message: "Falta código de activación."
      });
    }

    const { data: client, error: clientError } = await supabase
      .from("clients")
      .select("*")
      .eq("activation_code", normalizedCode)
      .maybeSingle();

    if (clientError) throw clientError;

    if (!client) {
      return res.status(401).json({
        success: true,
        allowed: false,
        message: "Código de activación inválido."
      });
    }

    if (client.status === "Suspendida") {
      return res.status(403).json({
        success: true,
        allowed: false,
        message: "La cuenta está suspendida. Contacta a soporte."
      });
    }

    if (client.status === "Vencida" || isExpired(client.expires_at)) {
      return res.status(403).json({
        success: true,
        allowed: false,
        message: "La cuenta está vencida. Renueva el servicio para continuar."
      });
    }

    if (deviceCode) {
      const { data: device, error: deviceError } = await supabase
        .from("devices")
        .select("*")
        .eq("activation_code", normalizedCode)
        .eq("device_code", String(deviceCode))
        .maybeSingle();

      if (deviceError) throw deviceError;

      if (device && device.blocked) {
        await logDeviceEvent({
          activationCode: normalizedCode,
          deviceCode,
          eventType: "blocked_status_check",
          message: device.blocked_reason || "Dispositivo bloqueado detectado en validación.",
          metadata: {
            source: "auth_status"
          }
        });

        return res.status(403).json({
          success: true,
          allowed: false,
          message: device.blocked_reason || "Este dispositivo fue bloqueado. Contacta a soporte."
        });
      }

      if (device) {
        await supabase
          .from("devices")
          .update({
            last_seen_at: nowIso(),
            ...buildDeviceInfoUpdate(req.body || {})
          })
          .eq("activation_code", normalizedCode)
          .eq("device_code", String(deviceCode));
      }
    }

    res.json({
      success: true,
      allowed: true,
      message: "Cuenta autorizada.",
      client: dbClientToApi(client)
    });
  } catch (error) {
    console.error("Auth status error:", error);

    res.status(500).json({
      success: false,
      allowed: false,
      message: "No se pudo validar la cuenta."
    });
  }
});


app.post("/auth/activate", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const { customerName, activationCode, deviceCode, appVersion } = req.body || {};
    const normalizedCode = normalizeCode(activationCode);

    if (!customerName || !normalizedCode || !deviceCode) {
      return res.status(400).json({
        success: false,
        message: "Faltan datos para activar el dispositivo."
      });
    }

    const { data: client, error: clientError } = await supabase
      .from("clients")
      .select("*")
      .eq("activation_code", normalizedCode)
      .maybeSingle();

    if (clientError) throw clientError;

    if (!client) {
      return res.status(401).json({
        success: false,
        message: "Codigo de activacion invalido."
      });
    }

    if (client.status === "Suspendida") {
      return res.status(403).json({
        success: false,
        message: "La cuenta esta suspendida. Contacta a soporte."
      });
    }

    if (client.status === "Vencida" || isExpired(client.expires_at)) {
      return res.status(403).json({
        success: false,
        message: "La cuenta esta vencida. Renueva el servicio para continuar."
      });
    }

    const devices = await getDeviceRows(normalizedCode);
    const existingDevice = devices.find((item) => item.device_code === deviceCode);

    if (existingDevice && existingDevice.blocked) {
      await logDeviceEvent({
        activationCode: normalizedCode,
        deviceCode,
        eventType: "blocked_activation_attempt",
        message: existingDevice.blocked_reason || "Intento de activación desde dispositivo bloqueado.",
        metadata: {
          appVersion,
          source: "auth_activate"
        }
      });

      return res.status(403).json({
        success: false,
        message: existingDevice.blocked_reason || "Este dispositivo fue bloqueado. Contacta a soporte."
      });
    }

    const alreadyRegistered = Boolean(existingDevice);
    const maxDevices = Number(client.max_devices || 1);

    if (!alreadyRegistered && devices.length >= maxDevices) {
      return res.status(403).json({
        success: false,
        message: "Limite de dispositivos alcanzado para esta cuenta."
      });
    }

    const { error: upsertError } = await supabase
      .from("devices")
      .upsert(
        {
          activation_code: normalizedCode,
          device_code: String(deviceCode),
          app_version: String(appVersion || ""),
          last_seen_at: nowIso()
        },
        { onConflict: "activation_code,device_code" }
      );

    if (upsertError) throw upsertError;

    const updatedDevices = await getDeviceRows(normalizedCode);

    return res.json({
      success: true,
      message: "Dispositivo activado correctamente.",
      customerName: client.customer_name || customerName,
      activationCode: normalizedCode,
      status: client.status,
      expiresAt: client.expires_at,
      playlistUrl: client.playlist_url || "",
      epgUrl: client.epg_url || "",
      maxDevices,
      deviceCount: updatedDevices.length,
      deviceCode,
      appVersion
    });
  } catch (error) {
    console.error("Activation error:", error);
    res.status(500).json({
      success: false,
      message: "Error interno al activar."
    });
  }
});

app.post("/reports/channel", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const body = req.body || {};

    const report = {
      id: "rep_" + Date.now() + "_" + Math.random().toString(16).slice(2),
      created_at: nowIso(),
      status: "Pendiente",
      channel_name: String(body.channelName || "Sin nombre").trim(),
      category: String(body.category || "").trim(),
      problem_type: String(body.problemType || "Otro problema").trim(),
      stream_url_masked: maskUrl(body.streamUrl || ""),
      customer_name: String(body.customerName || "").trim(),
      activation_code: normalizeCode(body.activationCode || ""),
      device_code: String(body.deviceCode || "").trim(),
      app_version: String(body.appVersion || "").trim(),
      android_version: String(body.androidVersion || "").trim(),
      device_model: String(body.deviceModel || "").trim(),
      player_error: String(body.playerError || "").trim(),
      internal_comment: ""
    };

    if (!report.channel_name) {
      return res.status(400).json({
        success: false,
        message: "Falta el nombre del canal."
      });
    }

    const { error } = await supabase.from("reports").insert(report);
    if (error) throw error;

    if (isBrokenLinkProblem(body.problemType, body.playerError)) {
      await saveBrokenLinkReport(body);
    }

    res.json({
      success: true,
      message: "Reporte enviado correctamente.",
      reportId: report.id
    });
  } catch (error) {
    console.error("Report error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo guardar el reporte."
    });
  }
});

app.get("/admin/api/stats", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  const { data: clients, error: clientsError } = await supabase
    .from("clients")
    .select("*");

  const { data: reportsRows, error: reportsError } = await supabase
    .from("reports")
    .select("*")
    .order("created_at", { ascending: false });

  if (clientsError || reportsError) {
    return res.status(500).json({
      success: false,
      message: "No se pudieron cargar estadisticas."
    });
  }

  const reports = (reportsRows || []).map(dbReportToApi);

  res.json({
    success: true,
    stats: {
      clientsTotal: clients.length,
      clientsActive: clients.filter((c) => c.status === "Activa").length,
      clientsTrial: clients.filter((c) => c.status === "Prueba").length,
      clientsSuspended: clients.filter((c) => c.status === "Suspendida").length,
      clientsExpired: clients.filter((c) => c.status === "Vencida" || isExpired(c.expires_at)).length,
      reportsTotal: reports.length,
      reportsPending: reports.filter((r) => r.status === "Pendiente").length,
      topReportedChannels: groupReportsByChannel(reports).slice(0, 10)
    }
  });
});

app.get("/admin/api/clients", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const { data: rows, error } = await supabase
      .from("clients")
      .select("*, resellers(name, username)")
      .order("customer_name", { ascending: true });

    if (error) throw error;

    const clients = [];

    for (const row of rows || []) {
      const code = normalizeCode(row.activation_code);
      const devices = await getDeviceRows(code);

      clients.push({
        ...dbClientToApi(row),
        activationCode: code,
        deviceCount: devices.length,
        devices: devices.map((item) => item.device_code),
        deviceDetails: devices.map(dbDeviceToApi),
        resellerId: row.reseller_id || "",
        resellerName: row.resellers?.name || "",
        resellerUsername: row.resellers?.username || ""
      });
    }

    res.json({
      success: true,
      clients
    });
  } catch (error) {
    console.error("Clients list error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudieron cargar clientes."
    });
  }
});

app.post("/admin/api/clients", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const client = apiClientToDb(req.body || {});

    if (!client.playlist_url) {
      client.playlist_url = resellerDefaultPlaylistUrl();
    }

    if (!client.epg_url) {
      client.epg_url = resellerDefaultEpgUrl();
    }


    if (!client.customer_name) {
      return res.status(400).json({
        success: false,
        message: "Falta el nombre del cliente."
      });
    }

    if (!client.activation_code) {
      return res.status(400).json({
        success: false,
        message: "Falta el codigo de activacion."
      });
    }

    const { data, error } = await supabase
      .from("clients")
      .insert(client)
      .select()
      .single();

    if (error) {
      if (String(error.message || "").includes("duplicate")) {
        return res.status(409).json({
          success: false,
          message: "Ya existe un cliente con ese codigo."
        });
      }

      throw error;
    }

    enqueueXtreamSyncForClient(data.activation_code);

    res.json({
      success: true,
      message: "Cliente creado.",
      client: dbClientToApi(data)
    });
  } catch (error) {
    console.error("Create client error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo crear cliente."
    });
  }
});

app.put("/admin/api/clients/:code", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const code = normalizeCode(req.params.code);
    const updated = apiClientToDb(req.body || {}, code);

    const { data, error } = await supabase
      .from("clients")
      .update(updated)
      .eq("activation_code", code)
      .select()
      .maybeSingle();

    if (error) throw error;

    if (!data) {
      return res.status(404).json({
        success: false,
        message: "Cliente no encontrado."
      });
    }

    res.json({
      success: true,
      message: "Cliente actualizado.",
      client: dbClientToApi(data)
    });
  } catch (error) {
    console.error("Update client error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo actualizar cliente."
    });
  }
});

app.delete("/admin/api/clients/:code", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const code = normalizeCode(req.params.code);

    await supabase.from("devices").delete().eq("activation_code", code);

    const { error } = await supabase
      .from("clients")
      .delete()
      .eq("activation_code", code);

    if (error) throw error;

    res.json({
      success: true,
      message: "Cliente eliminado."
    });
  } catch (error) {
    console.error("Delete client error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo eliminar cliente."
    });
  }
});

app.post("/admin/api/clients/:code/unlink-devices", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const code = normalizeCode(req.params.code);

    const { error } = await supabase
      .from("devices")
      .delete()
      .eq("activation_code", code);

    if (error) throw error;

    res.json({
      success: true,
      message: "Dispositivos desvinculados."
    });
  } catch (error) {
    console.error("Unlink devices error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudieron desvincular dispositivos."
    });
  }
});

app.get("/admin/api/reports", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const { data, error } = await supabase
      .from("reports")
      .select("*")
      .order("created_at", { ascending: false });

    if (error) throw error;

    const reports = (data || []).map(dbReportToApi);

    res.json({
      success: true,
      reports,
      grouped: groupReportsByChannel(reports)
    });
  } catch (error) {
    console.error("Reports list error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudieron cargar reportes."
    });
  }
});

app.put("/admin/api/reports/:id", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const id = String(req.params.id || "");

    const { data, error } = await supabase
      .from("reports")
      .update({
        status: String(req.body.status || "Pendiente"),
        internal_comment: String(req.body.internalComment || "")
      })
      .eq("id", id)
      .select()
      .maybeSingle();

    if (error) throw error;

    if (!data) {
      return res.status(404).json({
        success: false,
        message: "Reporte no encontrado."
      });
    }

    res.json({
      success: true,
      message: "Reporte actualizado.",
      report: dbReportToApi(data)
    });
  } catch (error) {
    console.error("Update report error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo actualizar reporte."
    });
  }
});

app.delete("/admin/api/reports/:id", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const id = String(req.params.id || "");

    const { error } = await supabase
      .from("reports")
      .delete()
      .eq("id", id);

    if (error) throw error;

    res.json({
      success: true,
      message: "Reporte eliminado."
    });
  } catch (error) {
    console.error("Delete report error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo eliminar reporte."
    });
  }
});


app.get("/app/config", async (req, res) => {
  try {
    const config = await getAppConfig();

    res.json({
      success: true,
      config
    });
  } catch (error) {
    console.error("App config error:", error);

    res.status(500).json({
      success: false,
      message: "No se pudo cargar la configuracion de la app."
    });
  }
});

app.get("/admin/config", (req, res) => {
  res.sendFile(path.join(__dirname, "..", "public", "app-config.html"));
});

app.get("/admin/api/app-config", requireAdmin, async (req, res) => {
  try {
    const config = await getAppConfig();

    res.json({
      success: true,
      config
    });
  } catch (error) {
    console.error("Admin app config get error:", error);

    res.status(500).json({
      success: false,
      message: "No se pudo cargar la configuracion."
    });
  }
});

app.put("/admin/api/app-config", requireAdmin, async (req, res) => {
  try {
    const config = await updateAppConfig(req.body || {});

    res.json({
      success: true,
      message: "Configuracion actualizada.",
      config
    });
  } catch (error) {
    console.error("Admin app config update error:", error);

    res.status(500).json({
      success: false,
      message: "No se pudo actualizar la configuracion."
    });
  }
});



let epgProxyCache = {
  xml: "",
  updatedAt: 0,
  sourceUrl: "",
  keywordsKey: ""
};

function normalizeEpgText(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
}

function parseEpgKeywords(value) {
  return String(value || "")
    .split(/[\n,;]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function getXmlAttribute(attrs, name) {
  const regex = new RegExp(name + '\\s*=\\s*"([^"]*)"', "i");
  const match = regex.exec(attrs || "");
  return match ? match[1] : "";
}

function stripTags(value) {
  return String(value || "").replace(/<[^>]+>/g, " ");
}

async function downloadTextWithLimit(url, maxBytes) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 65000);

  try {
    const response = await fetch(url, {
      signal: controller.signal,
      headers: {
        "User-Agent": "StoreTD-Play-EPG-Proxy",
        "Accept": "application/xml,text/xml,*/*"
      }
    });

    if (!response.ok) {
      throw new Error("HTTP " + response.status);
    }

    const contentLength = Number(response.headers.get("content-length") || 0);

    if (contentLength > maxBytes) {
      throw new Error("La EPG fuente es demasiado pesada para el proxy actual.");
    }

    const reader = response.body.getReader();
    const chunks = [];
    let total = 0;

    while (true) {
      const { done, value } = await reader.read();

      if (done) break;

      total += value.length;

      if (total > maxBytes) {
        throw new Error("La EPG fuente supera el limite permitido.");
      }

      chunks.push(Buffer.from(value));
    }

    return Buffer.concat(chunks).toString("utf8");
  } finally {
    clearTimeout(timeout);
  }
}

function filterXmlTv(xml, keywords) {
  const cleanKeywords = keywords
    .map(normalizeEpgText)
    .filter(Boolean);

  if (!cleanKeywords.length) {
    throw new Error("No hay palabras clave configuradas para filtrar EPG.");
  }

  const tvOpenMatch = xml.match(/<tv\b[^>]*>/i);
  const tvOpen = tvOpenMatch ? tvOpenMatch[0] : "<tv>";

  const selectedIds = new Set();
  const selectedChannelBlocks = [];

  const channelRegex = /<channel\s+([^>]*)>([\s\S]*?)<\/channel>/gi;
  let channelMatch;

  while ((channelMatch = channelRegex.exec(xml)) !== null) {
    const attrs = channelMatch[1];
    const body = channelMatch[2];
    const fullBlock = channelMatch[0];
    const id = getXmlAttribute(attrs, "id");

    if (!id) continue;

    const searchable = normalizeEpgText(id + " " + stripTags(body));
    const matches = cleanKeywords.some((keyword) => searchable.includes(keyword));

    if (matches) {
      selectedIds.add(id);
      selectedChannelBlocks.push(fullBlock);
    }

    if (selectedChannelBlocks.length >= 250) break;
  }

  if (!selectedIds.size) {
    throw new Error("La EPG fuente no tiene canales que coincidan con los filtros configurados.");
  }

  const selectedProgrammeBlocks = [];
  const programmeRegex = /<programme\s+([^>]*)>([\s\S]*?)<\/programme>/gi;
  let programmeMatch;

  while ((programmeMatch = programmeRegex.exec(xml)) !== null) {
    const attrs = programmeMatch[1];
    const channelId = getXmlAttribute(attrs, "channel");

    if (selectedIds.has(channelId)) {
      selectedProgrammeBlocks.push(programmeMatch[0]);
    }

    if (selectedProgrammeBlocks.length >= 2500) break;
  }

  const output = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    tvOpen,
    selectedChannelBlocks.join("\n"),
    selectedProgrammeBlocks.join("\n"),
    "</tv>"
  ].join("\n");

  return output;
}

app.get("/epg/proxy", async (req, res) => {
  try {
    const config = await getAppConfig();
    const sourceUrl = String(config.epgSourceUrl || "").trim();
    const keywords = parseEpgKeywords(config.epgFilterKeywords);
    const keywordsKey = keywords.join("|");
    const force = req.query.force === "1" && req.query.key === adminKey;
    const cacheTtlMs = 6 * 60 * 60 * 1000;
    const cacheValid =
      epgProxyCache.xml &&
      epgProxyCache.sourceUrl === sourceUrl &&
      epgProxyCache.keywordsKey === keywordsKey &&
      Date.now() - epgProxyCache.updatedAt < cacheTtlMs;

    if (!sourceUrl.startsWith("http://") && !sourceUrl.startsWith("https://")) {
      return res.status(400).json({
        success: false,
        message: "EPG source URL no configurada."
      });
    }

    if (!force && cacheValid) {
      res.setHeader("Content-Type", "application/xml; charset=utf-8");
      res.setHeader("X-StoreTD-EPG-Cache", "HIT");
      return res.send(epgProxyCache.xml);
    }

    const sourceXml = await downloadTextWithLimit(sourceUrl, 25 * 1024 * 1024);
    const filteredXml = filterXmlTv(sourceXml, keywords);

    epgProxyCache = {
      xml: filteredXml,
      updatedAt: Date.now(),
      sourceUrl,
      keywordsKey
    };

    res.setHeader("Content-Type", "application/xml; charset=utf-8");
    res.setHeader("X-StoreTD-EPG-Cache", "MISS");
    res.send(filteredXml);
  } catch (error) {
    console.error("EPG proxy error:", error);

    if (epgProxyCache.xml) {
      res.setHeader("Content-Type", "application/xml; charset=utf-8");
      res.setHeader("X-StoreTD-EPG-Cache", "STALE");
      return res.send(epgProxyCache.xml);
    }

    res.status(500).json({
      success: false,
      message: error.message || "No se pudo generar EPG proxy."
    });
  }
});

app.get("/admin/api/epg-proxy/refresh", requireAdmin, async (req, res) => {
  try {
    const config = await getAppConfig();
    const sourceUrl = String(config.epgSourceUrl || "").trim();
    const keywords = parseEpgKeywords(config.epgFilterKeywords);
    const sourceXml = await downloadTextWithLimit(sourceUrl, 25 * 1024 * 1024);
    const filteredXml = filterXmlTv(sourceXml, keywords);

    epgProxyCache = {
      xml: filteredXml,
      updatedAt: Date.now(),
      sourceUrl,
      keywordsKey: keywords.join("|")
    };

    res.json({
      success: true,
      message: "EPG proxy actualizada.",
      bytes: Buffer.byteLength(filteredXml),
      updatedAt: new Date(epgProxyCache.updatedAt).toISOString()
    });
  } catch (error) {
    console.error("EPG proxy refresh error:", error);

    res.status(500).json({
      success: false,
      message: error.message || "No se pudo actualizar EPG proxy."
    });
  }
});




let playlistProxyCache = new Map();

function normalizePlaylistText(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
}

function getM3uAttribute(line, name) {
  const regex = new RegExp(name + '="([^"]*)"', "i");
  const match = regex.exec(line || "");
  return match ? match[1] : "";
}

function getM3uName(line) {
  const index = String(line || "").lastIndexOf(",");
  if (index < 0) return "";
  return line.slice(index + 1).trim();
}

function isAdultEntry(entry) {
  const text = normalizePlaylistText(entry.name + " " + entry.group);
  return [
    "adult", "adulto", "xxx", "+18", "18+", "hot", "erotic", "erotico",
    "porn", "playboy"
  ].some((word) => text.includes(normalizePlaylistText(word)));
}

function getPlaylistEntryType(entry) {
  const nameText = normalizePlaylistText(entry.name);
  const groupText = normalizePlaylistText(entry.group);
  const text = normalizePlaylistText(entry.name + " " + entry.group);

  // Reglas fuertes por grupo:
  // TV | 01 Noticias          => live
  // TV | 06 Cine y Peliculas  => live, porque son canales lineales
  // TV | 07 Series TV         => live, porque son canales lineales
  // Peliculas | 2024          => movies
  // Series | Accion           => series

  if (
    groupText.startsWith("tv ") ||
    groupText.startsWith("tv |") ||
    groupText.startsWith("tv 0") ||
    groupText.startsWith("canales") ||
    groupText.includes("en vivo")
  ) {
    return "live";
  }

  if (
    groupText.startsWith("pelicula") ||
    groupText.startsWith("peliculas") ||
    groupText.startsWith("movie") ||
    groupText.startsWith("movies") ||
    groupText.startsWith("vod") ||
    groupText.startsWith("cine ")
  ) {
    return "movies";
  }

  if (
    groupText.startsWith("serie") ||
    groupText.startsWith("series") ||
    groupText.startsWith("temporada") ||
    groupText.startsWith("novela") ||
    groupText.startsWith("anime")
  ) {
    return "series";
  }

  const looksLikeEpisode =
    /\bs[0-9]{1,2}\s*e[0-9]{1,3}\b/i.test(nameText) ||
    /\b[0-9]{1,2}x[0-9]{1,3}\b/i.test(nameText);

  if (looksLikeEpisode) {
    return "series";
  }

  const movieWords = [
    "pelicula", "peliculas", "movie", "movies", "film", "films",
    "estreno", "estrenos", "accion", "terror", "comedia", "drama"
  ];

  const seriesWords = [
    "serie", "series", "season", "episode", "episodio", "capitulo"
  ];

  if (seriesWords.some((word) => groupText.includes(normalizePlaylistText(word)))) {
    return "series";
  }

  if (movieWords.some((word) => groupText.includes(normalizePlaylistText(word)))) {
    return "movies";
  }

  return "live";
}

function parseM3uEntries(m3uText) {
  const lines = String(m3uText || "")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

  const entries = [];

  for (let i = 0; i < lines.length; i++) {
    const info = lines[i];

    if (!info.startsWith("#EXTINF")) continue;

    const url = lines[i + 1] || "";

    if (!url || url.startsWith("#")) continue;

    entries.push({
      info,
      url,
      name: getM3uName(info),
      group: getM3uAttribute(info, "group-title"),
      tvgId: getM3uAttribute(info, "tvg-id"),
      logo: getM3uAttribute(info, "tvg-logo")
    });
  }

  return entries;
}

function buildM3u(entries) {
  return "#EXTM3U\n" + entries
    .map((entry) => entry.info + "\n" + entry.url)
    .join("\n");
}

async function findPlaylistClient(activationCode) {
  const code = String(activationCode || "").trim();

  if (!code) return null;

  if (typeof supabase !== "undefined" && supabase) {
    const { data, error } = await supabase
      .from("clients")
      .select("*")
      .eq("activation_code", code)
      .maybeSingle();

    if (!error && data) {
      return {
        activationCode: data.activation_code,
        status: data.status,
        playlistUrl: data.playlist_url
      };
    }
  }

  if (typeof clients !== "undefined" && Array.isArray(clients)) {
    return clients.find((client) => client.activationCode === code) || null;
  }

  return null;
}

async function downloadPlaylistTextWithLimit(url, maxBytes) {
  if (typeof downloadTextWithLimit === "function") {
    return downloadTextWithLimit(url, maxBytes);
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 65000);

  try {
    const response = await fetch(url, {
      signal: controller.signal,
      headers: {
        "User-Agent": "StoreTD-Play-Playlist-Proxy",
        "Accept": "application/x-mpegURL,text/plain,*/*"
      }
    });

    if (!response.ok) {
      throw new Error("HTTP " + response.status);
    }

    const contentLength = Number(response.headers.get("content-length") || 0);

    if (contentLength > maxBytes) {
      throw new Error("La playlist fuente es demasiado pesada.");
    }

    const reader = response.body.getReader();
    const chunks = [];
    let total = 0;

    while (true) {
      const { done, value } = await reader.read();

      if (done) break;

      total += value.length;

      if (total > maxBytes) {
        throw new Error("La playlist supera el límite permitido.");
      }

      chunks.push(Buffer.from(value));
    }

    return Buffer.concat(chunks).toString("utf8");
  } finally {
    clearTimeout(timeout);
  }
}


function getXtreamLiveApiConfig() {
  const rawUrl = String(process.env.XTREAM_LIVE_API_URL || "").trim();

  if (!rawUrl) return null;

  try {
    const parsed = new URL(rawUrl);
    const username = parsed.searchParams.get("username") || "";
    const password = parsed.searchParams.get("password") || "";

    if (!username || !password) return null;

    return {
      rawUrl,
      baseUrl: `${parsed.protocol}//${parsed.host}`,
      username,
      password
    };
  } catch (error) {
    console.error("XTREAM_LIVE_API_URL inválida:", error.message);
    return null;
  }
}

function liveSourceMode() {
  return String(process.env.CONTENT_LIVE_SOURCE_MODE || "m3u").trim().toLowerCase();
}

function shouldUseXtreamLiveSource(type) {
  return liveSourceMode() === "xtream" && String(type || "live").trim().toLowerCase() === "live";
}

function xmlAttrEscape(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function m3uLineEscape(value) {
  return String(value || "").replace(/\r/g, " ").replace(/\n/g, " ").trim();
}

async function fetchXtreamJsonByAction(config, action) {
  const url = new URL(config.rawUrl);
  url.searchParams.set("action", action);

  const response = await fetch(url.toString(), {
    headers: {
      "User-Agent": "StoreTD-Play-Backend/1.0"
    }
  });

  if (!response.ok) {
    throw new Error(`Xtream ${action} HTTP ${response.status}`);
  }

  return response.json();
}

function buildXtreamLiveStreamUrl(config, streamId) {
  return `${config.baseUrl}/live/${encodeURIComponent(config.username)}/${encodeURIComponent(config.password)}/${encodeURIComponent(String(streamId))}.m3u8`;
}

async function buildXtreamLiveM3u() {
  const config = getXtreamLiveApiConfig();

  if (!config) {
    throw new Error("XTREAM_LIVE_API_URL no configurada.");
  }

  const [categoriesRaw, streamsRaw] = await Promise.all([
    fetchXtreamJsonByAction(config, "get_live_categories").catch(() => []),
    fetchXtreamJsonByAction(config, "get_live_streams")
  ]);

  const categories = Array.isArray(categoriesRaw) ? categoriesRaw : [];
  const streams = Array.isArray(streamsRaw) ? streamsRaw : [];

  const categoryMap = new Map();

  for (const category of categories) {
    const id = String(category.category_id || category.id || "").trim();
    const name = String(category.category_name || category.name || "").trim();

    if (id && name) {
      categoryMap.set(id, name);
    }
  }

  const lines = ["#EXTM3U"];

  for (const stream of streams) {
    const streamId = stream.stream_id || stream.id;
    if (!streamId) continue;

    const name = m3uLineEscape(stream.name || stream.title || `Canal ${streamId}`);
    const logo = m3uLineEscape(stream.stream_icon || stream.logo || "");
    const categoryId = String(stream.category_id || "").trim();
    const group = m3uLineEscape(categoryMap.get(categoryId) || stream.category_name || "TV en vivo");
    const tvgId = m3uLineEscape(stream.epg_channel_id || stream.tvg_id || "");

    const streamUrl = buildXtreamLiveStreamUrl(config, streamId);

    lines.push(
      `#EXTINF:-1 tvg-type="live" tvg-id="${xmlAttrEscape(tvgId)}" tvg-name="${xmlAttrEscape(name)}" tvg-logo="${xmlAttrEscape(logo)}" group-title="${xmlAttrEscape(group)}",${name}`
    );
    lines.push(streamUrl);
  }

  return lines.join("\n") + "\n";
}

async function sendXtreamLiveM3uForProxy(req, res) {
  const cacheKey = "xtream-live|" + String(process.env.XTREAM_LIVE_API_URL || "").trim();
  const cached = playlistProxyCache.get(cacheKey);
  const now = Date.now();
  const ttlMs = Number(process.env.XTREAM_LIVE_CACHE_TTL_MS || 300000);

  if (cached && now - cached.createdAt < ttlMs) {
    res.setHeader("Content-Type", "application/vnd.apple.mpegurl; charset=utf-8");
    return res.send(cached.m3u);
  }

  const m3u = await buildXtreamLiveM3u();

  playlistProxyCache.set(cacheKey, {
    createdAt: now,
    m3u
  });

  res.setHeader("Content-Type", "application/vnd.apple.mpegurl; charset=utf-8");
  return res.send(m3u);
}


app.get("/playlist/proxy", async (req, res) => {
  try {
    const activationCode = String(req.query.code || "").trim();
    const type = String(req.query.type || "live").trim().toLowerCase();
    const force = req.query.force === "1";
    const allowedTypes = new Set(["live", "movies", "series", "all"]);

    if (!activationCode) {
      return res.status(400).send("#EXTM3U\n# Error: falta código de activación");
    }

    if (!allowedTypes.has(type)) {
      return res.status(400).send("#EXTM3U\n# Error: tipo inválido");
    }

    const client = await findPlaylistClient(activationCode);

    if (!client || !client.playlistUrl) {
      return res.status(404).send("#EXTM3U\n# Error: cliente sin playlist asignada");
    }

    if (String(client.status || "").toLowerCase() !== "activa") {
      return res.status(403).send("#EXTM3U\n# Error: cliente no activo");
    }

    if (shouldUseXtreamLiveSource(type)) {
      try {
        return await sendXtreamLiveM3uForProxy(req, res);
      } catch (error) {
        console.error("Xtream live proxy error:", error);
        return res.status(502).send("#EXTM3U\n# Error: no se pudo cargar TV en vivo desde Xtream\n");
      }
    }

    const sourceUrl = String(client.playlistUrl || "").trim();

    if (!sourceUrl.startsWith("http://") && !sourceUrl.startsWith("https://")) {
      return res.status(400).send("#EXTM3U\n# Error: playlist inválida");
    }

    const cacheKey = activationCode + "|" + type + "|" + sourceUrl;
    const cached = playlistProxyCache.get(cacheKey);
    const cacheTtlMs = 4 * 60 * 60 * 1000;

    if (!force && cached && Date.now() - cached.updatedAt < cacheTtlMs) {
      res.setHeader("Content-Type", "application/x-mpegURL; charset=utf-8");
      res.setHeader("X-StoreTD-Playlist-Cache", "HIT");
      return res.send(cached.m3u);
    }

    const sourceText = await downloadPlaylistTextWithLimit(sourceUrl, 40 * 1024 * 1024);
    const entries = parseM3uEntries(sourceText);

    let filtered = entries;

    if (type !== "all") {
      filtered = entries.filter((entry) => getPlaylistEntryType(entry) === type);
    }

    filtered = filtered.filter((entry) => !isAdultEntry(entry));

    if (type === "live" && filtered.length === 0) {
      filtered = entries.filter((entry) => !isAdultEntry(entry));
    }

    const limit = type === "live" ? 2500 : 3000;
    const output = buildM3u(filtered.slice(0, limit));

    playlistProxyCache.set(cacheKey, {
      m3u: output,
      updatedAt: Date.now()
    });

    res.setHeader("Content-Type", "application/x-mpegURL; charset=utf-8");
    res.setHeader("X-StoreTD-Playlist-Cache", "MISS");
    res.send(output);
  } catch (error) {
    console.error("Playlist proxy error:", error);

    res.status(500).send(
      "#EXTM3U\n# Error: no se pudo generar playlist proxy\n# " +
        String(error.message || "Error desconocido")
    );
  }
});



app.get("/admin/devices", (req, res) => {
  res.sendFile(path.join(__dirname, "..", "public", "devices.html"));
});

app.get("/admin/api/devices", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const { data, error } = await supabase
      .from("devices")
      .select("*")
      .order("last_seen_at", { ascending: false });

    if (error) throw error;

    res.json({
      success: true,
      devices: (data || []).map(dbDeviceToApi)
    });
  } catch (error) {
    console.error("Devices list error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudieron cargar dispositivos."
    });
  }
});

app.put("/admin/api/devices/:activationCode/:deviceCode", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.params.activationCode);
    const deviceCode = String(req.params.deviceCode || "");

    const { data, error } = await supabase
      .from("devices")
      .update({
        blocked: Boolean(req.body.blocked),
        nickname: String(req.body.nickname || ""),
        blocked_reason: String(req.body.blockedReason || "")
      })
      .eq("activation_code", activationCode)
      .eq("device_code", deviceCode)
      .select()
      .maybeSingle();

    if (error) throw error;

    if (!data) {
      return res.status(404).json({
        success: false,
        message: "Dispositivo no encontrado."
      });
    }

    await logDeviceEvent({
      activationCode,
      deviceCode,
      eventType: Boolean(req.body.blocked) ? "device_blocked_or_updated" : "device_unblocked_or_updated",
      message: Boolean(req.body.blocked)
        ? (String(req.body.blockedReason || "") || "Dispositivo bloqueado o actualizado por admin.")
        : "Dispositivo desbloqueado o actualizado por admin.",
      metadata: {
        nickname: String(req.body.nickname || ""),
        blocked: Boolean(req.body.blocked),
        source: "admin_devices"
      }
    });

    res.json({
      success: true,
      message: "Dispositivo actualizado.",
      device: dbDeviceToApi(data)
    });
  } catch (error) {
    console.error("Device update error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo actualizar dispositivo."
    });
  }
});

app.delete("/admin/api/devices/:activationCode/:deviceCode", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.params.activationCode);
    const deviceCode = String(req.params.deviceCode || "");

    const { error } = await supabase
      .from("devices")
      .delete()
      .eq("activation_code", activationCode)
      .eq("device_code", deviceCode);

    if (error) throw error;

    await logDeviceEvent({
      activationCode,
      deviceCode,
      eventType: "device_unlinked",
      message: "Dispositivo desvinculado por admin.",
      metadata: {
        source: "admin_devices"
      }
    });

    res.json({
      success: true,
      message: "Dispositivo desvinculado."
    });
  } catch (error) {
    console.error("Device unlink error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo desvincular dispositivo."
    });
  }
});



app.get("/admin/device-events", (req, res) => {
  res.sendFile(path.join(__dirname, "..", "public", "device-events.html"));
});

app.get("/admin/api/device-events", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    let query = supabase
      .from("device_events")
      .select("*")
      .order("created_at", { ascending: false })
      .limit(Math.min(Number(req.query.limit || 200), 500));

    const activationCode = normalizeCode(req.query.activationCode || "");
    const deviceCode = String(req.query.deviceCode || "").trim();
    const eventType = String(req.query.eventType || "").trim();

    if (activationCode) {
      query = query.eq("activation_code", activationCode);
    }

    if (deviceCode) {
      query = query.eq("device_code", deviceCode);
    }

    if (eventType) {
      query = query.eq("event_type", eventType);
    }

    const { data, error } = await query;

    if (error) throw error;

    res.json({
      success: true,
      events: (data || []).map(dbDeviceEventToApi)
    });
  } catch (error) {
    console.error("Device events list error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo cargar auditoría de dispositivos."
    });
  }
});








function normalizeM3uContentType(value, group) {
  const raw = String(value || "").trim().toLowerCase();
  const groupText = String(group || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();

  if (raw === "movie" || raw === "pelicula" || raw === "peliculas") return "movie";
  if (raw === "serie" || raw === "series") return "serie";
  if (raw === "live" || raw === "tv" || raw === "canal") return "live";

  if (groupText.startsWith("peliculas") || groupText.startsWith("peliculas |")) return "movie";
  if (groupText.startsWith("series") || groupText.startsWith("series |")) return "serie";
  if (groupText.startsWith("tv") || groupText.startsWith("tv |")) return "live";

  return "live";
}

function normalizeM3uExtinfLine(extinfLine, defaultType, defaultGroup) {
  const line = String(extinfLine || "").trim();
  if (!line.startsWith("#EXTINF")) return line;

  const groupMatch = line.match(/group-title="([^"]*)"/i);
  const group = groupMatch ? groupMatch[1] : defaultGroup;
  const contentType = normalizeM3uContentType(defaultType, group);

  if (/tvg-type="/i.test(line)) {
    return line;
  }

  return line.replace("#EXTINF:-1", `#EXTINF:-1 tvg-type="${contentType}"`);
}


function escapeM3uAttribute(value) {
  return String(value || "")
    .replace(/"/g, "'")
    .replace(/\r/g, " ")
    .replace(/\n/g, " ")
    .trim();
}

function ensureM3uHeader(content) {
  const text = String(content || "").replace(/\r/g, "").trim();

  if (text.startsWith("#EXTM3U")) {
    return text;
  }

  return "#EXTM3U\n" + text;
}

function getM3uExistingUrlHashes(m3uText) {
  const hashes = new Set();
  const lines = String(m3uText || "").replace(/\r/g, "").split("\n");

  for (const line of lines) {
    const url = String(line || "").trim();

    if (url && !url.startsWith("#")) {
      hashes.add(streamUrlHash(url));
    }
  }

  return hashes;
}

function buildM3uEntry({ name, group, streamUrl, logoUrl, tvgId, contentType }) {
  const safeName = escapeM3uAttribute(name) || "Contenido agregado";
  const safeGroup = escapeM3uAttribute(group) || "TV | Agregados";
  const safeLogo = escapeM3uAttribute(logoUrl);
  const safeTvgId = escapeM3uAttribute(tvgId);
  const safeType = normalizeM3uContentType(contentType, safeGroup);
  const cleanUrl = String(streamUrl || "").trim();

  const attrs = [
    `tvg-type="${safeType}"`,
    safeTvgId ? `tvg-id="${safeTvgId}"` : "",
    `tvg-name="${safeName}"`,
    safeLogo ? `tvg-logo="${safeLogo}"` : "",
    `group-title="${safeGroup}"`
  ].filter(Boolean).join(" ");

  return `#EXTINF:-1 ${attrs},${safeName}\n${cleanUrl}`;
}

function parseM3uBlocksForAppend(rawText, defaultGroup, defaultType = "live") {
  const lines = String(rawText || "")
    .replace(/\r/g, "")
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  const entries = [];

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];

    if (line.startsWith("#EXTM3U")) {
      continue;
    }

    if (line.startsWith("#EXTINF")) {
      const url = lines[i + 1] || "";

      if (url && !url.startsWith("#") && /^https?:\/\//i.test(url)) {
        const normalizedExtinf = normalizeM3uExtinfLine(line, defaultType, defaultGroup);
        entries.push(`${normalizedExtinf}\n${url}`);
        i += 1;
      }

      continue;
    }

    if (/^https?:\/\//i.test(line)) {
      const index = entries.length + 1;
      entries.push(buildM3uEntry({
        name: `Contenido agregado ${index}`,
        group: defaultGroup || "TV | Agregados",
        streamUrl: line,
        logoUrl: "",
        tvgId: "",
        contentType: defaultType
      }));
    }
  }

  return entries;
}


function appendUniqueM3uEntries(originalM3u, entries) {
  const existingHashes = getM3uExistingUrlHashes(originalM3u);
  const appended = [];
  let duplicates = 0;

  for (const entry of entries) {
    const lines = String(entry || "").replace(/\r/g, "").split("\n").map((line) => line.trim()).filter(Boolean);
    const url = lines.find((line) => !line.startsWith("#") && /^https?:\/\//i.test(line));

    if (!url) continue;

    const hash = streamUrlHash(url);

    if (existingHashes.has(hash)) {
      duplicates += 1;
      continue;
    }

    existingHashes.add(hash);
    appended.push(lines.join("\n"));
  }

  const base = ensureM3uHeader(originalM3u);
  const content = appended.length
    ? base.replace(/\s+$/g, "") + "\n" + appended.join("\n") + "\n"
    : base;

  return {
    content,
    added: appended.length,
    duplicates
  };
}



function smartoneM3uTypeFromGroupTitle(groupTitle) {
  const text = String(groupTitle || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .toLowerCase();

  if (
    text === "tv" ||
    text.startsWith("tv |") ||
    text.startsWith("tv|") ||
    text.startsWith("tv ") ||
    text.startsWith("canales") ||
    text.startsWith("en vivo") ||
    text.startsWith("live") ||
    text.startsWith("livetv")
  ) {
    return "live";
  }

  if (
    text === "peliculas" ||
    text === "pelicula" ||
    text.startsWith("peliculas |") ||
    text.startsWith("peliculas|") ||
    text.startsWith("peliculas ") ||
    text.startsWith("pelicula |") ||
    text.startsWith("pelicula|")
  ) {
    return "movie";
  }

  if (
    text === "series" ||
    text === "serie" ||
    text.startsWith("series |") ||
    text.startsWith("series|") ||
    text.startsWith("series ") ||
    text.startsWith("serie |") ||
    text.startsWith("serie|")
  ) {
    return "serie";
  }

  return "";
}

function repairSmartoneExtinfType(line) {
  const current = String(line || "");
  const groupMatch = current.match(/group-title="([^"]*)"/i);
  const groupTitle = groupMatch ? groupMatch[1] : "";
  const wantedType = smartoneM3uTypeFromGroupTitle(groupTitle);
  const currentTypeMatch = current.match(/tvg-type="([^"]*)"/i);
  const currentType = currentTypeMatch ? String(currentTypeMatch[1] || "").toLowerCase() : "";

  if (!wantedType) {
    return {
      line: current,
      type: currentType || "other",
      changed: false
    };
  }

  if (/tvg-type="[^"]*"/i.test(current)) {
    const repaired = current.replace(/tvg-type="[^"]*"/i, `tvg-type="${wantedType}"`);

    return {
      line: repaired,
      type: wantedType,
      changed: repaired !== current
    };
  }

  return {
    line: current.replace("#EXTINF:-1", `#EXTINF:-1 tvg-type="${wantedType}"`),
    type: wantedType,
    changed: true
  };
}

function normalizeM3uOrderForSmartone(m3uText) {
  const lines = String(m3uText || "").replace(/\r/g, "").split("\n");
  const buckets = {
    live: [],
    movie: [],
    serie: [],
    other: []
  };

  let entries = 0;
  let changedTypes = 0;

  for (let i = 0; i < lines.length; i += 1) {
    const line = String(lines[i] || "");
    const trimmed = line.trim();

    if (!trimmed || trimmed.startsWith("#EXTM3U")) {
      continue;
    }

    if (!trimmed.startsWith("#EXTINF")) {
      continue;
    }

    const repaired = repairSmartoneExtinfType(trimmed);
    const block = [repaired.line];

    let j = i + 1;

    while (j < lines.length) {
      const nextLine = String(lines[j] || "").trim();

      if (!nextLine) {
        j += 1;
        continue;
      }

      if (nextLine.startsWith("#EXTINF")) {
        break;
      }

      if (!nextLine.startsWith("#EXTM3U")) {
        block.push(nextLine);
      }

      if (!nextLine.startsWith("#")) {
        j += 1;
        break;
      }

      j += 1;
    }

    const bucketName = ["live", "movie", "serie"].includes(repaired.type)
      ? repaired.type
      : "other";

    buckets[bucketName].push(block.join("\n"));
    entries += 1;

    if (repaired.changed) {
      changedTypes += 1;
    }

    i = j - 1;
  }

  const orderedBlocks = [
    "#EXTM3U",
    ...buckets.live,
    ...buckets.movie,
    ...buckets.serie,
    ...buckets.other
  ];

  const content = orderedBlocks.join("\n").replace(/\s+$/g, "") + "\n";

  return {
    content,
    entries,
    changedTypes,
    changedOrder: content.trim() !== String(m3uText || "").replace(/\r/g, "").trim(),
    counts: {
      live: buckets.live.length,
      movies: buckets.movie.length,
      series: buckets.serie.length,
      other: buckets.other.length
    }
  };
}





function stripDiacriticsForTvGroups(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "");
}

function normalizeTvGroupKey(value) {
  return stripDiacriticsForTvGroups(value)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function getM3uGroupTitleFromLine(line) {
  const match = String(line || "").match(/group-title="([^"]*)"/i);
  return match ? match[1] : "";
}

function replaceM3uGroupTitleInLine(line, nextGroupTitle) {
  return String(line || "").replace(/(group-title=")([^"]*)(")/i, `$1${nextGroupTitle}$3`);
}

function isAdultTvGroupTitle(groupTitle) {
  const raw = stripDiacriticsForTvGroups(groupTitle)
    .toLowerCase()
    .trim();

  const clean = normalizeTvGroupKey(groupTitle);

  // No marcar como adulto solo por tener numero 18,
  // porque puede ser doble numeracion rota:
  // TV | 18 13 Musica
  return (
    clean.includes("adult") ||
    clean.includes("xxx") ||
    clean.includes("erot") ||
    clean.includes("para adultos") ||
    /(^|[^0-9])\+18([^0-9]|$)/.test(raw) ||
    /(^|[^0-9])18\+([^0-9]|$)/.test(raw)
  );
}

const SAFE_TV_GROUP_ORDER = [
  [1, "Noticias"],
  [2, "Deportes"],
  [3, "General"],
  [4, "Nacional Aire"],
  [5, "Gran Hermano AR"],
  [6, "Cine y Peliculas"],
  [7, "Series TV"],
  [8, "Documentales"],
  [9, "Infantiles"],
  [10, "Entretenimiento"],
  [11, "Internacionales"],
  [12, "Religion"],
  [13, "Musica"],
  [14, "Paraguay"],
  [15, "Paraguay Deportes"]
];

function getSafeTvGroupInfo(groupTitle) {
  const original = String(groupTitle || "").trim();

  if (!normalizeTvGroupKey(original).startsWith("tv")) {
    return null;
  }

  if (isAdultTvGroupTitle(original)) {
    return {
      kind: "adult",
      rank: 999,
      groupTitle: original
    };
  }

  const match = original.match(/^TV\s*\|\s*(.*)$/i);

  if (!match) {
    return {
      kind: "unknown",
      rank: 998,
      groupTitle: original
    };
  }

  const tail = String(match[1] || "").trim();

  // Borra SOLO numeros iniciales despues de "TV |".
  // Ejemplos:
  // TV | 16 01 Noticias -> Noticias
  // TV | 28 03 General  -> General
  // TV | 18 13 Musica   -> Musica
  const cleanTail = tail.replace(/^(?:\d{1,3}\s+)+/, "").trim() || tail;
  const cleanTailKey = normalizeTvGroupKey(cleanTail);

  const orderedBySpecificity = [...SAFE_TV_GROUP_ORDER].sort(
    (a, b) => String(b[1]).length - String(a[1]).length
  );

  for (const [number, label] of orderedBySpecificity) {
    const labelKey = normalizeTvGroupKey(label);

    if (cleanTailKey === labelKey || cleanTailKey.includes(labelKey)) {
      return {
        kind: "known",
        rank: number,
        groupTitle: `TV | ${String(number).padStart(2, "0")} ${cleanTail}`
      };
    }
  }

  return {
    kind: "unknown",
    rank: 998,
    groupTitle: original
  };
}

function normalizeTvGroupNumbersInM3u(m3uText) {
  const original = String(m3uText || "").replace(/\r/g, "");

  // Separacion liviana: no crea fingerprints enormes.
  const parts = original.split(/(?=^#EXTINF)/m);
  const header = parts.shift() || "";
  const entries = parts.filter(Boolean);

  const known = [];
  const unknown = [];
  const adult = [];
  const other = [];
  const changedGroupMap = new Map();
  let changedEntries = 0;

  entries.forEach((entry, index) => {
    const firstLineMatch = entry.match(/^#EXTINF[^\n]*/m);
    const firstLine = firstLineMatch ? firstLineMatch[0] : "";
    const groupTitle = getM3uGroupTitleFromLine(firstLine);
    const info = getSafeTvGroupInfo(groupTitle);

    const item = {
      index,
      rank: 10000,
      entry
    };

    if (!info) {
      other.push(item);
      return;
    }

    item.rank = info.rank;

    if (info.kind === "known") {
      if (info.groupTitle !== groupTitle) {
        item.entry = entry.replace(/^#EXTINF[^\n]*/m, (line) => {
          return replaceM3uGroupTitleInLine(line, info.groupTitle);
        });

        changedEntries += 1;
        changedGroupMap.set(groupTitle, info.groupTitle);
      }

      known.push(item);
      return;
    }

    if (info.kind === "adult") {
      // Adultos solo se mueve al final del bloque TV.
      // No se cambia el texto.
      adult.push(item);
      return;
    }

    // TV no reconocida: queda antes de adultos, sin cambiar texto.
    unknown.push(item);
  });

  known.sort((a, b) => {
    if (a.rank !== b.rank) return a.rank - b.rank;
    return a.index - b.index;
  });

  const ordered = [...known, ...unknown, ...adult, ...other];

  let content = header;
  for (const item of ordered) {
    content += item.entry;
  }

  return {
    content,
    changed: content === original ? 0 : Math.max(changedEntries, 1),
    changedGroups: Array.from(changedGroupMap.entries()),
    counts: {
      knownTv: known.length,
      unknownTv: unknown.length,
      adultTv: adult.length,
      other: other.length
    }
  };
}


function requireGistConfig(res) {
  const token = process.env.GITHUB_GIST_TOKEN || "";
  const gistId = process.env.GITHUB_GIST_ID || "";
  const filename = process.env.GITHUB_GIST_FILENAME || "lista.m3u";
  const rawUrl = process.env.GITHUB_GIST_RAW_URL || "";

  if (!token || !gistId || !filename || !rawUrl) {
    res.status(500).json({
      success: false,
      message: "Gist no configurado. Revisa GITHUB_GIST_TOKEN, GITHUB_GIST_ID, GITHUB_GIST_FILENAME y GITHUB_GIST_RAW_URL."
    });
    return null;
  }

  return { token, gistId, filename, rawUrl };
}

async function downloadGistM3uRaw(rawUrl) {
  const separator = rawUrl.includes("?") ? "&" : "?";
  const response = await fetch(`${rawUrl}${separator}t=${Date.now()}`, {
    headers: {
      "User-Agent": "StoreTD-Play-Admin",
      "Accept": "application/x-mpegURL,text/plain,*/*",
      "Cache-Control": "no-cache"
    }
  });

  if (!response.ok) {
    throw new Error(`No se pudo descargar la M3U original. HTTP ${response.status}`);
  }

  return await response.text();
}

async function updateGistFile({ token, gistId, filename, content }) {
  const mainFilename = process.env.GITHUB_GIST_FILENAME || "lista.m3u";
  const smartoneFilename = process.env.GITHUB_GIST_SMARTONE_FILENAME || "lista-smartone.m3u";

  const files = {
    [filename]: {
      content
    }
  };

  const shouldMirrorSmartone =
    filename === mainFilename &&
    smartoneFilename &&
    smartoneFilename !== filename &&
    String(content || "").includes("#EXTINF") &&
    typeof normalizeM3uOrderForSmartone === "function";

  if (shouldMirrorSmartone) {
    const normalizedSmartone = normalizeM3uOrderForSmartone(content);

    files[smartoneFilename] = {
      content: normalizedSmartone.content
    };
  }

  const response = await fetch(`https://api.github.com/gists/${gistId}`, {
    method: "PATCH",
    headers: {
      "Accept": "application/vnd.github+json",
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json",
      "User-Agent": "StoreTD-Play-Admin"
    },
    body: JSON.stringify({
      files
    })
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(data.message || `No se pudo actualizar el Gist. HTTP ${response.status}`);
  }

  return data;
}


function replaceM3uStreamUrlByHash(m3uText, targetHash, replacementUrl) {
  const lines = String(m3uText || "").replace(/\r/g, "").split("\n");
  let replacements = 0;

  const nextLines = lines.map((line) => {
    const trimmed = String(line || "").trim();

    if (
      trimmed &&
      !trimmed.startsWith("#") &&
      streamUrlHash(trimmed) === targetHash
    ) {
      replacements += 1;
      return replacementUrl;
    }

    return line;
  });

  return {
    content: nextLines.join("\n"),
    replacements
  };
}

function removeM3uEntriesByHash(m3uText, targetHash) {
  const lines = String(m3uText || "").replace(/\r/g, "").split("\n");
  const output = [];
  let removed = 0;

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i] || "";
    const nextLine = lines[i + 1] || "";

    if (
      line.trim().startsWith("#EXTINF") &&
      nextLine.trim() &&
      !nextLine.trim().startsWith("#") &&
      streamUrlHash(nextLine.trim()) === targetHash
    ) {
      removed += 1;
      i += 1;
      continue;
    }

    output.push(line);
  }

  return {
    content: output.join("\n"),
    removed
  };
}

function dbBrokenLinkToApi(row) {
  return {
    id: row.id,
    activationCode: row.activation_code || "",
    streamUrlHash: row.stream_url_hash || "",
    streamUrlMasked: row.stream_url_masked || "",
    channelName: row.channel_name || "",
    category: row.category || "",
    problemType: row.problem_type || "",
    playerError: row.player_error || "",
    firstReportedAt: row.first_reported_at || "",
    lastReportedAt: row.last_reported_at || "",
    reportCount: Number(row.report_count || 1),
    status: row.status || "Pendiente",
    replacementUrl: row.replacement_url || "",
    removedFromSource: Boolean(row.removed_from_source),
    resolvedAt: row.resolved_at || "",
    adminNote: row.admin_note || ""
  };
}





function normalizeAdminM3uType(value, group) {
  const raw = String(value || "").trim().toLowerCase();
  const groupText = String(group || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();

  if (raw === "movie" || raw === "pelicula" || raw === "peliculas") return "movie";
  if (raw === "serie" || raw === "series") return "serie";
  if (raw === "live" || raw === "tv" || raw === "canal") return "live";

  if (groupText.startsWith("peliculas")) return "movie";
  if (groupText.startsWith("series")) return "serie";
  if (groupText.startsWith("tv")) return "live";

  return "live";
}

function rewriteM3uGroupTitleAndType(m3uText, fromGroup, toGroup, contentType) {
  const sourceGroup = String(fromGroup || "").trim();
  const targetGroup = String(toGroup || "").trim();
  const safeType = normalizeAdminM3uType(contentType, targetGroup);

  if (!sourceGroup || !targetGroup) {
    return {
      content: String(m3uText || ""),
      changed: 0
    };
  }

  const lines = String(m3uText || "").replace(/\r/g, "").split("\n");
  let changed = 0;

  const nextLines = lines.map((line) => {
    if (!line.trim().startsWith("#EXTINF")) return line;

    const groupMatch = line.match(/group-title="([^"]*)"/i);
    const currentGroup = groupMatch ? groupMatch[1] : "";

    if (currentGroup !== sourceGroup) return line;

    let nextLine = line.replace(/group-title="[^"]*"/i, `group-title="${targetGroup}"`);

    if (/tvg-type="[^"]*"/i.test(nextLine)) {
      nextLine = nextLine.replace(/tvg-type="[^"]*"/i, `tvg-type="${safeType}"`);
    } else {
      nextLine = nextLine.replace("#EXTINF:-1", `#EXTINF:-1 tvg-type="${safeType}"`);
    }

    changed += 1;
    return nextLine;
  });

  return {
    content: nextLines.join("\n"),
    changed
  };
}




function readM3uAttributeFromLine(line, attrName) {
  const regex = new RegExp(attrName + '="([^"]*)"', "i");
  const match = String(line || "").match(regex);
  return match ? match[1] : "";
}

function readM3uDisplayName(line) {
  const text = String(line || "");
  const comma = text.lastIndexOf(",");
  return comma >= 0 ? text.slice(comma + 1).trim() : "";
}

function setM3uAttributeInLine(line, attrName, value) {
  const safeValue = escapeM3uAttribute(value);
  const regex = new RegExp(attrName + '="[^"]*"', "i");

  if (regex.test(line)) {
    return line.replace(regex, `${attrName}="${safeValue}"`);
  }

  return line.replace("#EXTINF:-1", `#EXTINF:-1 ${attrName}="${safeValue}"`);
}

function setM3uDisplayName(line, name) {
  const safeName = escapeM3uAttribute(name);
  const text = String(line || "");
  const comma = text.lastIndexOf(",");

  if (comma >= 0) {
    return text.slice(0, comma + 1) + safeName;
  }

  return text + "," + safeName;
}

function parseM3uEntriesForAdmin(m3uText, query = "", limit = 120) {
  const search = String(query || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();

  const lines = String(m3uText || "").replace(/\r/g, "").split("\n");
  const items = [];

  for (let i = 0; i < lines.length; i += 1) {
    const extinf = lines[i] || "";
    const url = lines[i + 1] || "";

    if (!extinf.trim().startsWith("#EXTINF")) continue;
    if (!url.trim() || url.trim().startsWith("#")) continue;

    const name = readM3uDisplayName(extinf) || readM3uAttributeFromLine(extinf, "tvg-name") || "Sin nombre";
    const group = readM3uAttributeFromLine(extinf, "group-title");
    const logoUrl = readM3uAttributeFromLine(extinf, "tvg-logo");
    const tvgId = readM3uAttributeFromLine(extinf, "tvg-id");
    const tvgType = readM3uAttributeFromLine(extinf, "tvg-type");
    const hash = streamUrlHash(url.trim());

    const haystack = `${name} ${group} ${url}`
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase();

    if (search && !haystack.includes(search)) continue;

    items.push({
      streamUrlHash: hash,
      name,
      group,
      logoUrl,
      tvgId,
      tvgType,
      streamUrlMasked: maskUrl(url.trim()),
      streamUrl: url.trim(),
      lineNumber: i + 1
    });

    if (items.length >= limit) break;
  }

  return items;
}

function updateM3uEntryByHash(m3uText, targetHash, changes = {}) {
  const lines = String(m3uText || "").replace(/\r/g, "").split("\n");
  let changed = 0;

  const newStreamUrl = String(changes.streamUrl || "").trim();
  const newName = String(changes.name || "").trim();
  const newGroup = String(changes.group || "").trim();
  const newLogoUrl = String(changes.logoUrl || "").trim();
  const newTvgId = String(changes.tvgId || "").trim();
  const newType = String(changes.contentType || "").trim();

  if (newStreamUrl) {
    const newHash = streamUrlHash(newStreamUrl);
    const duplicate = lines.some((line) => {
      const clean = String(line || "").trim();
      return clean &&
        !clean.startsWith("#") &&
        streamUrlHash(clean) === newHash &&
        newHash !== targetHash;
    });

    if (duplicate) {
      return {
        content: String(m3uText || ""),
        changed: 0,
        duplicate: true
      };
    }
  }

  for (let i = 0; i < lines.length; i += 1) {
    const extinf = lines[i] || "";
    const url = lines[i + 1] || "";

    if (!extinf.trim().startsWith("#EXTINF")) continue;
    if (!url.trim() || url.trim().startsWith("#")) continue;

    if (streamUrlHash(url.trim()) !== targetHash) continue;

    let nextExtinf = extinf;

    if (newName) {
      nextExtinf = setM3uAttributeInLine(nextExtinf, "tvg-name", newName);
      nextExtinf = setM3uDisplayName(nextExtinf, newName);
    }

    if (newGroup) {
      nextExtinf = setM3uAttributeInLine(nextExtinf, "group-title", newGroup);
    }

    if (newLogoUrl) {
      nextExtinf = setM3uAttributeInLine(nextExtinf, "tvg-logo", newLogoUrl);
    }

    if (newTvgId) {
      nextExtinf = setM3uAttributeInLine(nextExtinf, "tvg-id", newTvgId);
    }

    if (newType || newGroup) {
      const safeType = normalizeM3uContentType(newType, newGroup || readM3uAttributeFromLine(nextExtinf, "group-title"));
      nextExtinf = setM3uAttributeInLine(nextExtinf, "tvg-type", safeType);
    }

    lines[i] = nextExtinf;

    if (newStreamUrl) {
      lines[i + 1] = newStreamUrl;
    }

    changed += 1;
  }

  return {
    content: lines.join("\n"),
    changed,
    duplicate: false
  };
}




function analyzeM3uForAdmin(m3uText) {
  const lines = String(m3uText || "").replace(/\r/g, "").split("\n");
  const categories = new Map();
  const urlHashes = new Map();

  let entries = 0;
  let brokenEntries = 0;
  let duplicateUrls = 0;

  for (let i = 0; i < lines.length; i += 1) {
    const extinf = lines[i] || "";

    if (!extinf.trim().startsWith("#EXTINF")) continue;

    entries += 1;

    const url = String(lines[i + 1] || "").trim();

    if (!url || url.startsWith("#")) {
      brokenEntries += 1;
      continue;
    }

    const group = readM3uAttributeFromLine(extinf, "group-title") || "Sin categoría";
    categories.set(group, (categories.get(group) || 0) + 1);

    const hash = streamUrlHash(url);
    const count = (urlHashes.get(hash) || 0) + 1;
    urlHashes.set(hash, count);

    if (count === 2) {
      duplicateUrls += 1;
    }
  }

  const topCategories = Array.from(categories.entries())
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 80);

  return {
    entries,
    categories: categories.size,
    duplicateUrls,
    brokenEntries,
    topCategories
  };
}



app.get("/admin/api/m3u/download", requireAdmin, async (req, res) => {
  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const m3uText = await downloadGistM3uRaw(config.rawUrl);
    const stamp = new Date().toISOString().replace(/[:.]/g, "-");

    res.setHeader("Content-Type", "application/x-mpegURL; charset=utf-8");
    res.setHeader("Content-Disposition", `attachment; filename="storetd-lista-backup-${stamp}.m3u"`);
    res.send(m3uText);
  } catch (error) {
    console.error("Download M3U backup error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudo descargar backup M3U."
    });
  }
});


function findM3uDuplicateEntries(m3uText, limit = 200) {
  const lines = String(m3uText || "").replace(/\r/g, "").split("\n");
  const byHash = new Map();

  for (let i = 0; i < lines.length; i += 1) {
    const extinf = lines[i] || "";
    const url = lines[i + 1] || "";

    if (!extinf.trim().startsWith("#EXTINF")) continue;
    if (!url.trim() || url.trim().startsWith("#")) continue;

    const cleanUrl = url.trim();
    const hash = streamUrlHash(cleanUrl);
    const name = readM3uDisplayName(extinf) || readM3uAttributeFromLine(extinf, "tvg-name") || "Sin nombre";
    const group = readM3uAttributeFromLine(extinf, "group-title") || "Sin categoría";

    if (!byHash.has(hash)) {
      byHash.set(hash, []);
    }

    byHash.get(hash).push({
      streamUrlHash: hash,
      name,
      group,
      streamUrlMasked: maskUrl(cleanUrl),
      lineNumber: i + 1
    });
  }

  const duplicates = [];

  for (const [hash, entries] of byHash.entries()) {
    if (entries.length <= 1) continue;

    duplicates.push({
      streamUrlHash: hash,
      count: entries.length,
      keep: entries[0],
      duplicates: entries.slice(1)
    });

    if (duplicates.length >= limit) break;
  }

  return duplicates;
}

function removeDuplicateM3uEntriesKeepingFirst(m3uText) {
  const lines = String(m3uText || "").replace(/\r/g, "").split("\n");
  const seen = new Set();
  const output = [];
  let removed = 0;

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i] || "";
    const nextLine = lines[i + 1] || "";

    if (
      line.trim().startsWith("#EXTINF") &&
      nextLine.trim() &&
      !nextLine.trim().startsWith("#")
    ) {
      const hash = streamUrlHash(nextLine.trim());

      if (seen.has(hash)) {
        removed += 1;
        i += 1;
        continue;
      }

      seen.add(hash);
      output.push(line);
      output.push(nextLine);
      i += 1;
      continue;
    }

    output.push(line);
  }

  return {
    content: output.join("\n"),
    removed
  };
}



app.get("/admin/api/m3u/duplicates", requireAdmin, async (req, res) => {
  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const originalM3u = await downloadGistM3uRaw(config.rawUrl);
    const duplicates = findM3uDuplicateEntries(originalM3u, 300);

    res.json({
      success: true,
      duplicateGroups: duplicates.length,
      duplicateEntries: duplicates.reduce((sum, item) => sum + Math.max(0, item.count - 1), 0),
      duplicates
    });
  } catch (error) {
    console.error("M3U duplicates list error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudieron buscar duplicados."
    });
  }
});

app.post("/admin/api/m3u/remove-duplicates", requireAdmin, async (req, res) => {
  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const originalM3u = await downloadGistM3uRaw(config.rawUrl);
    const result = removeDuplicateM3uEntriesKeepingFirst(originalM3u);

    if (result.removed <= 0) {
      return res.json({
        success: true,
        message: "No había URLs duplicadas para limpiar.",
        removed: 0
      });
    }

    await updateGistFile({
      token: config.token,
      gistId: config.gistId,
      filename: config.filename,
      content: result.content
    });

    res.json({
      success: true,
      message: `Duplicados eliminados: ${result.removed}. Se conservó la primera aparición de cada URL.`,
      removed: result.removed
    });
  } catch (error) {
    console.error("M3U remove duplicates error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudieron limpiar duplicados."
    });
  }
});



function listM3uCategoriesForAdmin(m3uText) {
  const lines = String(m3uText || "").replace(/\r/g, "").split("\n");
  const categories = new Map();

  for (let i = 0; i < lines.length; i += 1) {
    const extinf = lines[i] || "";
    const url = lines[i + 1] || "";

    if (!extinf.trim().startsWith("#EXTINF")) continue;
    if (!url.trim() || url.trim().startsWith("#")) continue;

    const group = readM3uAttributeFromLine(extinf, "group-title") || "Sin categoría";
    const name = readM3uDisplayName(extinf) || readM3uAttributeFromLine(extinf, "tvg-name") || "Sin nombre";

    if (!categories.has(group)) {
      categories.set(group, {
        name: group,
        count: 0,
        samples: []
      });
    }

    const item = categories.get(group);
    item.count += 1;

    if (item.samples.length < 3) {
      item.samples.push(name);
    }
  }

  return Array.from(categories.values())
    .sort((a, b) => a.name.localeCompare(b.name));
}

function removeM3uEntriesByGroup(m3uText, targetGroup) {
  const groupToRemove = String(targetGroup || "").trim();
  const lines = String(m3uText || "").replace(/\r/g, "").split("\n");
  const output = [];
  let removed = 0;

  if (!groupToRemove) {
    return {
      content: String(m3uText || ""),
      removed: 0
    };
  }

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i] || "";
    const nextLine = lines[i + 1] || "";

    if (
      line.trim().startsWith("#EXTINF") &&
      nextLine.trim() &&
      !nextLine.trim().startsWith("#")
    ) {
      const group = readM3uAttributeFromLine(line, "group-title") || "Sin categoría";

      if (group === groupToRemove) {
        removed += 1;
        i += 1;
        continue;
      }

      output.push(line);
      output.push(nextLine);
      i += 1;
      continue;
    }

    output.push(line);
  }

  return {
    content: output.join("\n"),
    removed
  };
}



app.get("/admin/api/m3u/categories", requireAdmin, async (req, res) => {
  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const originalM3u = await downloadGistM3uRaw(config.rawUrl);
    const categories = listM3uCategoriesForAdmin(originalM3u);

    res.json({
      success: true,
      count: categories.length,
      categories
    });
  } catch (error) {
    console.error("M3U categories list error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudieron cargar categorías."
    });
  }
});

app.post("/admin/api/m3u/delete-group", requireAdmin, async (req, res) => {
  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const group = String(req.body.group || "").trim();

    if (!group) {
      return res.status(400).json({
        success: false,
        message: "Falta categoría para eliminar."
      });
    }

    const originalM3u = await downloadGistM3uRaw(config.rawUrl);
    const result = removeM3uEntriesByGroup(originalM3u, group);

    if (result.removed <= 0) {
      return res.status(404).json({
        success: false,
        message: "No encontré entradas con esa categoría."
      });
    }

    await updateGistFile({
      token: config.token,
      gistId: config.gistId,
      filename: config.filename,
      content: result.content
    });

    res.json({
      success: true,
      message: `Categoría eliminada de lista.m3u: ${group}. Entradas eliminadas: ${result.removed}.`,
      removed: result.removed
    });
  } catch (error) {
    console.error("M3U delete group error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudo eliminar la categoría."
    });
  }
});


app.get("/admin/api/m3u/validate", requireAdmin, async (req, res) => {
  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const m3uText = await downloadGistM3uRaw(config.rawUrl);
    const analysis = analyzeM3uForAdmin(m3uText);

    res.json({
      success: true,
      ...analysis
    });
  } catch (error) {
    console.error("Validate M3U error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudo validar la M3U."
    });
  }
});


app.get("/admin/api/m3u/search", requireAdmin, async (req, res) => {
  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const query = String(req.query.q || "").trim();
    const limit = Math.min(Number(req.query.limit || 120), 300);

    const originalM3u = await downloadGistM3uRaw(config.rawUrl);
    const items = parseM3uEntriesForAdmin(originalM3u, query, limit);

    res.json({
      success: true,
      query,
      count: items.length,
      items
    });
  } catch (error) {
    console.error("Admin M3U search error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudo buscar en la M3U."
    });
  }
});

app.post("/admin/api/m3u/update-entry", requireAdmin, async (req, res) => {
  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const streamUrlHashValue = String(req.body.streamUrlHash || "").trim();
    const changes = {
      name: String(req.body.name || "").trim(),
      group: String(req.body.group || "").trim(),
      logoUrl: String(req.body.logoUrl || "").trim(),
      tvgId: String(req.body.tvgId || "").trim(),
      contentType: String(req.body.contentType || "").trim(),
      streamUrl: String(req.body.streamUrl || "").trim()
    };

    if (!streamUrlHashValue) {
      return res.status(400).json({
        success: false,
        message: "Falta streamUrlHash."
      });
    }

    if (changes.streamUrl && !changes.streamUrl.startsWith("http://") && !changes.streamUrl.startsWith("https://")) {
      return res.status(400).json({
        success: false,
        message: "El nuevo link debe empezar con http:// o https://."
      });
    }

    const originalM3u = await downloadGistM3uRaw(config.rawUrl);
    const result = updateM3uEntryByHash(originalM3u, streamUrlHashValue, changes);

    if (result.duplicate) {
      return res.status(409).json({
        success: false,
        message: "Ese nuevo link ya existe en la M3U."
      });
    }

    if (result.changed <= 0) {
      return res.status(404).json({
        success: false,
        message: "No encontré esa entrada en la M3U original."
      });
    }

    await updateGistFile({
      token: config.token,
      gistId: config.gistId,
      filename: config.filename,
      content: result.content
    });

    res.json({
      success: true,
      message: `Entrada actualizada (${result.changed} coincidencia/s).`,
      changed: result.changed
    });
  } catch (error) {
    console.error("Admin M3U update entry error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudo actualizar la entrada."
    });
  }
});

app.post("/admin/api/m3u/delete-entry", requireAdmin, async (req, res) => {
  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const streamUrlHashValue = String(req.body.streamUrlHash || "").trim();

    if (!streamUrlHashValue) {
      return res.status(400).json({
        success: false,
        message: "Falta streamUrlHash."
      });
    }

    const originalM3u = await downloadGistM3uRaw(config.rawUrl);
    const result = removeM3uEntriesByHash(originalM3u, streamUrlHashValue);

    if (result.removed <= 0) {
      return res.status(404).json({
        success: false,
        message: "No encontré esa entrada en la M3U original."
      });
    }

    await updateGistFile({
      token: config.token,
      gistId: config.gistId,
      filename: config.filename,
      content: result.content
    });

    res.json({
      success: true,
      message: `Entrada eliminada de lista.m3u (${result.removed} bloque/s).`,
      removed: result.removed
    });
  } catch (error) {
    console.error("Admin M3U delete entry error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudo eliminar la entrada."
    });
  }
});


app.post("/admin/api/m3u/rename-group", requireAdmin, async (req, res) => {
  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const fromGroup = String(req.body.fromGroup || "").trim();
    const toGroup = String(req.body.toGroup || "").trim();
    const contentType = String(req.body.contentType || "").trim();

    if (!fromGroup || !toGroup) {
      return res.status(400).json({
        success: false,
        message: "Falta grupo origen o grupo destino."
      });
    }

    const originalM3u = await downloadGistM3uRaw(config.rawUrl);
    const result = rewriteM3uGroupTitleAndType(
      originalM3u,
      fromGroup,
      toGroup,
      contentType
    );

    if (result.changed <= 0) {
      return res.status(404).json({
        success: false,
        message: `No encontré entradas con la categoría: ${fromGroup}`
      });
    }

    await updateGistFile({
      token: config.token,
      gistId: config.gistId,
      filename: config.filename,
      content: result.content
    });

    res.json({
      success: true,
      message: `Categoría actualizada: ${result.changed} entrada/s movida/s de "${fromGroup}" a "${toGroup}".`,
      changed: result.changed
    });
  } catch (error) {
    console.error("Rename M3U group error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudo renombrar la categoría."
    });
  }
});


app.post("/admin/api/m3u/add-entry", requireAdmin, async (req, res) => {
  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const name = String(req.body.name || "").trim();
    const group = String(req.body.group || "Agregados").trim();
    const streamUrl = String(req.body.streamUrl || "").trim();
    const logoUrl = String(req.body.logoUrl || "").trim();
    const tvgId = String(req.body.tvgId || "").trim();
    const contentType = normalizeM3uContentType(req.body.contentType, group);

    if (!name) {
      return res.status(400).json({
        success: false,
        message: "Falta nombre del contenido."
      });
    }

    if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
      return res.status(400).json({
        success: false,
        message: "El link debe empezar con http:// o https://."
      });
    }

    const originalM3u = await downloadGistM3uRaw(config.rawUrl);
    const entry = buildM3uEntry({ name, group, streamUrl, logoUrl, tvgId, contentType });
    const result = appendUniqueM3uEntries(originalM3u, [entry]);

    if (result.added <= 0) {
      return res.status(409).json({
        success: false,
        message: "Ese link ya existe en la M3U original.",
        duplicates: result.duplicates
      });
    }

    await updateGistFile({
      token: config.token,
      gistId: config.gistId,
      filename: config.filename,
      content: result.content
    });

    res.json({
      success: true,
      message: "Contenido agregado a lista.m3u. Actualiza contenido optimizado para verlo en la APK.",
      added: result.added,
      duplicates: result.duplicates
    });
  } catch (error) {
    console.error("Add M3U entry error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudo agregar el contenido a la M3U."
    });
  }
});

app.post("/admin/api/m3u/import", requireAdmin, async (req, res) => {
  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const m3uText = String(req.body.m3uText || "").trim();
    const defaultGroup = String(req.body.defaultGroup || "TV | Agregados").trim();
    const defaultType = normalizeM3uContentType(req.body.defaultType, defaultGroup);

    if (!m3uText) {
      return res.status(400).json({
        success: false,
        message: "Pegá una lista M3U o links para importar."
      });
    }

    const entries = parseM3uBlocksForAppend(m3uText, defaultGroup, defaultType);

    if (!entries.length) {
      return res.status(400).json({
        success: false,
        message: "No encontré entradas válidas para importar."
      });
    }

    const originalM3u = await downloadGistM3uRaw(config.rawUrl);
    const result = appendUniqueM3uEntries(originalM3u, entries);

    if (result.added <= 0) {
      return res.status(409).json({
        success: false,
        message: "No se agregó nada. Todos los links ya existían o eran inválidos.",
        duplicates: result.duplicates
      });
    }

    await updateGistFile({
      token: config.token,
      gistId: config.gistId,
      filename: config.filename,
      content: result.content
    });

    res.json({
      success: true,
      message: `Importación lista: ${result.added} agregado/s, ${result.duplicates} duplicado/s ignorado/s. Actualiza contenido optimizado para verlo en la APK.`,
      added: result.added,
      duplicates: result.duplicates
    });
  } catch (error) {
    console.error("Import M3U error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudo importar la lista M3U."
    });
  }
});


app.get("/admin/api/broken-links", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const status = String(req.query.status || "").trim();

    let query = supabase
      .from("broken_links")
      .select("*")
      .order("last_reported_at", { ascending: false })
      .limit(5000);

    if (status) {
      query = query.eq("status", status);
    }

    const { data, error } = await query;

    if (error) throw error;

    res.json({
      success: true,
      brokenLinks: (data || []).map(dbBrokenLinkToApi)
    });
  } catch (error) {
    console.error("Admin broken links list error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudieron cargar enlaces caídos."
    });
  }
});

app.post("/admin/api/broken-links/:id/replace-url", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const id = String(req.params.id || "").trim();
    const replacementUrl = String(req.body.replacementUrl || req.body.newStreamUrl || "").trim();
    const adminNote = String(req.body.adminNote || "").trim();

    if (!id) {
      return res.status(400).json({
        success: false,
        message: "Falta ID del enlace caído."
      });
    }

    if (!replacementUrl.startsWith("http://") && !replacementUrl.startsWith("https://")) {
      return res.status(400).json({
        success: false,
        message: "El nuevo link debe empezar con http:// o https://."
      });
    }

    const { data: brokenLink, error: linkError } = await supabase
      .from("broken_links")
      .select("*")
      .eq("id", id)
      .maybeSingle();

    if (linkError) throw linkError;

    if (!brokenLink) {
      return res.status(404).json({
        success: false,
        message: "Enlace caído no encontrado."
      });
    }

    const originalM3u = await downloadGistM3uRaw(config.rawUrl);
    const result = replaceM3uStreamUrlByHash(
      originalM3u,
      brokenLink.stream_url_hash,
      replacementUrl
    );

    if (result.replacements <= 0) {
      return res.status(404).json({
        success: false,
        message: "No encontré ese link dentro de la M3U original. Puede que la lista ya haya cambiado."
      });
    }

    await updateGistFile({
      token: config.token,
      gistId: config.gistId,
      filename: config.filename,
      content: result.content
    });

    const now = nowIso();

    const { error: updateError } = await supabase
      .from("broken_links")
      .update({
        status: "Solucionado",
        replacement_url: replacementUrl,
        removed_from_source: false,
        resolved_at: now,
        admin_note: adminNote || "Link reemplazado desde panel admin."
      })
      .eq("id", id);

    if (updateError) throw updateError;

    res.json({
      success: true,
      message: `Link reemplazado en la M3U original (${result.replacements} coincidencia/s). Actualiza contenido optimizado para reflejar el cambio en la APK.`,
      replacements: result.replacements,
      activationCode: brokenLink.activation_code
    });
  } catch (error) {
    console.error("Replace broken link error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudo reemplazar el link."
    });
  }
});

app.post("/admin/api/broken-links/:id/remove-from-m3u", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const config = requireGistConfig(res);
    if (!config) return;

    const id = String(req.params.id || "").trim();
    const adminNote = String(req.body.adminNote || "").trim();

    if (!id) {
      return res.status(400).json({
        success: false,
        message: "Falta ID del enlace caído."
      });
    }

    const { data: brokenLink, error: linkError } = await supabase
      .from("broken_links")
      .select("*")
      .eq("id", id)
      .maybeSingle();

    if (linkError) throw linkError;

    if (!brokenLink) {
      return res.status(404).json({
        success: false,
        message: "Enlace caído no encontrado."
      });
    }

    const originalM3u = await downloadGistM3uRaw(config.rawUrl);
    const result = removeM3uEntriesByHash(
      originalM3u,
      brokenLink.stream_url_hash
    );

    if (result.removed <= 0) {
      return res.status(404).json({
        success: false,
        message: "No encontré esa entrada dentro de la M3U original. Puede que la lista ya haya cambiado."
      });
    }

    await updateGistFile({
      token: config.token,
      gistId: config.gistId,
      filename: config.filename,
      content: result.content
    });

    const now = nowIso();

    const { error: updateError } = await supabase
      .from("broken_links")
      .update({
        status: "Solucionado",
        removed_from_source: true,
        resolved_at: now,
        admin_note: adminNote || "Entrada eliminada de la M3U original desde panel admin."
      })
      .eq("id", id);

    if (updateError) throw updateError;

    res.json({
      success: true,
      message: `Entrada eliminada de la M3U original (${result.removed} bloque/s). Actualiza contenido optimizado para reflejar el cambio en la APK.`,
      removed: result.removed,
      activationCode: brokenLink.activation_code
    });
  } catch (error) {
    console.error("Remove broken link from M3U error:", error);
    res.status(500).json({
      success: false,
      message: error.message || "No se pudo eliminar la entrada de la M3U."
    });
  }
});


app.get("/api/broken-links", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.query.code);

    if (!activationCode) {
      return res.status(400).json({
        success: false,
        message: "Falta código de activación."
      });
    }

    const { data, error } = await supabase
      .from("broken_links")
      .select("stream_url_hash, channel_name, category, stream_url_masked, report_count, status, last_reported_at")
      .eq("activation_code", activationCode)
      .neq("status", "Solucionado")
      .order("last_reported_at", { ascending: false })
      .limit(5000);

    if (error) throw error;

    res.json({
      success: true,
      activationCode,
      count: (data || []).length,
      hashes: (data || []).map((row) => row.stream_url_hash).filter(Boolean),
      items: (data || []).map((row) => ({
        streamUrlHash: row.stream_url_hash,
        channelName: row.channel_name || "",
        category: row.category || "",
        streamUrlMasked: row.stream_url_masked || "",
        reportCount: Number(row.report_count || 1),
        status: row.status || "Pendiente",
        lastReportedAt: row.last_reported_at || ""
      }))
    });
  } catch (error) {
    console.error("Broken links list error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudieron cargar enlaces reportados."
    });
  }
});



app.post("/admin/api/m3u/normalize-series-type", requireAdmin, async (req, res) => {
  const config = requireGistConfig(res);
  if (!config) return;

  try {
    const original = await downloadGistM3uRaw(config.rawUrl);
    const normalized = original.replace(/tvg-type="serie"/gi, 'tvg-type="series"');
    const changed = (original.match(/tvg-type="serie"/gi) || []).length;

    if (changed === 0) {
      return res.json({
        success: true,
        changed: 0,
        message: "No había entradas con tvg-type=serie para normalizar."
      });
    }

    await updateGistFile({
      token: config.token,
      gistId: config.gistId,
      filename: config.filename,
      content: normalized
    });

    res.json({
      success: true,
      changed,
      message: `Series normalizadas: ${changed} entradas cambiadas a tvg-type=series.`
    });
  } catch (error) {
    console.error("Normalize series type error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo normalizar tvg-type de series.",
      error: error.message
    });
  }
});



app.post("/admin/api/m3u/normalize-series-type-legacy", requireAdmin, async (req, res) => {
  const config = requireGistConfig(res);
  if (!config) return;

  try {
    const original = await downloadGistM3uRaw(config.rawUrl);
    const normalized = original.replace(/tvg-type="series"/gi, 'tvg-type="serie"');
    const changed = (original.match(/tvg-type="series"/gi) || []).length;

    if (changed === 0) {
      return res.json({
        success: true,
        changed: 0,
        message: "No había entradas con tvg-type=series para volver a serie."
      });
    }

    await updateGistFile({
      token: config.token,
      gistId: config.gistId,
      filename: config.filename,
      content: normalized
    });

    res.json({
      success: true,
      changed,
      message: `Series restauradas: ${changed} entradas cambiadas a tvg-type=serie.`
    });
  } catch (error) {
    console.error("Normalize series type legacy error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo restaurar tvg-type de series.",
      error: error.message
    });
  }
});



app.post("/admin/api/m3u/restore-before-import-packages", requireAdmin, async (req, res) => {
  const config = requireGistConfig(res);
  if (!config) return;

  try {
    const original = await downloadGistM3uRaw(config.rawUrl);
    const marker = 'Farewell Song (2019)';
    const markerIndex = original.indexOf(marker);

    if (markerIndex === -1) {
      return res.status(404).json({
        success: false,
        message: "No se encontró el marcador Farewell Song (2019). No se modificó la lista."
      });
    }

    const beforeMarker = original.lastIndexOf("#EXTINF", markerIndex);

    if (beforeMarker === -1) {
      return res.status(500).json({
        success: false,
        message: "No se pudo ubicar el inicio del bloque a restaurar."
      });
    }

    const restored = original.slice(0, beforeMarker).replace(/\s+$/g, "") + "\n";
    const removedBytes = original.length - restored.length;

    await updateGistFile({
      token: config.token,
      gistId: config.gistId,
      filename: config.filename,
      content: restored
    });

    res.json({
      success: true,
      message: "Lista restaurada antes de paquetes importados.",
      marker,
      originalBytes: original.length,
      restoredBytes: restored.length,
      removedBytes
    });
  } catch (error) {
    console.error("Restore before import packages error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo restaurar la lista antes de paquetes.",
      error: error.message
    });
  }
});



app.post(
  "/admin/api/m3u/restore-from-text",
  requireAdmin,
  express.text({ type: "*/*", limit: "50mb" }),
  async (req, res) => {
    const config = requireGistConfig(res);
    if (!config) return;

    try {
      const content = String(req.body || "").trim();

      if (!content.startsWith("#EXTM3U")) {
        return res.status(400).json({
          success: false,
          message: "El contenido enviado no parece una lista M3U válida."
        });
      }

      await updateGistFile({
        token: config.token,
        gistId: config.gistId,
        filename: config.filename,
        content: content + "\n"
      });

      res.json({
        success: true,
        message: "Lista restaurada desde archivo local.",
        bytes: content.length
      });
    } catch (error) {
      console.error("Restore from text error:", error);
      res.status(500).json({
        success: false,
        message: "No se pudo restaurar la lista desde texto.",
        error: error.message
      });
    }
  }
);



app.post("/admin/api/m3u/normalize-smartone", requireAdmin, async (req, res) => {
  try {
    const gistConfig = requireGistConfig(res);
    if (!gistConfig) return;

    const activationCode = normalizeCode(req.body?.activationCode || req.query.code || "");
    const original = await downloadGistM3uRaw(gistConfig.rawUrl);
    const normalized = normalizeM3uOrderForSmartone(original);

    if (normalized.changedOrder || normalized.changedTypes > 0) {
      await updateGistFile({
        token: gistConfig.token,
        gistId: gistConfig.gistId,
        filename: gistConfig.filename,
        content: normalized.content
      });
    }

    let refreshResult = null;

    if (activationCode) {
      refreshResult = await refreshContentCacheForClient(activationCode);
    }

    res.json({
      success: true,
      message: "Lista M3U normalizada para Smartone IPTV.",
      changedOrder: normalized.changedOrder,
      changedTypes: normalized.changedTypes,
      entries: normalized.entries,
      counts: normalized.counts,
      activationCode: activationCode || null,
      refresh: refreshResult
    });
  } catch (error) {
    console.error("Normalize Smartone M3U error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo normalizar la lista para Smartone IPTV.",
      error: error.message
    });
  }
});



app.post("/admin/api/m3u/publish-smartone", requireAdmin, async (req, res) => {
  try {
    const gistConfig = requireGistConfig(res);
    if (!gistConfig) return;

    const targetFilename = String(req.body?.filename || req.query.filename || "lista-smartone.m3u").trim();
    const original = await downloadGistM3uRaw(gistConfig.rawUrl);
    const normalized = normalizeM3uOrderForSmartone(original);

    const response = await fetch(`https://api.github.com/gists/${gistConfig.gistId}`, {
      method: "PATCH",
      headers: {
        "Accept": "application/vnd.github+json",
        "Authorization": `Bearer ${gistConfig.token}`,
        "Content-Type": "application/json",
        "User-Agent": "StoreTD-Play-Admin"
      },
      body: JSON.stringify({
        files: {
          [targetFilename]: {
            content: normalized.content
          }
        }
      })
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      throw new Error(data.message || `No se pudo publicar lista Smartone. HTTP ${response.status}`);
    }

    const rawUrl = `https://gist.githubusercontent.com/gabijerecelroa/${gistConfig.gistId}/raw/${encodeURIComponent(targetFilename)}`;

    res.json({
      success: true,
      message: "Lista Smartone publicada.",
      filename: targetFilename,
      rawUrl,
      entries: normalized.entries,
      counts: normalized.counts,
      changedTypes: normalized.changedTypes,
      changedOrder: normalized.changedOrder
    });
  } catch (error) {
    console.error("Publish Smartone M3U error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo publicar la lista Smartone.",
      error: error.message
    });
  }
});




app.post("/admin/api/m3u/normalize-tv-groups", requireAdmin, async (req, res) => {
  try {
    const gistConfig = requireGistConfig(res);
    if (!gistConfig) return;

    const activationCode = normalizeCode(req.body?.activationCode || req.query.code || "");
    const original = await downloadGistM3uRaw(gistConfig.rawUrl);
    const result = normalizeTvGroupNumbersInM3u(original);

    if (result.changed > 0) {
      await updateGistFile({
        token: gistConfig.token,
        gistId: gistConfig.gistId,
        filename: gistConfig.filename,
        content: result.content
      });
    }

    let refreshResult = null;

    if (activationCode) {
      refreshResult = await refreshContentCacheForClient(activationCode);
    }

    res.json({
      success: true,
      message: result.changed > 0
        ? "TV en vivo corregido: numeros 01-15, adultos al final, sin tocar URLs ni datos."
        : "TV en vivo ya estaba correcto.",
      changed: result.changed,
      counts: result.counts,
      changedGroups: result.changedGroups,
      activationCode: activationCode || null,
      refresh: refreshResult
    });
  } catch (error) {
    console.error("Normalize TV groups error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo corregir TV en vivo.",
      error: error.message
    });
  }
});



function shouldUseXtreamLiveRefresh(section) {
  const value = String(section || "").trim().toLowerCase();

  return (
    String(process.env.CONTENT_LIVE_SOURCE_MODE || "m3u").trim().toLowerCase() === "xtream" &&
    (value === "live" || value === "all")
  );
}

function runScriptForClient(scriptName, activationCode) {
  const code = normalizeCode(activationCode);

  return new Promise((resolve, reject) => {
    try {
      const { spawn } = require("child_process");
      const path = require("path");

      const backendRoot = path.join(__dirname, "..");
      const scriptPath = path.join(backendRoot, "scripts", scriptName);

      const child = spawn(process.execPath, [scriptPath, code], {
        cwd: backendRoot,
        env: process.env,
        stdio: ["ignore", "pipe", "pipe"]
      });

      let stdout = "";
      let stderr = "";

      child.stdout.on("data", (data) => {
        stdout += data.toString();
      });

      child.stderr.on("data", (data) => {
        stderr += data.toString();
      });

      child.on("error", reject);

      child.on("close", (exitCode) => {
        if (exitCode === 0) {
          resolve({ stdout, stderr });
        } else {
          reject(new Error(`Script ${scriptName} falló con código ${exitCode}: ${stderr || stdout}`));
        }
      });
    } catch (error) {
      reject(error);
    }
  });
}

async function refreshXtreamLiveCacheForClient(activationCode) {
  const code = normalizeCode(activationCode);

  await runScriptForClient("sync_xtream_live.js", code);

  let liveCount = 0;
  let updatedAt = new Date().toISOString();

  try {
    const { data } = await supabase
      .from("playlist_cache")
      .select("payload, updated_at")
      .eq("activation_code", code)
      .eq("section", "live")
      .maybeSingle();

    if (data?.payload) {
      liveCount = Number(data.payload.itemCount || 0);

      if (!liveCount && Array.isArray(data.payload.items)) {
        liveCount = data.payload.items.length;
      }
    }

    if (data?.updated_at) {
      updatedAt = data.updated_at;
    }
  } catch (error) {
    console.error("No se pudo leer conteo live xtream:", error);
  }

  return {
    success: true,
    activationCode: code,
    section: "live",
    sourceMode: "xtream",
    counts: {
      live: liveCount
    },
    updatedAt
  };
}



async function refreshContentProtectingXtreamLive(activationCode, section) {
  const safeSection = String(section || "all").trim().toLowerCase();

  if (shouldUseXtreamLiveRefresh(safeSection)) {
    const liveResult = await refreshXtreamLiveCacheForClient(activationCode);

    if (safeSection === "live") {
      return liveResult;
    }

    const moviesResult = await refreshContentCacheForClient(activationCode, { section: "movies" });
    const seriesResult = await refreshContentCacheForClient(activationCode, { section: "series" });

    return {
      success: Boolean(liveResult.success && moviesResult.success && seriesResult.success),
      activationCode: normalizeCode(activationCode),
      section: "all",
      sourceMode: "mixed-xtream-live",
      counts: {
        live: liveResult?.counts?.live || 0,
        movies: moviesResult?.counts?.movies || moviesResult?.counts?.movieCategories || 0,
        movieCategories: moviesResult?.counts?.movieCategories || 0,
        series: seriesResult?.counts?.series || 0,
        seriesFolders: seriesResult?.counts?.seriesFolders || 0
      },
      updatedAt: new Date().toISOString()
    };
  }

  return await refreshContentCacheForClient(activationCode, { section: safeSection });
}


app.post("/api/content/refresh-app", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.body?.activationCode || req.query.code);
    const section = String(req.body?.section || req.query.section || "all")
      .trim()
      .toLowerCase();

    if (!activationCode) {
      return res.status(400).json({
        success: false,
        message: "Falta activationCode o code."
      });
    }

    const runAsync =
      req.query.async === "1" ||
      req.body?.async === true ||
      req.body?.async === "1";

    if (shouldUseXtreamLiveRefresh(section)) {
      if (runAsync) {
        refreshXtreamLiveCacheForClient(activationCode)
          .then((result) => {
            console.log("Async Xtream live refresh finished:", activationCode, result);
          })
          .catch((error) => {
            console.error("Async Xtream live refresh error:", activationCode, error);
          });

        return res.json({
          success: true,
          accepted: true,
          message: "Actualización de TV en vivo Xtream iniciada en segundo plano.",
          activationCode,
          section: "live",
          sourceMode: "xtream"
        });
      }

      const result = await refreshXtreamLiveCacheForClient(activationCode);
      return res.status(result.success ? 200 : 400).json(result);
    }

    if (runAsync) {
      refreshContentProtectingXtreamLive(activationCode, section)
        .then((result) => {
          console.log("Async content refresh finished:", activationCode, result);
        })
        .catch((error) => {
          console.error("Async content refresh error:", activationCode, error);
        });

      return res.json({
        success: true,
        accepted: true,
        message: "Actualización iniciada en segundo plano.",
        activationCode
      });
    }

    const result = await refreshContentProtectingXtreamLive(activationCode, section);
    res.status(result.success ? 200 : 400).json(result);
  } catch (error) {
    console.error("App content refresh error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo actualizar el contenido desde la app.",
      error: error.message
    });
  }
});

app.post("/api/content/refresh", requireAdmin, async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.body?.activationCode || req.query.code);
    const section = String(req.body?.section || req.query.section || "all")
      .trim()
      .toLowerCase();

    if (!activationCode) {
      return res.status(400).json({
        success: false,
        message: "Falta activationCode."
      });
    }

    if (shouldUseXtreamLiveRefresh(section)) {
      const result = await refreshXtreamLiveCacheForClient(activationCode);
      return res.status(result.success ? 200 : 400).json(result);
    }

    const result = await refreshContentProtectingXtreamLive(activationCode, section);
    res.status(result.success ? 200 : 400).json(result);
  } catch (error) {
    console.error("Content refresh error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo actualizar el contenido.",
      error: error.message
    });
  }
});



// HYBRID_XTREAM_FOLDER_ROUTES_START
function hybridModeEnabled() {
  return String(process.env.CONTENT_SOURCE_MODE || "").trim().toLowerCase() === "hybrid";
}

function hybridSlug(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "") || "categoria";
}

async function hybridGetCacheSection(activationCode, section) {
  const { data, error } = await supabase
    .from("playlist_cache")
    .select("payload, item_count, updated_at, playlist_url")
    .eq("activation_code", activationCode)
    .eq("section", section)
    .order("updated_at", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (error) throw error;
  return data || null;
}

function hybridMaskedPlaylist(url) {
  if (!url) return "";
  if (String(url).includes("gist.githubusercontent.com")) return "https://gist.githubusercontent.com/***";
  return String(url).replace(/username=[^&]+/i, "username=***").replace(/password=[^&]+/i, "password=***");
}

function hybridBuildGroups(items) {
  const set = new Set(["Todos"]);
  for (const item of items || []) {
    if (item.group) set.add(item.group);
  }
  return [...set];
}

function hybridCategoryListFromItems(items) {
  const map = new Map();

  for (const item of items || []) {
    const title = item.group || "Sin categoría";
    const key = hybridSlug(title);

    if (!map.has(key)) {
      map.set(key, {
        key,
        title,
        group: title,
        itemCount: 0
      });
    }

    map.get(key).itemCount++;
  }

  return [...map.values()].sort((a, b) => String(a.title).localeCompare(String(b.title)));
}

app.get("/api/content/movie-categories-lite", async (req, res, next) => {
  if (!hybridModeEnabled()) return next();
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.query.code || req.query.activationCode);
    if (!activationCode) {
      return res.status(400).json({ success: false, message: "Falta code." });
    }

    const categoriesRow = await hybridGetCacheSection(activationCode, "movie-categories");
    const moviesRow = await hybridGetCacheSection(activationCode, "movies");

    const moviesPayload = moviesRow?.payload || {};
    const moviesItems = Array.isArray(moviesPayload.items) ? moviesPayload.items : [];

    let categories = [];

    const rawCategories = categoriesRow?.payload?.categories;
    if (Array.isArray(rawCategories) && rawCategories.length) {
      categories = rawCategories.map((c) => ({
        key: c.key || hybridSlug(c.title || c.name || c.group),
        title: c.title || c.name || c.group || "Sin categoría",
        itemCount: Number(c.itemCount || c.count || 0)
      }));
    } else {
      categories = hybridCategoryListFromItems(moviesItems);
    }

    return res.json({
      success: true,
      fromCache: true,
      hybrid: true,
      section: "movie-categories-lite",
      activationCode,
      playlistUrlMasked: hybridMaskedPlaylist(moviesRow?.playlist_url || categoriesRow?.playlist_url),
      updatedAt: moviesPayload.updatedAt || moviesRow?.updated_at || categoriesRow?.updated_at || "",
      categoryCount: categories.length,
      itemCount: moviesItems.length || Number(categoriesRow?.item_count || 0),
      categories
    });
  } catch (error) {
    console.error("Hybrid movie-categories-lite error:", error);
    return res.status(500).json({
      success: false,
      message: "No se pudieron cargar categorías híbridas de películas.",
      error: error.message
    });
  }
});

app.get("/api/content/movie-category", async (req, res, next) => {
  if (!hybridModeEnabled()) return next();
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.query.code || req.query.activationCode);
    const key = String(req.query.key || "").trim();

    if (!activationCode || !key) {
      return res.status(400).json({ success: false, message: "Falta code o key." });
    }

    const moviesRow = await hybridGetCacheSection(activationCode, "movies");
    const payload = moviesRow?.payload || {};
    const allItems = Array.isArray(payload.items) ? payload.items : [];

    const items = allItems.filter((item) => hybridSlug(item.group || "Sin categoría") === key);
    const title = items[0]?.group || key;

    return res.json({
      success: true,
      fromCache: true,
      hybrid: true,
      section: "movie-category",
      activationCode,
      key,
      title,
      itemCount: items.length,
      items,
      groups: hybridBuildGroups(items),
      updatedAt: payload.updatedAt || moviesRow?.updated_at || ""
    });
  } catch (error) {
    console.error("Hybrid movie-category error:", error);
    return res.status(500).json({
      success: false,
      message: "No se pudo cargar categoría híbrida de películas.",
      error: error.message
    });
  }
});


function hybridXtreamBase() {
  return String(process.env.XTREAM_BASE_URL || process.env.XTREAM_BASE || "")
    .trim()
    .replace(/\/+$/, "");
}

function hybridXtreamUser() {
  return String(process.env.XTREAM_USERNAME || process.env.XTREAM_USER || "").trim();
}

function hybridXtreamPass() {
  return String(process.env.XTREAM_PASSWORD || process.env.XTREAM_PASS || "").trim();
}

async function hybridFetchXtream(action, extra = {}) {
  const base = hybridXtreamBase();
  const username = hybridXtreamUser();
  const password = hybridXtreamPass();

  if (!base || !username || !password) {
    throw new Error("Faltan XTREAM_BASE_URL, XTREAM_USERNAME o XTREAM_PASSWORD.");
  }

  const params = new URLSearchParams({
    username,
    password,
    action
  });

  for (const [key, value] of Object.entries(extra || {})) {
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      params.set(key, String(value));
    }
  }

  const url = `${base}/player_api.php?${params.toString()}`;

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 120000);

  try {
    const response = await fetch(url, {
      signal: controller.signal,
      headers: {
        "User-Agent": "StoreTD-Play-Backend/2.0",
        "Accept": "application/json,text/plain,*/*"
      }
    });

    if (!response.ok) {
      const text = await response.text().catch(() => "");
      throw new Error(`Xtream HTTP ${response.status}: ${text.slice(0, 200)}`);
    }

    const text = await response.text();
    return JSON.parse(text);
  } finally {
    clearTimeout(timeout);
  }
}

function hybridEpisodeUrl(episode) {
  const base = hybridXtreamBase();
  const username = encodeURIComponent(hybridXtreamUser());
  const password = encodeURIComponent(hybridXtreamPass());

  const id = episode.id || episode.stream_id || episode.episode_id;
  const ext = String(
    episode.container_extension ||
    episode.containerExtension ||
    episode.ext ||
    "mp4"
  ).replace(/^\./, "");

  return `${base}/series/${username}/${password}/${id}.${ext}`;
}

function hybridBuildEpisodeItems(seriesInfo, seriesId, fallbackTitle, fallbackLogo) {
  const episodesObj = seriesInfo?.episodes || {};
  const seriesTitle =
    seriesInfo?.info?.name ||
    seriesInfo?.info?.title ||
    fallbackTitle ||
    `Serie ${seriesId}`;

  const seriesLogo =
    seriesInfo?.info?.cover ||
    seriesInfo?.info?.movie_image ||
    fallbackLogo ||
    null;

  const items = [];

  for (const [seasonKey, episodeList] of Object.entries(episodesObj)) {
    if (!Array.isArray(episodeList)) continue;

    for (const ep of episodeList) {
      const seasonNumber = Number(ep.season || seasonKey || 0);
      const episodeNumber = Number(ep.episode_num || ep.episode || ep.number || 0);

      const sText = seasonNumber ? String(seasonNumber).padStart(2, "0") : "00";
      const eText = episodeNumber ? String(episodeNumber).padStart(2, "0") : "00";

      const title =
        ep.title ||
        ep.name ||
        ep.info?.name ||
        ep.info?.title ||
        `Capítulo ${episodeNumber || items.length + 1}`;

      const episodeId = ep.id || ep.stream_id || ep.episode_id;
      if (!episodeId) continue;

      items.push({
        name: `${seriesTitle} S${sText}E${eText}`,
        title: `${seriesTitle} S${sText}E${eText}`,
        displayName: title,
        group: seriesTitle,
        category: seriesTitle,
        tvgId: String(episodeId),
        logoUrl: ep.info?.movie_image || ep.info?.cover || ep.movie_image || seriesLogo,
        streamUrl: hybridEpisodeUrl(ep),
        streamId: episodeId,
        episodeId,
        seriesId,
        seriesName: seriesTitle,
        season: seasonNumber,
        episode: episodeNumber,
        episodeTitle: title,
        plot: ep.info?.plot || ep.plot || "",
        duration: ep.info?.duration || ep.duration || "",
        added: ep.added || "",
        type: "series",
        source: "xtream"
      });
    }
  }

  items.sort((a, b) => {
    return Number(a.season || 0) - Number(b.season || 0)
      || Number(a.episode || 0) - Number(b.episode || 0)
      || String(a.name).localeCompare(String(b.name));
  });

  return items;
}

app.get("/api/content/series-folders-lite", async (req, res, next) => {
  if (!hybridModeEnabled()) return next();
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.query.code || req.query.activationCode);

    if (!activationCode) {
      return res.status(400).json({ success: false, message: "Falta code." });
    }

    const seriesRow = await hybridGetCacheSection(activationCode, "series");
    const payload = seriesRow?.payload || {};
    const seriesItems = Array.isArray(payload.items) ? payload.items : [];

    const folders = seriesItems
      .filter((item) => item.seriesId || item.tvgId)
      .map((item) => {
        const seriesId = item.seriesId || item.tvgId;
        return {
          key: `series-${seriesId}`,
          title: item.name || `Serie ${seriesId}`,
          name: item.name || `Serie ${seriesId}`,
          group: item.group || "Series",
          category: item.group || "Series",
          logoUrl: item.logoUrl || null,
          seriesId,
          itemCount: Number(item.itemCount || item.episodeCount || item.episodes || 1),
          type: "series-folder",
          source: "xtream"
        };
      })
      .sort((a, b) => {
        return String(a.group).localeCompare(String(b.group))
          || String(a.title).localeCompare(String(b.title));
      });

    return res.json({
      success: true,
      fromCache: true,
      hybrid: true,
      section: "series-folders-lite",
      mode: "series-as-folders",
      activationCode,
      updatedAt: payload.updatedAt || seriesRow?.updated_at || "",
      folderCount: folders.length,
      itemCount: folders.length,
      folders
    });
  } catch (error) {
    console.error("Hybrid series-folders-lite episodes mode error:", error);
    return res.status(500).json({
      success: false,
      message: "No se pudieron cargar carpetas híbridas de series.",
      error: error.message
    });
  }
});

app.get("/api/content/series-folder", async (req, res, next) => {
  if (!hybridModeEnabled()) return next();
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.query.code || req.query.activationCode);
    const key = String(req.query.key || "").trim();

    if (!activationCode || !key) {
      return res.status(400).json({ success: false, message: "Falta code o key." });
    }

    const seriesRow = await hybridGetCacheSection(activationCode, "series");
    const payload = seriesRow?.payload || {};
    const allSeries = Array.isArray(payload.items) ? payload.items : [];

    let seriesId = "";
    let selectedSeries = null;

    if (key.startsWith("series-")) {
      seriesId = key.replace(/^series-/, "").trim();
      selectedSeries = allSeries.find((item) => String(item.seriesId || item.tvgId) === String(seriesId));
    } else {
      selectedSeries = allSeries.find((item) => hybridSlug(item.name || "") === key);
      seriesId = selectedSeries?.seriesId || selectedSeries?.tvgId || "";
    }

    if (!seriesId) {
      return res.status(404).json({
        success: false,
        message: "Serie no encontrada."
      });
    }

    const info = await hybridFetchXtream("get_series_info", { series_id: seriesId });

    const items = hybridBuildEpisodeItems(
      info,
      seriesId,
      selectedSeries?.name || "",
      selectedSeries?.logoUrl || null
    );

    const title =
      info?.info?.name ||
      info?.info?.title ||
      selectedSeries?.name ||
      `Serie ${seriesId}`;

    return res.json({
      success: true,
      fromCache: false,
      hybrid: true,
      section: "series-folder",
      mode: "episodes",
      activationCode,
      key,
      seriesId,
      title,
      itemCount: items.length,
      items,
      groups: ["Todos", title],
      updatedAt: new Date().toISOString()
    });
  } catch (error) {
    console.error("Hybrid series-folder episodes error:", error);
    return res.status(500).json({
      success: false,
      message: "No se pudieron cargar capítulos de la serie.",
      error: error.message
    });
  }
});


// HYBRID_XTREAM_FOLDER_ROUTES_END


app.get("/api/content/series-folders-lite", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.query.code);

    if (!activationCode) {
      return res.status(400).json({
        success: false,
        message: "Falta code."
      });
    }

    const result = await getSeriesFoldersLite({
      activationCode,
      autoRefresh: req.query.autoRefresh !== "0"
    });

    if (!result.success) {
      return res.status(result.status || 500).json({
        success: false,
        message: result.message
      });
    }

    res.json({
      success: true,
      fromCache: result.fromCache,
      ...filterPayloadAdultContent(
        result.payload,
        req.query.includeAdult === "1"
      )
    });
  } catch (error) {
    console.error("Series folders lite error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo obtener carpetas de series.",
      error: error.message
    });
  }
});

app.get("/api/content/series-folder", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.query.code);
    const key = String(req.query.key || "").trim();

    if (!activationCode) {
      return res.status(400).json({
        success: false,
        message: "Falta code."
      });
    }

    const result = await getSeriesFolderByKey({
      activationCode,
      key,
      autoRefresh: req.query.autoRefresh !== "0"
    });

    if (!result.success) {
      return res.status(result.status || 500).json({
        success: false,
        message: result.message
      });
    }

    res.json({
      success: true,
      fromCache: result.fromCache,
      ...filterPayloadAdultContent(
        result.payload,
        req.query.includeAdult === "1"
      )
    });
  } catch (error) {
    console.error("Series folder error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo obtener episodios de la carpeta.",
      error: error.message
    });
  }
});

app.get("/api/content/movie-categories-lite", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.query.code);

    if (!activationCode) {
      return res.status(400).json({
        success: false,
        message: "Falta code."
      });
    }

    const result = await getMovieCategoriesLite({
      activationCode,
      autoRefresh: req.query.autoRefresh !== "0"
    });

    if (!result.success) {
      return res.status(result.status || 500).json({
        success: false,
        message: result.message
      });
    }

    res.json({
      success: true,
      fromCache: result.fromCache,
      ...filterPayloadAdultContent(
        result.payload,
        req.query.includeAdult === "1"
      )
    });
  } catch (error) {
    console.error("Movie categories lite error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo obtener categorías de películas.",
      error: error.message
    });
  }
});

app.get("/api/content/movie-category", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.query.code);
    const key = String(req.query.key || "").trim();

    if (!activationCode) {
      return res.status(400).json({
        success: false,
        message: "Falta code."
      });
    }

    const result = await getMovieCategoryByKey({
      activationCode,
      key,
      autoRefresh: req.query.autoRefresh !== "0"
    });

    if (!result.success) {
      return res.status(result.status || 500).json({
        success: false,
        message: result.message
      });
    }

    res.json({
      success: true,
      fromCache: result.fromCache,
      ...filterPayloadAdultContent(
        result.payload,
        req.query.includeAdult === "1"
      )
    });
  } catch (error) {
    console.error("Movie category error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo obtener películas de la categoría.",
      error: error.message
    });
  }
});




// MAGMA_DYNAMIC_SEARCH_START
function magmaDynamicSearchNormalize(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();
}

function magmaDynamicSearchMatches(item, query) {
  const q = magmaDynamicSearchNormalize(query);
  if (!q) return false;

  const text = magmaDynamicSearchNormalize([
    item?.name,
    item?.title,
    item?.group,
    item?.category,
    item?.category_name,
    item?.plot,
    item?.release,
    item?.releaseDate
  ].filter(Boolean).join(" "));

  return text.includes(q);
}

function magmaDynamicSearchImage(value, size = "w500") {
  const text = String(value || "").trim();

  if (!text || text === "-") return "";

  if (/^https?:\/\//i.test(text)) return text;

  if (text.startsWith("/")) {
    return `https://image.tmdb.org/t/p/${size}${text}`;
  }

  return text;
}

async function magmaDynamicSearchFetchJson(action, extra = {}) {
  const base = String(process.env.MAGMA_BASE_URL || "").trim().replace(/\/+$/, "");
  const user = String(process.env.MAGMA_USER || "").trim();
  const pass = String(process.env.MAGMA_PASS || "").trim();

  if (!base || !user || !pass) {
    throw new Error("Faltan credenciales MAGMA_BASE_URL / MAGMA_USER / MAGMA_PASS.");
  }

  const params = new URLSearchParams();
  params.set("username", user);
  params.set("password", pass);
  params.set("action", action);

  for (const [key, value] of Object.entries(extra || {})) {
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      params.set(key, String(value));
    }
  }

  const url = `${base}/player_api.php?${params.toString()}`;

  const response = await fetch(url, {
    headers: {
      "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 15; StoreTD Play)",
      "Accept": "application/json,text/plain,*/*",
      "Accept-Encoding": "gzip"
    }
  });

  const text = await response.text();

  if (!response.ok) {
    throw new Error(`Magma search HTTP ${response.status}: ${text.slice(0, 160)}`);
  }

  if (!text.trim()) return [];

  return JSON.parse(text);
}

function magmaDynamicSearchPublicBase(req) {
  const fromEnv = String(process.env.PUBLIC_BASE_URL || process.env.APP_PUBLIC_BASE_URL || "").trim().replace(/\/+$/, "");

  if (fromEnv) return fromEnv;

  const protocol = req.headers["x-forwarded-proto"] || req.protocol || "http";
  const host = req.headers["x-forwarded-host"] || req.headers.host || "82.39.109.213:5000";

  return `${protocol}://${host}`.replace(/\/+$/, "");
}

async function magmaDynamicSearchSeriesCategoryMap() {
  const raw = await magmaDynamicSearchFetchJson("get_series_categories");
  const list = Array.isArray(raw) ? raw : [];
  const map = new Map();

  for (const item of list) {
    const id = String(item.category_id || item.id || "").trim();
    const name = String(item.category_name || item.name || "").trim();

    if (id && name) {
      map.set(id, name);
    }
  }

  return map;
}

app.get("/api/content/search", async (req, res, next) => {
  const section = String(req.query.section || "").trim().toLowerCase();

  if (section !== "movies" && section !== "series") {
    return next();
  }

  try {
    const activationCode = normalizeCode(req.query.code || req.query.activationCode);
    const query = String(req.query.q || req.query.query || "").trim();
    const limit = Math.max(1, Math.min(80, Number(req.query.limit || 40)));
    const publicBase = magmaDynamicSearchPublicBase(req);

    if (!activationCode) {
      return res.status(400).json({
        success: false,
        message: "Falta code."
      });
    }

    if (!query) {
      return res.json({
        success: true,
        source: section === "movies" ? "magma-movies-search" : "magma-series-search",
        section,
        query,
        itemCount: 0,
        items: []
      });
    }

    if (section === "movies") {
      const moviesRaw = await magmaDynamicSearchFetchJson("get_vod_streams");
      const movies = Array.isArray(moviesRaw) ? moviesRaw : [];

      const items = movies
        .filter((item) => magmaDynamicSearchMatches(item, query))
        .slice(0, limit)
        .map((item) => {
          const streamId = String(item.stream_id || item.id || "").trim();
          const poster = magmaDynamicSearchImage(item.stream_icon || item.cover || item.poster_path, "w500");
          const backdrop = magmaDynamicSearchImage(item.backdrop || item.backdrop_path, "w780");
          const release = String(item.release || item.releaseDate || "").trim();

          return {
            id: streamId,
            name: item.name || item.title || `Película ${streamId}`,
            title: item.name || item.title || `Película ${streamId}`,
            group: release ? `Películas | ${release}` : "Películas",
            tvgId: "",
            logoUrl: poster,
            posterUrl: poster,
            backdropUrl: backdrop,
            streamUrl: `${publicBase}/magma-lite/movie/${encodeURIComponent(streamId)}.m3u8?code=${encodeURIComponent(activationCode)}`,
            type: "movie",
            source: "magma-lite",
            streamId,
            release,
            rating: Number(item.rating_5based || item.rating || 0)
          };
        })
        .filter((item) => item.streamId);

      return res.json({
        success: true,
        source: "magma-movies-search",
        section,
        query,
        itemCount: items.length,
        items
      });
    }

    const categoryMap = await magmaDynamicSearchSeriesCategoryMap();
    const seriesRaw = await magmaDynamicSearchFetchJson("get_series");
    const series = Array.isArray(seriesRaw) ? seriesRaw : [];

    const items = series
      .filter((item) => magmaDynamicSearchMatches(item, query))
      .slice(0, limit)
      .map((item) => {
        const seriesId = String(item.series_id || item.id || "").trim();
        const categoryId = String(item.category_id || "").trim();
        const categoryName = categoryMap.get(categoryId) || "Series";
        const poster = magmaDynamicSearchImage(item.cover || item.stream_icon || item.poster_path, "w500");
        const backdrop = magmaDynamicSearchImage(item.backdrop_path || item.backdrop, "w780");
        const release = String(item.releaseDate || item.release || "").trim();

        return {
          id: seriesId,
          key: seriesId,
          name: item.name || item.title || `Serie ${seriesId}`,
          title: item.name || item.title || `Serie ${seriesId}`,
          group: categoryName,
          category: categoryName,
          tvgId: seriesId,
          logoUrl: poster,
          posterUrl: poster,
          backdropUrl: backdrop,
          streamUrl: `${publicBase}/magma-lite/series/${encodeURIComponent(seriesId)}?code=${encodeURIComponent(activationCode)}`,
          type: "series-folder",
          source: "magma-lite",
          seriesId,
          itemCount: Number(item.episode_count || item.itemCount || item.episodes || 1),
          release,
          rating: Number(item.rating_5based || item.rating || 0),
          plot: item.plot || ""
        };
      })
      .filter((item) => item.seriesId);

    return res.json({
      success: true,
      source: "magma-series-search",
      section,
      query,
      itemCount: items.length,
      items
    });
  } catch (error) {
    console.error("Magma dynamic search error:", error);

    return res.status(500).json({
      success: false,
      message: "No se pudo buscar en catálogo Magma.",
      error: error.message
    });
  }
});
// MAGMA_DYNAMIC_SEARCH_END

app.get("/api/content/search", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.query.code);
    const section = String(req.query.section || "movies").trim().toLowerCase();
    const query = String(req.query.q || req.query.query || "").trim();
    const limit = Number(req.query.limit || 80);

    if (!activationCode) {
      return res.status(400).json({
        success: false,
        message: "Falta code."
      });
    }

    const result = await searchContentItems({
      activationCode,
      section,
      query,
      limit,
      autoRefresh: req.query.autoRefresh !== "0"
    });

    if (!result.success) {
      return res.status(result.status || 500).json({
        success: false,
        message: result.message
      });
    }

    res.json({
      success: true,
      fromCache: result.fromCache,
      ...filterPayloadAdultContent(
        result.payload,
        req.query.includeAdult === "1"
      )
    });
  } catch (error) {
    console.error("Content search error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo buscar contenido.",
      error: error.message
    });
  }
});

app.get("/api/content/:section", async (req, res) => {
  if (!requireDb(res)) return;

  try {
    const activationCode = normalizeCode(req.query.code);
    const section = String(req.params.section || "").toLowerCase();

    if (!activationCode) {
      return res.status(400).json({
        success: false,
        message: "Falta code."
      });
    }

    const result = await getCachedContentSection({
      activationCode,
      section,
      autoRefresh: req.query.autoRefresh !== "0"
    });

    if (!result.success) {
      return res.status(result.status || 500).json({
        success: false,
        message: result.message
      });
    }

    res.json({
      success: true,
      fromCache: result.fromCache,
      ...filterPayloadAdultContent(
        result.payload,
        req.query.includeAdult === "1"
      )
    });
  } catch (error) {
    console.error("Content section error:", error);
    res.status(500).json({
      success: false,
      message: "No se pudo obtener contenido optimizado.",
      error: error.message
    });
  }
});


app.listen(port, () => {
  console.log("StoreTD Play backend running on port " + port);
});
