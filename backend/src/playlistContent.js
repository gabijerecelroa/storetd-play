const { supabase } = require("./db");

const CACHE_TABLE = "playlist_cache";

function normalizeCode(code) {
  return String(code || "").trim().toUpperCase();
}

function todayDate() {
  return new Date().toISOString().slice(0, 10);
}

function isExpired(expiresAt) {
  if (!expiresAt) return false;
  return String(expiresAt).slice(0, 10) < todayDate();
}

function normalizeText(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function attr(line, name) {
  const re = new RegExp(`${name}="([^"]*)"`, "i");
  const match = line.match(re);
  return match ? match[1].trim() : "";
}

function parseExtinfName(line) {
  const commaIndex = line.lastIndexOf(",");
  if (commaIndex === -1) return "";
  return line.slice(commaIndex + 1).trim();
}

function parseM3u(raw) {
  const lines = String(raw || "")
    .replace(/\r/g, "")
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  const items = [];
  let pending = null;

  for (const line of lines) {
    if (line.startsWith("#EXTINF")) {
      const tvgName = attr(line, "tvg-name");
      const displayName = parseExtinfName(line);

      pending = {
        name: tvgName || displayName || "Sin nombre",
        streamUrl: "",
        logoUrl: attr(line, "tvg-logo") || null,
        group: attr(line, "group-title") || "Sin categoría",
        tvgId: attr(line, "tvg-id") || null
      };
      continue;
    }

    if (line.startsWith("#")) continue;

    if (pending) {
      pending.streamUrl = line;

      if (pending.streamUrl) {
        items.push(pending);
      }

      pending = null;
    }
  }

  return items;
}

const adultWords = [
  "adult", "adulto", "adultos", "xxx", "+18", "18+", "hot",
  "erotic", "erotico", "erotica", "porno", "porn", "playboy",
  "venus", "private", "sexy", "sex", "para adultos", "brazzers"
];

const movieWords = [
  "pelicula", "peliculas", "movie", "movies", "cine", "cinema",
  "film", "films", "estreno", "estrenos", "vod", "accion",
  "terror", "comedia", "drama", "suspenso"
];

const seriesWords = [
  "serie", "series", "temporada", "season", "episode", "episodio",
  "capitulo", "novela", "novelas", "anime", "tv show", "shows"
];

function isAdult(item) {
  const text = normalizeText(`${item.name} ${item.group}`);
  return adultWords.some((word) => text.includes(normalizeText(word)));
}

function isMovie(item) {
  const name = normalizeText(item.name);
  const group = normalizeText(item.group);

  if (
    group.startsWith("pelicula") ||
    group.startsWith("peliculas") ||
    group.startsWith("movie") ||
    group.startsWith("movies") ||
    group.startsWith("vod") ||
    group.startsWith("cine")
  ) {
    return true;
  }

  return movieWords.some((word) => group.includes(normalizeText(word))) &&
    !isSeries(item);
}

function isSeries(item) {
  const name = normalizeText(item.name);
  const group = normalizeText(item.group);

  if (
    group.startsWith("serie") ||
    group.startsWith("series") ||
    group.startsWith("temporada") ||
    group.startsWith("novela") ||
    group.startsWith("anime")
  ) {
    return true;
  }

  return seriesWords.some((word) => group.includes(normalizeText(word)) || name.includes(normalizeText(word)));
}

function sectionOf(item) {
  if (isSeries(item)) return "series";
  if (isMovie(item)) return "movies";
  return "live";
}

function uniqueByUrl(items) {
  const map = new Map();

  for (const item of items) {
    const key = item.streamUrl || `${item.name}|${item.group}`;

    if (!map.has(key)) {
      map.set(key, item);
    }
  }

  return Array.from(map.values());
}

function groupNames(items) {
  return ["Todos", ...Array.from(new Set(items.map((item) => item.group || "Sin categoría"))).sort()];
}

function buildPayload({ activationCode, playlistUrl, section, items }) {
  return {
    section,
    activationCode,
    playlistUrlMasked: maskUrl(playlistUrl),
    updatedAt: new Date().toISOString(),
    itemCount: items.length,
    groups: groupNames(items),
    items
  };
}

function maskUrl(value) {
  const text = String(value || "");

  try {
    const url = new URL(text);
    return `${url.origin}/***`;
  } catch {
    if (text.length <= 18) return "***";
    return `${text.slice(0, 10)}***${text.slice(-6)}`;
  }
}

async function getClientByActivationCode(activationCode) {
  const code = normalizeCode(activationCode);

  const { data, error } = await supabase
    .from("clients")
    .select("*")
    .eq("activation_code", code)
    .maybeSingle();

  if (error) throw error;
  return data;
}

function validateClient(client) {
  if (!client) {
    return "Código de activación no encontrado.";
  }

  const status = String(client.status || "").toLowerCase();

  if (status.includes("bloq") || status.includes("suspend")) {
    return "Cuenta suspendida.";
  }

  if (isExpired(client.expires_at)) {
    return "Cuenta vencida.";
  }

  if (!client.playlist_url) {
    return "La cuenta no tiene lista asignada.";
  }

  return null;
}

async function fetchPlaylist(url) {
  const response = await fetch(url, {
    headers: {
      "User-Agent": "StoreTD-Play-Backend/2.0"
    }
  });

  if (!response.ok) {
    throw new Error(`No se pudo descargar la lista. HTTP ${response.status}`);
  }

  return await response.text();
}

function splitSections(items) {
  const clean = uniqueByUrl(items).filter((item) => !isAdult(item));

  const sections = {
    live: [],
    movies: [],
    series: []
  };

  for (const item of clean) {
    sections[sectionOf(item)].push(item);
  }

  sections.movies = uniqueByUrl(sections.movies)
    .sort((a, b) => String(a.name).localeCompare(String(b.name)));

  sections.live = sections.live
    .sort((a, b) => String(a.group).localeCompare(String(b.group)) || String(a.name).localeCompare(String(b.name)));

  sections.series = sections.series
    .sort((a, b) => String(a.group).localeCompare(String(b.group)) || String(a.name).localeCompare(String(b.name)));

  return sections;
}


function slugKey(value) {
  return normalizeText(value)
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function cleanSeriesTitle(value, fallbackGroup = "") {
  let title = String(value || "").trim();

  title = title
    .replace(/^series\s*[|:/-]\s*/i, "")
    .replace(/^serie\s*[|:/-]\s*/i, "")
    .replace(/^temporadas\s*[|:/-]\s*/i, "")
    .replace(/^cap[ií]tulos\s*[|:/-]\s*/i, "");

  title = title
    .replace(/\bS\s*\d{1,2}\s*E\s*\d{1,3}\b.*$/i, "")
    .replace(/\bT\s*\d{1,2}\s*E\s*\d{1,3}\b.*$/i, "")
    .replace(/\b\d{1,2}\s*x\s*\d{1,3}\b.*$/i, "")
    .replace(/\btemporada\s*\d{1,2}\b.*$/i, "")
    .replace(/\bseason\s*\d{1,2}\b.*$/i, "")
    .replace(/\bcap[ií]tulo\s*\d{1,3}\b.*$/i, "")
    .replace(/\bepisodio\s*\d{1,3}\b.*$/i, "")
    .replace(/\bepisode\s*\d{1,3}\b.*$/i, "")
    .replace(/\bep\s*\d{1,3}\b.*$/i, "")
    .replace(/\[[^\]]*\]/g, "")
    .replace(/\([^)]*\)/g, "")
    .replace(/\b(latino|castellano|subtitulado|dual audio|hd|fhd|4k|1080p|720p)\b/gi, "")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/^[\s\-|.:_]+|[\s\-|.:_]+$/g, "");

  if (title.length >= 3 && !looksLikeGenericSeriesGroup(title)) {
    return title;
  }

  if (fallbackGroup && !looksLikeGenericSeriesGroup(fallbackGroup)) {
    return fallbackGroup;
  }

  return String(value || fallbackGroup || "Sin título").trim();
}

function looksLikeGenericSeriesGroup(value) {
  const text = normalizeText(value);

  return text === "series" ||
    text === "serie" ||
    text.startsWith("series |") ||
    text.startsWith("series|") ||
    text.startsWith("serie |") ||
    text.startsWith("serie|") ||
    text.startsWith("series ") ||
    text.includes("animadas") ||
    text.includes("anime") ||
    text.includes("netflix") ||
    text.includes("hbo") ||
    text.includes("max") ||
    text.includes("disney") ||
    text.includes("prime") ||
    text.includes("paramount") ||
    text.includes("amc+") ||
    text.includes("adultos") ||
    text.includes("infantil") ||
    text.includes("documental") ||
    text.includes("latinas");
}

function episodeSeason(name) {
  const text = String(name || "");

  const patterns = [
    /\bS\s*(\d{1,2})\s*E\s*\d{1,3}\b/i,
    /\bT\s*(\d{1,2})\s*E\s*\d{1,3}\b/i,
    /\b(\d{1,2})\s*x\s*\d{1,3}\b/i,
    /\btemporada\s*(\d{1,2})\b/i,
    /\bseason\s*(\d{1,2})\b/i
  ];

  for (const pattern of patterns) {
    const match = text.match(pattern);
    if (match) return Number(match[1]) || 1;
  }

  return 1;
}

function episodeNumber(name) {
  const text = String(name || "");

  const patterns = [
    /\bS\s*\d{1,2}\s*E\s*(\d{1,3})\b/i,
    /\bT\s*\d{1,2}\s*E\s*(\d{1,3})\b/i,
    /\b\d{1,2}\s*x\s*(\d{1,3})\b/i,
    /\bcap[ií]tulo\s*(\d{1,3})\b/i,
    /\bepisodio\s*(\d{1,3})\b/i,
    /\bepisode\s*(\d{1,3})\b/i,
    /\bep\s*(\d{1,3})\b/i
  ];

  for (const pattern of patterns) {
    const match = text.match(pattern);
    if (match) return Number(match[1]) || 9999;
  }

  return 9999;
}

function buildSeriesFoldersPayload({ activationCode, playlistUrl, items }) {
  const foldersMap = new Map();

  for (const item of items) {
    const title = cleanSeriesTitle(item.name, item.group);
    const key = slugKey(title) || slugKey(item.group) || slugKey(item.name);

    if (!key) continue;

    if (!foldersMap.has(key)) {
      foldersMap.set(key, {
        key,
        title,
        group: item.group || "Series",
        posterUrl: item.logoUrl || null,
        episodeCount: 0,
        episodes: []
      });
    }

    const folder = foldersMap.get(key);

    if (!folder.posterUrl && item.logoUrl) {
      folder.posterUrl = item.logoUrl;
    }

    folder.episodes.push({
      id: item.id || slugKey(`${item.name}|${item.streamUrl}`),
      name: item.name,
      streamUrl: item.streamUrl,
      logoUrl: null,
      group: folder.title,
      tvgId: item.tvgId || null,
      season: episodeSeason(item.name),
      episode: episodeNumber(item.name)
    });
  }

  const folders = Array.from(foldersMap.values())
    .map((folder) => {
      const unique = new Map();

      for (const episode of folder.episodes) {
        const key = episode.streamUrl || `${episode.name}|${episode.season}|${episode.episode}`;
        if (!unique.has(key)) unique.set(key, episode);
      }

      folder.episodes = Array.from(unique.values()).sort((a, b) => {
        return (a.season - b.season) ||
          (a.episode - b.episode) ||
          String(a.name).localeCompare(String(b.name));
      });

      folder.episodeCount = folder.episodes.length;
      return folder;
    })
    .filter((folder) => folder.episodeCount > 0)
    .sort((a, b) => String(a.title).localeCompare(String(b.title)));

  return {
    section: "series-folders",
    activationCode,
    playlistUrlMasked: maskUrl(playlistUrl),
    updatedAt: new Date().toISOString(),
    folderCount: folders.length,
    itemCount: folders.reduce((sum, folder) => sum + folder.episodeCount, 0),
    folders
  };
}

function buildMovieCategoriesPayload({ activationCode, playlistUrl, items }) {
  const categoryMap = new Map();

  for (const item of items) {
    const title = item.group || "Sin categoría";
    const key = slugKey(title) || "sin-categoria";

    if (!categoryMap.has(key)) {
      categoryMap.set(key, {
        key,
        title,
        itemCount: 0,
        items: []
      });
    }

    categoryMap.get(key).items.push(item);
  }

  const categories = Array.from(categoryMap.values())
    .map((category) => {
      const unique = new Map();

      for (const item of category.items) {
        const key = item.streamUrl || `${item.name}|${item.group}`;
        if (!unique.has(key)) unique.set(key, item);
      }

      category.items = Array.from(unique.values())
        .sort((a, b) => String(a.name).localeCompare(String(b.name)));

      category.itemCount = category.items.length;
      return category;
    })
    .filter((category) => category.itemCount > 0)
    .sort((a, b) => String(a.title).localeCompare(String(b.title)));

  return {
    section: "movie-categories",
    activationCode,
    playlistUrlMasked: maskUrl(playlistUrl),
    updatedAt: new Date().toISOString(),
    categoryCount: categories.length,
    itemCount: categories.reduce((sum, category) => sum + category.itemCount, 0),
    categories
  };
}

async function saveRawPayloadCache({ activationCode, playlistUrl, section, payload }) {
  const itemCount =
    Number(payload.itemCount || payload.folderCount || payload.categoryCount || 0);

  const { error } = await supabase
    .from(CACHE_TABLE)
    .upsert(
      {
        activation_code: normalizeCode(activationCode),
        playlist_url: playlistUrl,
        section,
        payload,
        item_count: itemCount,
        updated_at: new Date().toISOString()
      },
      {
        onConflict: "activation_code,section"
      }
    );

  if (error) throw error;

  return payload;
}


async function saveSectionCache({ activationCode, playlistUrl, section, items }) {
  const payload = buildPayload({
    activationCode,
    playlistUrl,
    section,
    items
  });

  const { error } = await supabase
    .from(CACHE_TABLE)
    .upsert(
      {
        activation_code: normalizeCode(activationCode),
        playlist_url: playlistUrl,
        section,
        payload,
        item_count: items.length,
        updated_at: new Date().toISOString()
      },
      {
        onConflict: "activation_code,section"
      }
    );

  if (error) throw error;

  return payload;
}

async function refreshContentCacheForClient(activationCode) {
  const code = normalizeCode(activationCode);
  const client = await getClientByActivationCode(code);
  const invalidReason = validateClient(client);

  if (invalidReason) {
    return {
      success: false,
      message: invalidReason
    };
  }

  const raw = await fetchPlaylist(client.playlist_url);
  const parsed = parseM3u(raw);
  const sections = splitSections(parsed);

  const seriesFoldersPayload = buildSeriesFoldersPayload({
    activationCode: code,
    playlistUrl: client.playlist_url,
    items: sections.series
  });

  const movieCategoriesPayload = buildMovieCategoriesPayload({
    activationCode: code,
    playlistUrl: client.playlist_url,
    items: sections.movies
  });

  const [live, movies, series, seriesFolders, movieCategories] = await Promise.all([
    saveSectionCache({
      activationCode: code,
      playlistUrl: client.playlist_url,
      section: "live",
      items: sections.live
    }),
    saveSectionCache({
      activationCode: code,
      playlistUrl: client.playlist_url,
      section: "movies",
      items: sections.movies
    }),
    saveSectionCache({
      activationCode: code,
      playlistUrl: client.playlist_url,
      section: "series",
      items: sections.series
    }),
    saveRawPayloadCache({
      activationCode: code,
      playlistUrl: client.playlist_url,
      section: "series-folders",
      payload: seriesFoldersPayload
    }),
    saveRawPayloadCache({
      activationCode: code,
      playlistUrl: client.playlist_url,
      section: "movie-categories",
      payload: movieCategoriesPayload
    })
  ]);

  return {
    success: true,
    activationCode: code,
    counts: {
      live: live.itemCount,
      movies: movies.itemCount,
      series: series.itemCount,
      seriesFolders: seriesFolders.folderCount,
      movieCategories: movieCategories.categoryCount
    },
    updatedAt: new Date().toISOString()
  };
}

async function getCachedContentSection({ activationCode, section, autoRefresh = true }) {
  const code = normalizeCode(activationCode);
  const safeSection = String(section || "").toLowerCase();

  if (!["live", "movies", "series", "series-folders", "movie-categories"].includes(safeSection)) {
    return {
      success: false,
      status: 400,
      message: "Sección inválida. Usa live, movies o series."
    };
  }

  const client = await getClientByActivationCode(code);
  const invalidReason = validateClient(client);

  if (invalidReason) {
    return {
      success: false,
      status: 403,
      message: invalidReason
    };
  }

  const { data, error } = await supabase
    .from(CACHE_TABLE)
    .select("*")
    .eq("activation_code", code)
    .eq("section", safeSection)
    .maybeSingle();

  if (error) throw error;

  if (data?.payload) {
    return {
      success: true,
      status: 200,
      fromCache: true,
      payload: data.payload
    };
  }

  if (!autoRefresh) {
    return {
      success: false,
      status: 404,
      message: "No hay caché generado para esta sección."
    };
  }

  const refreshed = await refreshContentCacheForClient(code);

  if (!refreshed.success) {
    return {
      success: false,
      status: 500,
      message: refreshed.message || "No se pudo generar caché."
    };
  }

  return await getCachedContentSection({
    activationCode: code,
    section: safeSection,
    autoRefresh: false
  });
}


async function getSeriesFoldersLite({ activationCode, autoRefresh = true }) {
  const result = await getCachedContentSection({
    activationCode,
    section: "series-folders",
    autoRefresh
  });

  if (!result.success) return result;

  const payload = result.payload || {};
  const folders = Array.isArray(payload.folders) ? payload.folders : [];

  const liteFolders = folders.map((folder) => ({
    key: folder.key,
    title: folder.title,
    group: folder.group,
    posterUrl: folder.posterUrl || null,
    episodeCount: Number(folder.episodeCount || folder.episodes?.length || 0)
  }));

  return {
    success: true,
    status: 200,
    fromCache: result.fromCache,
    payload: {
      section: "series-folders-lite",
      activationCode: payload.activationCode,
      playlistUrlMasked: payload.playlistUrlMasked,
      updatedAt: payload.updatedAt,
      folderCount: liteFolders.length,
      itemCount: liteFolders.reduce((sum, folder) => sum + Number(folder.episodeCount || 0), 0),
      folders: liteFolders
    }
  };
}

async function getSeriesFolderByKey({ activationCode, key, autoRefresh = true }) {
  const safeKey = String(key || "").trim();

  if (!safeKey) {
    return {
      success: false,
      status: 400,
      message: "Falta key de carpeta."
    };
  }

  const result = await getCachedContentSection({
    activationCode,
    section: "series-folders",
    autoRefresh
  });

  if (!result.success) return result;

  const payload = result.payload || {};
  const folders = Array.isArray(payload.folders) ? payload.folders : [];
  const folder = folders.find((item) => String(item.key || "") === safeKey);

  if (!folder) {
    return {
      success: false,
      status: 404,
      message: "Carpeta no encontrada."
    };
  }

  const episodes = Array.isArray(folder.episodes) ? folder.episodes : [];

  return {
    success: true,
    status: 200,
    fromCache: result.fromCache,
    payload: {
      section: "series-folder",
      activationCode: payload.activationCode,
      playlistUrlMasked: payload.playlistUrlMasked,
      updatedAt: payload.updatedAt,
      folder: {
        key: folder.key,
        title: folder.title,
        group: folder.group,
        posterUrl: folder.posterUrl || null,
        episodeCount: episodes.length
      },
      items: episodes
    }
  };
}

async function getMovieCategoriesLite({ activationCode, autoRefresh = true }) {
  const result = await getCachedContentSection({
    activationCode,
    section: "movie-categories",
    autoRefresh
  });

  if (!result.success) return result;

  const payload = result.payload || {};
  const categories = Array.isArray(payload.categories) ? payload.categories : [];

  const liteCategories = categories.map((category) => ({
    key: category.key,
    title: category.title,
    itemCount: Number(category.itemCount || category.items?.length || 0)
  }));

  return {
    success: true,
    status: 200,
    fromCache: result.fromCache,
    payload: {
      section: "movie-categories-lite",
      activationCode: payload.activationCode,
      playlistUrlMasked: payload.playlistUrlMasked,
      updatedAt: payload.updatedAt,
      categoryCount: liteCategories.length,
      itemCount: liteCategories.reduce((sum, category) => sum + Number(category.itemCount || 0), 0),
      categories: liteCategories
    }
  };
}

async function getMovieCategoryByKey({ activationCode, key, autoRefresh = true }) {
  const safeKey = String(key || "").trim();

  if (!safeKey) {
    return {
      success: false,
      status: 400,
      message: "Falta key de categoría."
    };
  }

  const result = await getCachedContentSection({
    activationCode,
    section: "movie-categories",
    autoRefresh
  });

  if (!result.success) return result;

  const payload = result.payload || {};
  const categories = Array.isArray(payload.categories) ? payload.categories : [];
  const category = categories.find((item) => String(item.key || "") === safeKey);

  if (!category) {
    return {
      success: false,
      status: 404,
      message: "Categoría no encontrada."
    };
  }

  const items = Array.isArray(category.items) ? category.items : [];

  return {
    success: true,
    status: 200,
    fromCache: result.fromCache,
    payload: {
      section: "movie-category",
      activationCode: payload.activationCode,
      playlistUrlMasked: payload.playlistUrlMasked,
      updatedAt: payload.updatedAt,
      category: {
        key: category.key,
        title: category.title,
        itemCount: items.length
      },
      items
    }
  };
}


module.exports = {
  parseM3u,
  refreshContentCacheForClient,
  getCachedContentSection,
  getSeriesFoldersLite,
  getSeriesFolderByKey,
  getMovieCategoriesLite,
  getMovieCategoryByKey
};
