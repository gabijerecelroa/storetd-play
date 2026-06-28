const { supabase } = require("./db");

const CACHE_TABLE = "playlist_cache";

function xtreamNumber(obj, ...keys) {
  if (!obj) return 0;

  for (const key of keys) {
    const value = obj[key];
    if (value !== undefined && value !== null && value !== "") {
      const num = Number(value);
      if (!isNaN(num)) {
        return num;
      }
    }
  }
  return 0;
}

function xtreamString(obj, ...keys) {
  if (!obj) return "";

  for (const key of keys) {
    const value = obj[key];
    if (value !== undefined && value !== null) {
      const str = String(value).trim();
      if (str) return str;
    }
  }
  return "";
}

function xtreamGroupName(type, name) {
  // type puede ser "live", "movie", o "series"
  // name es el nombre original de la categoría (con emojis, etc.)
  return String(name || "").trim() || "Sin Categoria";
}

async function fetchXtreamJson(playlistUrl, action, extraParams = {}) {
  const url = new URL(playlistUrl);
  url.searchParams.set("action", action);

  for (const [key, value] of Object.entries(extraParams)) {
    url.searchParams.set(key, value);
  }

  const response = await fetch(url.toString(), {
    headers: {
      "User-Agent": "StoreTD-Play-Backend/1.0"
    }
  });

  if (!response.ok) {
    throw new Error(`Xtream action '${action}' failed with HTTP ${response.status}`);
  }

  return response.json();
}

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

// Removed parseM3u
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

function isAdult(item) { return false;
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

function isLiveTvGroup(item) {
  const group = normalizeText(item?.group || "");

  return group === "tv" ||
    group.startsWith("tv |") ||
    group.startsWith("tv|") ||
    group.startsWith("tv ") ||
    group.startsWith("canales") ||
    group.startsWith("en vivo") ||
    group.startsWith("live") ||
    group.startsWith("livetv");
}

function sectionOf(item) {
  if (isLiveTvGroup(item)) return "live";
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

// Removed fetchPlaylist
function splitSections(items) {
  const clean = uniqueByUrl(items);

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

function cleanSeriesBaseText(value) {
  return String(value || "")
    .replace(/^series\s*[|:/-]\s*/i, "")
    .replace(/^serie\s*[|:/-]\s*/i, "")
    .replace(/^temporadas\s*[|:/-]\s*/i, "")
    .replace(/^cap[ií]tulos\s*[|:/-]\s*/i, "")
    .replace(/\[[^\]]*\]/g, "")
    .replace(/\([^)]*\)/g, "")
    .replace(/\b(latino|castellano|subtitulado|dual audio|hd|fhd|4k|1080p|720p)\b/gi, "")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/^[\s\-|.:_]+|[\s\-|.:_]+$/g, "");
}

function extractSeriesTitleBeforeEpisode(value) {
  const raw = cleanSeriesBaseText(value);

  const patterns = [
    // Ej: ¡Baymax! S01E01, 24 S01E01, Los abandonados S2025E01
    /^(.+?)(?:\s+|[._\-|:]+)(?:[sS]\s*\d{1,4}\s*[eE]\s*\d{1,4})\b.*$/,

    // Ej: Serie T01E01
    /^(.+?)(?:\s+|[._\-|:]+)(?:[tT]\s*\d{1,4}\s*[eE]\s*\d{1,4})\b.*$/,

    // Ej: Serie 1x01
    /^(.+?)(?:\s+|[._\-|:]+)(?:\d{1,4}\s*x\s*\d{1,4})\b.*$/i,

    // Ej: Serie Temporada 1 / Season 1 / Episodio 1
    /^(.+?)(?:\s+|[._\-|:]+)temporada\s*\d{1,4}\b.*$/i,
    /^(.+?)(?:\s+|[._\-|:]+)season\s*\d{1,4}\b.*$/i,
    /^(.+?)(?:\s+|[._\-|:]+)cap[ií]tulo\s*\d{1,4}\b.*$/i,
    /^(.+?)(?:\s+|[._\-|:]+)episodio\s*\d{1,4}\b.*$/i,
    /^(.+?)(?:\s+|[._\-|:]+)episode\s*\d{1,4}\b.*$/i,
    /^(.+?)(?:\s+|[._\-|:]+)ep\s*\d{1,4}\b.*$/i
  ];

  for (const pattern of patterns) {
    const match = raw.match(pattern);

    if (match && match[1]) {
      const title = cleanSeriesBaseText(match[1]);

      if (title && !looksLikeGenericSeriesGroup(title)) {
        return title;
      }
    }
  }

  return "";
}

function cleanSeriesTitle(value, fallbackGroup = "") {
  const original = String(value || "").trim();

  const extracted = extractSeriesTitleBeforeEpisode(original);
  if (extracted) {
    return extracted;
  }

  let title = cleanSeriesBaseText(original);

  title = title
    .replace(/(?:^|[\s._\-|:!¡])(?:S|T)\s*\d{1,4}\s*E\s*\d{1,4}\b.*$/i, "")
    .replace(/(?:^|[\s._\-|:!¡])\d{1,4}\s*x\s*\d{1,4}\b.*$/i, "")
    .replace(/\btemporada\s*\d{1,4}\b.*$/i, "")
    .replace(/\bseason\s*\d{1,4}\b.*$/i, "")
    .replace(/\bcap[ií]tulo\s*\d{1,4}\b.*$/i, "")
    .replace(/\bepisodio\s*\d{1,4}\b.*$/i, "")
    .replace(/\bepisode\s*\d{1,4}\b.*$/i, "")
    .replace(/\bep\s*\d{1,4}\b.*$/i, "")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/^[\s\-|.:_]+|[\s\-|.:_]+$/g, "");

  if (title.length >= 1 && !looksLikeGenericSeriesGroup(title)) {
    return title;
  }

  if (fallbackGroup && !looksLikeGenericSeriesGroup(fallbackGroup)) {
    return fallbackGroup;
  }

  return original || String(fallbackGroup || "Sin título").trim();
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
    /\bmax\b/.test(text) ||
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
    /\bS\s*(\d{1,4})\s*E\s*\d{1,4}\b/i,
    /\bT\s*(\d{1,4})\s*E\s*\d{1,4}\b/i,
    /\b(\d{1,4})\s*x\s*\d{1,4}\b/i,
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
    /\bS\s*\d{1,4}\s*E\s*(\d{1,4})\b/i,
    /\bT\s*\d{1,4}\s*E\s*(\d{1,4})\b/i,
    /\b\d{1,4}\s*x\s*(\d{1,4})\b/i,
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


function forceCleanSeriesEpisodeTitle(value) {
  const raw = cleanSeriesBaseText(value)
    .replace(/[\u00a0\u2007\u202f]/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  if (!raw) return "";

  const markerPatterns = [
    /[sS]\s*\d{1,4}\s*[eE]\s*\d{1,4}\b/,
    /[tT]\s*\d{1,4}\s*[eE]\s*\d{1,4}\b/,
    /\d{1,4}\s*x\s*\d{1,4}\b/i,
    /temporada\s*\d{1,4}\b/i,
    /season\s*\d{1,4}\b/i,
    /cap[ií]tulo\s*\d{1,4}\b/i,
    /episodio\s*\d{1,4}\b/i,
    /episode\s*\d{1,4}\b/i,
    /\bep\s*\d{1,4}\b/i
  ];

  let bestIndex = -1;

  for (const pattern of markerPatterns) {
    const match = raw.match(pattern);

    if (match && typeof match.index === "number" && match.index > 0) {
      if (bestIndex === -1 || match.index < bestIndex) {
        bestIndex = match.index;
      }
    }
  }

  if (bestIndex > 0) {
    const title = cleanSeriesBaseText(raw.slice(0, bestIndex));

    if (title && !looksLikeGenericSeriesGroup(title)) {
      return title;
    }
  }

  return "";
}



function finalCleanSeriesFolderTitle(value, fallbackGroup = "") {
  const raw = String(value || "")
    .replace(/[\u00a0\u2007\u202f]/g, " ")
    .replace(/^series\s*[|:/-]\s*/i, "")
    .replace(/^serie\s*[|:/-]\s*/i, "")
    .replace(/\[[^\]]*\]/g, "")
    .replace(/\([^)]*\)/g, "")
    .replace(/\b(latino|castellano|subtitulado|dual audio|hd|fhd|4k|1080p|720p)\b/gi, "")
    .replace(/\s+/g, " ")
    .trim();

  const markerPatterns = [
    /[sS]\s*\d{1,4}\s*[eE]\s*\d{1,4}/,
    /[tT]\s*\d{1,4}\s*[eE]\s*\d{1,4}/,
    /\d{1,4}\s*x\s*\d{1,4}/i,
    /temporada\s*\d{1,4}/i,
    /season\s*\d{1,4}/i,
    /cap[ií]tulo\s*\d{1,4}/i,
    /episodio\s*\d{1,4}/i,
    /episode\s*\d{1,4}/i,
    /\bep\s*\d{1,4}/i
  ];

  let cutIndex = -1;

  for (const pattern of markerPatterns) {
    const match = raw.match(pattern);

    if (match && typeof match.index === "number" && match.index > 0) {
      if (cutIndex === -1 || match.index < cutIndex) {
        cutIndex = match.index;
      }
    }
  }

  let title = cutIndex > 0 ? raw.slice(0, cutIndex) : raw;

  title = title
    .replace(/[\s._\-|:]+$/g, "")
    .replace(/^[\s._\-|:]+/g, "")
    .replace(/\s+/g, " ")
    .trim();

  if (title && !looksLikeGenericSeriesGroup(title)) {
    return title;
  }

  const fallback = String(fallbackGroup || "").trim();

  if (fallback && !looksLikeGenericSeriesGroup(fallback)) {
    return fallback;
  }

  return raw || fallback || "Sin título";
}

function finalSeriesFolderTitleFromItem(item) {
  const byName = finalCleanSeriesFolderTitle(item?.name || "", "");

  if (byName && !looksLikeGenericSeriesGroup(byName)) {
    return byName;
  }

  const byGroup = finalCleanSeriesFolderTitle(item?.group || "", "");

  if (byGroup && !looksLikeGenericSeriesGroup(byGroup)) {
    return byGroup;
  }

  return cleanSeriesTitle(item?.name || item?.group || "Sin título", item?.group || "");
}




function finalMergeSeriesTitleV9(value, fallbackGroup = "") {
  const raw = String(value || "")
    .replace(/[\u00a0\u2007\u202f]/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  if (!raw) return String(fallbackGroup || "").trim() || "Sin título";

  const tokens = raw.split(/\s+/);
  const markerIndex = tokens.findIndex((token) => {
    const clean = String(token || "")
      .replace(/^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$/g, "")
      .toLowerCase();

    return /^s\d{1,4}e\d{1,4}$/.test(clean) ||
      /^t\d{1,4}e\d{1,4}$/.test(clean) ||
      /^\d{1,4}x\d{1,4}$/.test(clean);
  });

  if (markerIndex > 0) {
    const title = tokens
      .slice(0, markerIndex)
      .join(" ")
      .replace(/[\s._\-|:]+$/g, "")
      .trim();

    if (title && !looksLikeGenericSeriesGroup(title)) {
      return title;
    }
  }

  const fallback = finalCleanSeriesFolderTitle(raw, fallbackGroup);

  return fallback || raw;
}


function hardCleanGeneratedSeriesTitle(value, fallbackGroup = "") {
  const raw = String(value || "")
    .replace(/[\u00a0\u2007\u202f]/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  if (!raw) {
    return String(fallbackGroup || "").trim() || "Sin título";
  }

  const patterns = [
    /\s+[sS]\d{1,4}\s*[eE]\d{1,4}\b.*$/,
    /\s+[tT]\d{1,4}\s*[eE]\d{1,4}\b.*$/,
    /\s+\d{1,4}\s*x\s*\d{1,4}\b.*$/i,
    /\s+temporada\s*\d{1,4}\b.*$/i,
    /\s+season\s*\d{1,4}\b.*$/i,
    /\s+cap[ií]tulo\s*\d{1,4}\b.*$/i,
    /\s+episodio\s*\d{1,4}\b.*$/i,
    /\s+episode\s*\d{1,4}\b.*$/i,
    /\s+ep\s*\d{1,4}\b.*$/i
  ];

  for (const pattern of patterns) {
    const cleaned = raw
      .replace(pattern, "")
      .replace(/[\s._\-|:]+$/g, "")
      .trim();

    if (cleaned && cleaned !== raw && !looksLikeGenericSeriesGroup(cleaned)) {
      return cleaned;
    }
  }

  return finalCleanSeriesFolderTitle(raw, fallbackGroup) || raw;
}


function hardCleanSeriesTitleV13(value, fallbackGroup = "") {
  const raw = String(value || "")
    .replace(/[\u00a0\u2007\u202f]/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  if (!raw) {
    return String(fallbackGroup || "").trim() || "Sin título";
  }

  const directCleaned = raw
    .replace(/\s+[sS]\s*\d{1,4}\s*[eE]\s*\d{1,4}\b.*$/, "")
    .replace(/\s+[tT]\s*\d{1,4}\s*[eE]\s*\d{1,4}\b.*$/, "")
    .replace(/\s+\d{1,4}\s*x\s*\d{1,4}\b.*$/i, "")
    .replace(/\s+temporada\s*\d{1,4}\b.*$/i, "")
    .replace(/\s+season\s*\d{1,4}\b.*$/i, "")
    .replace(/\s+cap[ií]tulo\s*\d{1,4}\b.*$/i, "")
    .replace(/\s+episodio\s*\d{1,4}\b.*$/i, "")
    .replace(/\s+episode\s*\d{1,4}\b.*$/i, "")
    .replace(/\s+ep\s*\d{1,4}\b.*$/i, "")
    .replace(/[\s._\-|:]+$/g, "")
    .trim();

  if (
    directCleaned &&
    directCleaned !== raw &&
    !looksLikeGenericSeriesGroup(directCleaned)
  ) {
    return directCleaned;
  }

  const tokens = raw.split(/\s+/);
  const markerIndex = tokens.findIndex((token) => {
    const clean = String(token || "")
      .replace(/^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$/g, "")
      .toLowerCase();

    return /^s\d{1,4}e\d{1,4}$/.test(clean) ||
      /^t\d{1,4}e\d{1,4}$/.test(clean) ||
      /^\d{1,4}x\d{1,4}$/.test(clean);
  });

  if (markerIndex > 0) {
    const tokenCleaned = tokens
      .slice(0, markerIndex)
      .join(" ")
      .replace(/[\s._\-|:]+$/g, "")
      .trim();

    if (tokenCleaned && !looksLikeGenericSeriesGroup(tokenCleaned)) {
      return tokenCleaned;
    }
  }

  const fallback = String(fallbackGroup || "").trim();

  if (fallback && !looksLikeGenericSeriesGroup(fallback)) {
    return fallback;
  }

  return raw;
}


function mergeGeneratedSeriesFolders(folders) {
  const merged = new Map();

  for (const folder of folders || []) {
    const title = hardCleanSeriesTitleV13(folder.title, folder.group);
    const key = slugKey(title) || folder.key || slugKey(folder.title);

    if (!merged.has(key)) {
      merged.set(key, {
        ...folder,
        key,
        title,
        group: folder.group || "Series",
        posterUrl: folder.posterUrl || null,
        episodes: []
      });
    }

    const target = merged.get(key);

    if (!target.posterUrl && folder.posterUrl) {
      target.posterUrl = folder.posterUrl;
    }

    const episodes = Array.isArray(folder.episodes) ? folder.episodes : [];

    target.episodes.push(...episodes.map((episode) => ({
      ...episode,
      group: title
    })));
  }

  return Array.from(merged.values())
    .map((folder) => {
      const unique = new Map();

      for (const episode of folder.episodes || []) {
        const key = episode.streamUrl || `${episode.name}|${episode.season}|${episode.episode}`;

        if (!unique.has(key)) {
          unique.set(key, episode);
        }
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
}



function buildSeriesFoldersPayload({ activationCode, playlistUrl, items }) {
  const foldersMap = new Map();

  for (const item of items) {
    const title = finalSeriesFolderTitleFromItem(item);
    const key = slugKey(title) || slugKey(item.group) || slugKey(item.name);

    if (!key) continue;

    if (!foldersMap.has(key)) {
      foldersMap.set(key, {
        key,
        title,
        group: item.group || "Series",
        logoUrl: item.cover || item.stream_icon || item.logoUrl || null,
        posterUrl: item.cover || item.stream_icon || item.logoUrl || null,
        episodeCount: 0,
        episodes: []
      });
    }

    const folder = foldersMap.get(key);

    if (!folder.logoUrl && (item.cover || item.stream_icon || item.logoUrl)) {
      folder.logoUrl = item.cover || item.stream_icon || item.logoUrl;
      folder.posterUrl = item.cover || item.stream_icon || item.logoUrl;
    }

    folder.episodes.push({
      id: item.id || slugKey(`${item.name}|${item.streamUrl}`),
      name: item.name,
      streamUrl: item.streamUrl,
      logoUrl: item.cover || item.stream_icon || item.logoUrl || null,
        posterUrl: item.cover || item.stream_icon || item.logoUrl || null,
      group: folder.title,
      tvgId: item.tvgId || null,
      season: episodeSeason(item.name),
      episode: episodeNumber(item.name)
    });
  }

  const mergedFoldersMap = new Map();

  for (const folder of foldersMap.values()) {
    const mergedTitle = finalCleanSeriesFolderTitle(folder.title, folder.group);

    const mergedKey = slugKey(mergedTitle) || folder.key;

    if (!mergedFoldersMap.has(mergedKey)) {
      mergedFoldersMap.set(mergedKey, {
        ...folder,
        key: mergedKey,
        title: mergedTitle || folder.title,
        group: folder.group || "Series",
        episodes: []
      });
    }

    const mergedFolder = mergedFoldersMap.get(mergedKey);

    if (!mergedFolder.posterUrl && folder.posterUrl) {
      mergedFolder.posterUrl = folder.posterUrl;
    }

    mergedFolder.episodes.push(...folder.episodes);
  }

  let folders = Array.from(mergedFoldersMap.values())
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

  folders = mergeGeneratedSeriesFolders(folders);

  return {
    section: "series-folders",
    groupingVersion: "series-direct-clean-v13",
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


// XTREAM_SOURCE_START
const xtreamSeriesInfoMemoryCache = new Map();

function isXtreamContentMode() {
  return true;
}

function xtreamConfig(playlistUrl) {
  if (playlistUrl) {
    try {
      const url = new URL(playlistUrl);
      const baseUrl = url.origin;
      const username = url.searchParams.get("username") || "";
      const password = url.searchParams.get("password") || "";
      return { baseUrl, username, password };
    } catch(e) {}
  }
  const baseUrl = String(process.env.XTREAM_BASE_URL || process.env.XTREAM_BASE || "http://tv.m3uts.xyz").trim().replace(/\/+$/, "");
  const username = String(process.env.XTREAM_USERNAME || process.env.XTREAM_USER || "m").trim();
  const password = String(process.env.XTREAM_PASSWORD || process.env.XTREAM_PASS || "m").trim();

  if (!baseUrl || !username || !password) {
    throw new Error("Xtream no configurado. Revisa XTREAM_BASE_URL, XTREAM_USERNAME y XTREAM_PASSWORD.");
  }

  return { baseUrl, username, password };
}

function xtreamSourceUrlMasked(playlistUrl) {
  const { baseUrl } = xtreamConfig(playlistUrl);
  return baseUrl;
}

function xtreamLiveUrl(playlistUrl, streamId, ext = "ts") {
  const { baseUrl, username, password } = xtreamConfig(playlistUrl);
  return `${baseUrl}/live/${username}/${password}/${streamId}.m3u8`;
}

function xtreamMovieUrl(playlistUrl, streamId, ext = "mp4") {
  const { baseUrl, username, password } = xtreamConfig(playlistUrl);
  return baseUrl + "/movie/" + username + "/" + password + "/" + streamId + "." + ext;
}

function xtreamSeriesEpisodeUrl(playlistUrl, episodeId, ext = "mp4") {
  const { baseUrl, username, password } = xtreamConfig(playlistUrl);
  return baseUrl + "/series/" + username + "/" + password + "/" + episodeId + "." + ext;
}

function normalizeXtreamLiveItems(playlistUrl, rows, categoryMap) {
  if (!Array.isArray(rows)) return [];

  return rows
    .map((row) => {
      const streamId = xtreamNumber(row, "stream_id", "id");
      if (!streamId) return null;

      const categoryId = xtreamString(row, "category_id");
      const category = xtreamCategoryName(categoryMap, categoryId, "Sin Categoria");

      const ext = xtreamString(row, "container_extension") || "ts";

      return {
        id: String(streamId),
        name: xtreamString(row, "name", "title") || `Canal ${streamId}`,
        streamUrl: xtreamLiveUrl(playlistUrl, streamId, ext),
        logoUrl: xtreamString(row, "stream_icon", "cover", "image") || null,
        group: xtreamGroupName("live", category), // El nombre con emojis se mantiene para la TV
        tvgId: xtreamString(row, "epg_channel_id", "tvg_id") || null,
        source: {
          provider: "xtream",
          streamId,
          categoryId
        }
      };
    })
    .filter(Boolean);
}

function xtreamCategoryIds(row) {
  return String(row?.category_id || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function xtreamCategoryMatches(row, categoryId) {
  const safeCategoryId = String(categoryId || "").trim();
  if (!safeCategoryId) return true;
  return xtreamCategoryIds(row).includes(safeCategoryId);
}

function xtreamCategoryMap(categories) {
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

function xtreamCategoryName(categoryMap, categoryId, fallback = "Sin Categoria") {
  return categoryMap.get(String(categoryId)) || fallback;
}

function buildXtreamMovieCategoriesPayload({ activationCode, playlistUrl, rows, categoryMap }) {
  const categoriesById = new Map();

  if (Array.isArray(rows)) {
    for (const row of rows) {
      const categoryIds = xtreamCategoryIds(row);
      const ids = categoryIds.length ? categoryIds : [""];

      for (const categoryId of ids) {
        const rawTitle = xtreamCategoryName(categoryMap, categoryId, "Sin Categoria");
        const title = xtreamGroupName("movie", rawTitle);
        const keyBase = slugKey(title) || "sin-categoria";
        const key = categoryId ? `${keyBase}-${categoryId}` : keyBase;

        if (!categoriesById.has(key)) {
          categoriesById.set(key, {
            key,
            title,
            itemCount: 0,
            items: [],
            source: {
              provider: "xtream",
              categoryId
            }
          });
        }

        categoriesById.get(key).itemCount += 1;
      }
    }
  }

  const categories = Array.from(categoriesById.values())
    .filter((category) => Number(category.itemCount || 0) > 0)
    .sort((a, b) => String(a.title).localeCompare(String(b.title)));

  return {
    section: "movie-categories",
    groupingVersion: "xtream-movies-lazy-v1",
    activationCode,
    playlistUrlMasked: maskUrl(playlistUrl),
    updatedAt: new Date().toISOString(),
    categoryCount: categories.length,
    itemCount: categories.reduce((sum, category) => sum + Number(category.itemCount || 0), 0),
    categories
  };
}


function normalizeXtreamMovieItems(playlistUrl, rows, categoryMap) {
  if (!Array.isArray(rows)) return [];

  return rows
    .map((row) => {
      const streamId = xtreamNumber(row, "stream_id", "id");
      if (!streamId) return null;

      const categoryId = xtreamString(row, "category_id");
      const category = xtreamCategoryName(categoryMap, categoryId, "Sin Categoria");
      const ext = xtreamString(row, "container_extension") || "mp4";

      return {
        id: String(streamId),
        name: xtreamString(row, "name", "title") || `Pelicula ${streamId}`,
        streamUrl: xtreamMovieUrl(playlistUrl, streamId, ext),
        logoUrl: xtreamString(row, "stream_icon", "cover", "image") || null,
        group: xtreamGroupName("movie", category),
        tvgId: null,
        source: {
          provider: "xtream",
          streamId,
          categoryId,
          extension: ext
        }
      };
    })
    .filter(Boolean);
}

function buildXtreamSeriesFoldersPayload({ activationCode, playlistUrl, rows, categoryMap }) {
  const folders = [];

  if (Array.isArray(rows)) {
    for (const row of rows) {
      const seriesId = xtreamNumber(row, "series_id", "id");
      if (!seriesId) continue;

      const title = xtreamString(row, "name", "title") || `Serie ${seriesId}`;
      const categoryId = xtreamString(row, "category_id");
      const category = xtreamCategoryName(categoryMap, categoryId, "Sin Categoria");
      const baseKey = slugKey(title) || "serie";
      const key = `${baseKey}-${seriesId}`;

      folders.push({
        key,
        title,
        group: xtreamGroupName("series", category),
        posterUrl: xtreamString(row, "cover", "stream_icon", "image") || null,
        episodeCount: Number(row.episode_count || row.episodes_count || row.episodes || 1) || 1,
        episodes: [],
        source: {
          provider: "xtream",
          seriesId,
          categoryId
        }
      });
    }
  }

  folders.sort((a, b) => String(a.title).localeCompare(String(b.title)));

  return {
    section: "series-folders",
    groupingVersion: "xtream-series-lazy-v1",
    activationCode,
    playlistUrlMasked: maskUrl(playlistUrl),
    updatedAt: new Date().toISOString(),
    folderCount: folders.length,
    itemCount: folders.reduce((sum, folder) => sum + Number(folder.episodeCount || 0), 0),
    folders
  };
}

function flattenXtreamEpisodes(info) {
  const episodes = info?.episodes;
  const result = [];

  if (Array.isArray(episodes)) {
    for (const episode of episodes) {
      if (episode && typeof episode === "object") {
        result.push(episode);
      }
    }

    return result;
  }

  if (episodes && typeof episodes === "object") {
    for (const [seasonKey, list] of Object.entries(episodes)) {
      if (!Array.isArray(list)) continue;

      for (const episode of list) {
        if (episode && typeof episode === "object") {
          result.push({
            ...episode,
            _season: seasonKey
          });
        }
      }
    }
  }

  return result;
}

function xtreamEpisodeName(folderTitle, episode) {
  const direct = xtreamString(episode, "title", "name");

  if (direct) return direct;

  const season = xtreamString(episode, "season", "_season") || "1";
  const episodeNumberValue = xtreamString(episode, "episode_num", "episode", "episode_number") || "1";

  return `${folderTitle} S${String(season).padStart(2, "0")}E${String(episodeNumberValue).padStart(2, "0")}`;
}

async function getXtreamEpisodesForSeriesFolder(folder, playlistUrl) {
  const seriesId = Number(folder?.source?.seriesId || 0);

  if (!seriesId) return [];

  const cacheKey = String(seriesId);
  let info = xtreamSeriesInfoMemoryCache.get(cacheKey);

  if (!info) {
    info = await fetchXtreamJson(playlistUrl, "get_series_info", { series_id: seriesId });
    xtreamSeriesInfoMemoryCache.set(cacheKey, info);
  }

  const rawEpisodes = flattenXtreamEpisodes(info);

  return rawEpisodes
    .map((episode) => {
      const episodeId = xtreamNumber(episode, "id", "episode_id");
      if (!episodeId) return null;

      const infoObject = episode.info && typeof episode.info === "object" ? episode.info : {};
      const ext =
        xtreamString(episode, "container_extension") ||
        xtreamString(infoObject, "container_extension") ||
        "mp4";

      const logo =
        xtreamString(infoObject, "movie_image", "cover", "image") ||
        xtreamString(episode, "cover", "image", "stream_icon") ||
        folder.posterUrl ||
        null;

      const season = Number(xtreamString(episode, "season", "_season") || 0) || episodeSeason(xtreamEpisodeName(folder.title, episode));
      const episodeIndex = Number(xtreamString(episode, "episode_num", "episode", "episode_number") || 0) || episodeNumber(xtreamEpisodeName(folder.title, episode));

      return {
        id: String(episodeId),
        name: xtreamEpisodeName(folder.title, episode),
        streamUrl: xtreamSeriesEpisodeUrl(playlistUrl, episodeId, ext),
        logoUrl: logo || item.stream_icon || item.cover,
        group: folder.title,
        tvgId: null,
        season,
        episode: episodeIndex
      };
    })
    .filter(Boolean)
    .sort((a, b) => {
      return (a.season - b.season) ||
        (a.episode - b.episode) ||
        String(a.name).localeCompare(String(b.name));
    });
}

async function refreshXtreamContentCacheForClient({ activationCode, refreshSection, shouldRefresh, playlistUrl }) {
  var playlistUrlMasked = xtreamSourceUrlMasked(playlistUrl);
  const counts = {};
  const tasks = [];

  if (shouldRefresh("live")) {
    const [liveCategories, liveRows] = await Promise.all([
      fetchXtreamJson(playlistUrl, "get_live_categories"),
      fetchXtreamJson(playlistUrl, "get_live_streams")
    ]);

    const liveItems = normalizeXtreamLiveItems(playlistUrl, liveRows, xtreamCategoryMap(liveCategories));

    tasks.push(
      saveSectionCache({
        activationCode,
        playlistUrl: playlistUrlMasked,
        section: "live",
        items: liveItems
      }).then((payload) => {
        counts.live = payload.itemCount;
      })
    );
  }

  if (shouldRefresh("movies")) {
    const [movieCategories, movieRows] = await Promise.all([
      fetchXtreamJson(playlistUrl, "get_vod_categories"),
      fetchXtreamJson(playlistUrl, "get_vod_streams")
    ]);

    const movieCategoryMap = xtreamCategoryMap(movieCategories);
    const movieCategoriesPayload = buildXtreamMovieCategoriesPayload({
      activationCode,
      playlistUrl,
      rows: movieRows,
      categoryMap: movieCategoryMap
    });

    counts.movies = Number(movieCategoriesPayload.itemCount || 0);

    // En modo Xtream no guardamos la lista plana de peliculas para evitar timeout.
    // Las peliculas se cargan lazy cuando se abre cada categoria.
    tasks.push(
      saveSectionCache({
        activationCode,
        playlistUrl: playlistUrlMasked,
        section: "movies",
        items: []
      })
    );

    tasks.push(
      saveRawPayloadCache({
        activationCode,
        playlistUrl: playlistUrlMasked,
        section: "movie-categories",
        payload: movieCategoriesPayload
      }).then((payload) => {
        counts.movieCategories = payload.categoryCount;
      })
    );
  }

  if (shouldRefresh("series")) {
    const [seriesCategories, seriesRows] = await Promise.all([
      fetchXtreamJson(playlistUrl, "get_series_categories"),
      fetchXtreamJson(playlistUrl, "get_series")
    ]);

    const seriesFoldersPayload = buildXtreamSeriesFoldersPayload({
      activationCode,
      playlistUrl,
      rows: seriesRows,
      categoryMap: xtreamCategoryMap(seriesCategories)
    });

    counts.series = Number(seriesFoldersPayload.folderCount || 0);

    tasks.push(
      saveRawPayloadCache({
        activationCode,
        playlistUrl: playlistUrlMasked,
        section: "series-folders",
        payload: seriesFoldersPayload
      }).then((payload) => {
        counts.seriesFolders = payload.folderCount;
      })
    );
  }

  await Promise.all(tasks);

  return {
    success: true,
    activationCode,
    sourceMode: "xtream",
    section: refreshSection,
    counts,
    updatedAt: new Date().toISOString()
  };
}
// XTREAM_SOURCE_END


async function refreshContentCacheForClient(activationCode, options = {}) {
  const code = normalizeCode(activationCode);
  const requestedSection = String(options.section || options.sections || "all")
    .trim()
    .toLowerCase();

  const allowedSections = new Set(["all", "live", "movies", "series"]);
  const refreshSection = allowedSections.has(requestedSection)
    ? requestedSection
    : "all";

  const shouldRefresh = (section) => {
    if (refreshSection === "all") return true;
    return refreshSection === section;
  };

  const client = await getClientByActivationCode(code);
  const invalidReason = validateClient(client);

  if (invalidReason) {
    return {
      success: false,
      message: invalidReason
    };
  }

  return await refreshXtreamContentCacheForClient({
    activationCode: code,
    refreshSection,
    shouldRefresh,
    playlistUrl: client.playlist_url
  });
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




function filterAdultItems(items, includeAdult) {
  if (includeAdult) return items || [];
  return (items || []).filter((item) => !isAdult(item));
}

function isAdultLiteEntry(entry) { return false;
  const text = normalizeText([
    entry?.title || "",
    entry?.name || "",
    entry?.group || "",
    entry?.key || ""
  ].join(" "));

  return adultWords.some((word) => {
    const normalizedWord = normalizeText(word);
    if (!normalizedWord) return false;

    if (normalizedWord === "sex") {
      return /\bsex\b/.test(text);
    }

    if (normalizedWord === "hot") {
      return /\bhot\b/.test(text);
    }

    return text.includes(normalizedWord);
  });
}

function filterPayloadAdultContent(payload, includeAdult) {
  if (includeAdult || !payload) return payload;

  if (Array.isArray(payload.items)) {
    const items = filterAdultItems(payload.items, false);

    return {
      ...payload,
      items,
      itemCount: items.length,
      groups: groupNames(items)
    };
  }

  if (Array.isArray(payload.folders)) {
    const folders = payload.folders
      .map((folder) => {
        if (Array.isArray(folder.episodes)) {
          const episodes = filterAdultItems(folder.episodes, false);

          return {
            ...folder,
            episodes,
            episodeCount: episodes.length
          };
        }

        return folder;
      })
      .filter((folder) => {
        if (Array.isArray(folder.episodes)) {
          return Number(folder.episodeCount || 0) > 0;
        }

        return !isAdultLiteEntry(folder);
      });

    return {
      ...payload,
      folders,
      folderCount: folders.length,
      itemCount: folders.reduce(
        (sum, folder) => sum + Number(folder.episodeCount || 0),
        0
      )
    };
  }

  if (Array.isArray(payload.categories)) {
    const categories = payload.categories
      .map((category) => {
        if (Array.isArray(category.items)) {
          const items = filterAdultItems(category.items, false);

          return {
            ...category,
            items,
            itemCount: items.length
          };
        }

        return category;
      })
      .filter((category) => {
        if (Array.isArray(category.items)) {
          return Number(category.itemCount || 0) > 0;
        }

        return !isAdultLiteEntry(category);
      });

    return {
      ...payload,
      categories,
      categoryCount: categories.length,
      itemCount: categories.reduce(
        (sum, category) => sum + Number(category.itemCount || 0),
        0
      )
    };
  }

  return payload;
}




function findSeriesFolderKeyByTitle(sourceFolders, title, fallbackKey = "") {
  const wanted = normalizeText(title);

  if (!wanted) return fallbackKey;

  const exact = (sourceFolders || []).find((folder) => {
    return normalizeText(folder?.title) === wanted;
  });

  if (exact?.key) return exact.key;

  const loose = (sourceFolders || []).find((folder) => {
    const current = normalizeText(folder?.title);
    return current && (
      current.includes(wanted) ||
      wanted.includes(current)
    );
  });

  return loose?.key || fallbackKey;
}


function shouldPreserveXtreamSeriesFolders(payload) {
  return isXtreamContentMode() ||
    String(payload?.groupingVersion || "").startsWith("xtream-");
}

function normalizeSeriesFoldersForPayload(payload) {
  const sourceFolders = Array.isArray(payload?.folders) ? payload.folders : [];

  if (shouldPreserveXtreamSeriesFolders(payload)) {
    return sourceFolders;
  }

  return mergeGeneratedSeriesFolders(sourceFolders);
}


async function getSeriesFoldersLite({ activationCode, autoRefresh = true }) {
  const result = await getCachedContentSection({
    activationCode,
    section: "series-folders",
    autoRefresh
  });

  if (!result.success) return result;

  const payload = result.payload || {};

  console.log("[BACKEND] getSeriesFoldersLite → activationCode:", activationCode);
  console.log("[BACKEND] getSeriesFoldersLite → folders count:", Array.isArray(payload?.folders) ? payload.folders.length : 0);
  if (Array.isArray(payload?.folders) && payload.folders.length > 0) {
    console.log("[BACKEND] Primer folder posterUrl:", payload.folders[0].posterUrl || "SIN POSTER");
  }
  if (shouldPreserveXtreamSeriesFolders(payload)) {
    const sourceFolders = Array.isArray(payload.folders) ? payload.folders : [];

    const liteFolders = sourceFolders.map((folder) => ({
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
        groupingVersion: "xtream-fast-lite-v1",
        activationCode: payload.activationCode,
        playlistUrlMasked: payload.playlistUrlMasked,
        updatedAt: payload.updatedAt,
        folderCount: liteFolders.length,
        itemCount: liteFolders.reduce((sum, folder) => sum + Number(folder.episodeCount || 0), 0),
        folders: liteFolders
      }
    };
  }

  const sourceFolders = Array.isArray(payload.folders) ? payload.folders : [];
  const liteMap = new Map();

  const cleanLiteTitle = (value, fallbackGroup = "") => {
    const raw = String(value || "")
      .replace(/[\u00a0\u2007\u202f]/g, " ")
      .replace(/\s+/g, " ")
      .trim();

    const cleaned = raw
      .replace(/\s+[sS][0-9]{1,4}\s*[eE][0-9]{1,4}\b.*$/, "")
      .replace(/\s+[tT][0-9]{1,4}\s*[eE][0-9]{1,4}\b.*$/, "")
      .replace(/\s+[0-9]{1,4}\s*x\s*[0-9]{1,4}\b.*$/i, "")
      .replace(/\s+temporada\s*[0-9]{1,4}\b.*$/i, "")
      .replace(/\s+season\s*[0-9]{1,4}\b.*$/i, "")
      .replace(/\s+cap[ií]tulo\s*[0-9]{1,4}\b.*$/i, "")
      .replace(/\s+episodio\s*[0-9]{1,4}\b.*$/i, "")
      .replace(/\s+episode\s*[0-9]{1,4}\b.*$/i, "")
      .replace(/\s+ep\s*[0-9]{1,4}\b.*$/i, "")
      .replace(/[\s._\-|:]+$/g, "")
      .trim();

    if (cleaned && cleaned !== raw && !looksLikeGenericSeriesGroup(cleaned)) {
      return cleaned;
    }

    const fallback = String(fallbackGroup || "").trim();

    if (fallback && !looksLikeGenericSeriesGroup(fallback)) {
      return fallback;
    }

    return raw || fallback || "Sin título";
  };

  const isSeriesLiteSourceGroup = (value) => {
    const normalized = normalizeText(value || "");
    return normalized === "series" || normalized.startsWith("series ");
  };

  for (const folder of sourceFolders) {
    if (!isSeriesLiteSourceGroup(folder.group)) {
      continue;
    }

    const title = cleanLiteTitle(folder.title, folder.group);
    const key = slugKey(title) || folder.key || slugKey(folder.title);
    const episodeCount = Number(folder.episodeCount || folder.episodes?.length || 0);

    if (!liteMap.has(key)) {
      liteMap.set(key, {
        key,
        title,
        group: folder.group,
        posterUrl: folder.posterUrl || null,
        episodeCount: 0
      });
    }

    const current = liteMap.get(key);
    current.episodeCount += episodeCount;

    if (!current.posterUrl && folder.posterUrl) {
      current.posterUrl = folder.posterUrl;
    }
  }

  const liteFolders = Array.from(liteMap.values())
    .filter((folder) => Number(folder.episodeCount || 0) > 0)
    .sort((a, b) => String(a.title).localeCompare(String(b.title)));

  const sourceFoldersForKeys = Array.isArray(payload.folders) ? payload.folders : [];
  const responseLiteFolders = shouldPreserveXtreamSeriesFolders(payload)
    ? liteFolders.map((folder) => ({
        ...folder,
        key: findSeriesFolderKeyByTitle(sourceFoldersForKeys, folder.title, folder.key)
      }))
    : liteFolders;

  return {
    success: true,
    status: 200,
    fromCache: result.fromCache,
    payload: {
      section: "series-folders-lite",
      groupingVersion: "series-lite-endpoint-v15-series-only",
      activationCode: payload.activationCode,
      playlistUrlMasked: payload.playlistUrlMasked,
      updatedAt: payload.updatedAt,
      folderCount: responseLiteFolders.length,
      itemCount: responseLiteFolders.reduce((sum, folder) => sum + Number(folder.episodeCount || 0), 0),
      folders: responseLiteFolders
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
  const folders = normalizeSeriesFoldersForPayload(payload);
  const folder = folders.find((item) => String(item.key || "") === safeKey);

  if (!folder) {
    return {
      success: false,
      status: 404,
      message: "Carpeta no encontrada."
    };
  }

  let episodes = Array.isArray(folder.episodes) ? folder.episodes : [];

  if (isXtreamContentMode() && folder?.source?.provider === "xtream") {
    const client = await getClientByActivationCode(activationCode);
    episodes = await getXtreamEpisodesForSeriesFolder(folder, client.playlist_url);
  }

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

  let items = Array.isArray(category.items) ? category.items : [];

  if (isXtreamContentMode() && category?.source?.provider === "xtream") {
    const categoryId = String(category.source.categoryId || "").trim();
    const client = await getClientByActivationCode(activationCode);
    const playlistUrl = client.playlist_url;
    const movieRows = await fetchXtreamJson(playlistUrl, "get_vod_streams");
    const filteredRows = Array.isArray(movieRows)
      ? movieRows.filter((row) => xtreamCategoryMatches(row, categoryId))
      : [];

    const categoryMap = new Map([[categoryId, category.title]]);
    items = normalizeXtreamMovieItems(playlistUrl, filteredRows, categoryMap)
      .map((item) => ({
        ...item,
        group: category.title
      }));
  }

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


async function searchContentItems({
  activationCode,
  section = "movies",
  query = "",
  limit = 80,
  autoRefresh = true
}) {
  const safeSection = String(section || "movies").trim().toLowerCase();
  const safeQuery = String(query || "").trim();
  const safeLimit = Math.max(1, Math.min(Number(limit || 80), 150));

  if (!safeQuery) {
    return {
      success: true,
      status: 200,
      fromCache: true,
      payload: {
        section: "search",
        searchSection: safeSection,
        query: safeQuery,
        itemCount: 0,
        items: []
      }
    };
  }

  if (!["live", "movies", "series"].includes(safeSection)) {
    return {
      success: false,
      status: 400,
      message: "Seccion invalida."
    };
  }

  const result = await getCachedContentSection({
    activationCode,
    section: safeSection,
    autoRefresh
  });

  if (!result.success) return result;

  const payload = result.payload || {};
  const items = Array.isArray(payload.items) ? payload.items : [];
  const needle = normalizeText(safeQuery);

  const found = [];

  for (const item of items) {
    const haystack = normalizeText([
      item?.name || "",
      item?.title || "",
      item?.group || "",
      item?.tvgId || ""
    ].join(" "));

    if (haystack.includes(needle)) {
      found.push(item);
      if (found.length >= safeLimit) break;
    }
  }

  return {
    success: true,
    status: 200,
    fromCache: result.fromCache,
    payload: {
      section: "search",
      searchSection: safeSection,
      query: safeQuery,
      activationCode: payload.activationCode,
      playlistUrlMasked: payload.playlistUrlMasked,
      updatedAt: payload.updatedAt,
      itemCount: found.length,
      items: found
    }
  };
}



// SMARTONE_XTREAM_START
function m3uAttrEscape(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "")
    .replace(/>/g, "");
}

function m3uDisplayName(value) {
  return String(value || "Sin nombre")
    .replace(/\r/g, " ")
    .replace(/\n/g, " ")
    .trim() || "Sin nombre";
}

function smartoneM3uLineForItem(item, type) {
  const name = m3uDisplayName(item.name);
  const tvgType = type || item.type || "live";
  const logo = item.logoUrl || "";
  const group = item.group || "Sin categoría";
  const tvgId = item.tvgId || "";

  return [
    `#EXTINF:-1 tvg-type="${m3uAttrEscape(tvgType)}" tvg-id="${m3uAttrEscape(tvgId)}" tvg-name="${m3uAttrEscape(name)}" tvg-logo="${m3uAttrEscape(logo)}" group-title="${m3uAttrEscape(group)}",${name}`,
    item.streamUrl || ""
  ].join("\n");
}


function smartoneIncludeSeriesEnabled() {
  return ["1", "true", "yes", "si"].includes(
    String(process.env.SMARTONE_INCLUDE_SERIES || "")
      .trim()
      .toLowerCase()
  );
}

function smartoneSeriesMaxEpisodes() {
  const value = Number(process.env.SMARTONE_SERIES_MAX_EPISODES || process.env.SMARTONE_SERIES_LIMIT || 30000);
  return Number.isFinite(value) && value >= 0 ? value : 30000;
}

function smartoneSeriesConcurrency() {
  const value = Number(process.env.SMARTONE_SERIES_CONCURRENCY || 6);
  if (!Number.isFinite(value)) return 6;
  return Math.max(1, Math.min(12, Math.floor(value)));
}

function smartoneSeriesEpisodeName(folder, episode) {
  const folderTitle = String(folder?.title || "").trim();
  const episodeName = String(episode?.name || "").trim();

  if (!folderTitle) return episodeName || "Episodio";
  if (!episodeName) return folderTitle;

  const cleanFolder = normalizeText(folderTitle);
  const cleanEpisode = normalizeText(episodeName);

  if (cleanEpisode.includes(cleanFolder.slice(0, 12))) {
    return episodeName;
  }

  return `${folderTitle} - ${episodeName}`;
}

function smartoneSeriesItemFromEpisode(folder, episode) {
  return {
    type: "series",
    name: smartoneSeriesEpisodeName(folder, episode),
    streamUrl: episode.streamUrl,
    logoUrl: episode.logoUrl || folder.posterUrl || "" || item.stream_icon || item.cover,
    group: folder.group || `Series | ${folder.title || "Sin Categoria"}`,
    tvgId: null
  };
}

async function collectSmartoneSeriesItems({ activationCode, playlistUrl, seriesRows, categoryMap }) {
  const foldersPayload = buildXtreamSeriesFoldersPayload({
    activationCode,
    playlistUrl,
    rows: seriesRows,
    categoryMap
  });

  const folders = Array.isArray(foldersPayload.folders) ? foldersPayload.folders : [];
  const maxEpisodes = smartoneSeriesMaxEpisodes();
  const concurrency = smartoneSeriesConcurrency();
  const items = [];

  let index = 0;

  async function worker() {
    while (index < folders.length) {
      if (maxEpisodes > 0 && items.length >= maxEpisodes) return;

      const folder = folders[index];
      index += 1;

      try {
        const client = await getClientByActivationCode(activationCode);
    episodes = await getXtreamEpisodesForSeriesFolder(folder, client.playlist_url);

        for (const episode of episodes) {
          if (maxEpisodes > 0 && items.length >= maxEpisodes) return;

          if (episode?.streamUrl) {
            items.push(smartoneSeriesItemFromEpisode(folder, episode));
          }
        }
      } catch (error) {
        console.error("Smartone Xtream series folder error:", folder?.key, error.message);
      }
    }
  }

  await Promise.all(
    Array.from({ length: concurrency }, () => worker())
  );

  return {
    folderCount: folders.length,
    items,
    limited: maxEpisodes > 0 && items.length >= maxEpisodes,
    maxEpisodes
  };
}

async function buildSmartoneXtreamM3u() {
  if (!isXtreamContentMode()) {
    return null;
  }

  const includeSeries = smartoneIncludeSeriesEnabled();

  const [
    liveCategories,
    liveRows,
    movieCategories,
    movieRows,
    seriesCategories,
    seriesRows
  ] = await Promise.all([
    fetchXtreamJson(playlistUrl, "get_live_categories"),
    fetchXtreamJson(playlistUrl, "get_live_streams"),
    fetchXtreamJson(playlistUrl, "get_vod_categories"),
    fetchXtreamJson(playlistUrl, "get_vod_streams"),
    includeSeries ? fetchXtreamJson(playlistUrl, "get_series_categories") : Promise.resolve([]),
    includeSeries ? fetchXtreamJson(playlistUrl, "get_series") : Promise.resolve([])
  ]);

  const liveItems = normalizeXtreamLiveItems(playlistUrl, liveRows, xtreamCategoryMap(liveCategories));
  const movieItems = normalizeXtreamMovieItems(movieRows, xtreamCategoryMap(movieCategories));

  let seriesItems = [];
  let seriesFolderCount = 0;
  let seriesLimited = false;
  let seriesMaxEpisodes = 0;

  if (includeSeries) {
    const seriesResult = await collectSmartoneSeriesItems({
      activationCode: "SMARTONE",
      playlistUrl: xtreamSourceUrlMasked(),
      seriesRows,
      categoryMap: xtreamCategoryMap(seriesCategories)
    });

    seriesItems = seriesResult.items;
    seriesFolderCount = seriesResult.folderCount;
    seriesLimited = seriesResult.limited;
    seriesMaxEpisodes = seriesResult.maxEpisodes;
  }

  const lines = ["#EXTM3U"];

  for (const item of liveItems) {
    lines.push(smartoneM3uLineForItem(item, "live"));
  }

  for (const item of movieItems) {
    lines.push(smartoneM3uLineForItem(item, "movie"));
  }

  for (const item of seriesItems) {
    lines.push(smartoneM3uLineForItem(item, "series"));
  }

  return {
    sourceMode: includeSeries ? "xtream-live-movies-series" : "xtream-live-movies",
    content: lines.join("\n") + "\n",
    counts: {
      live: liveItems.length,
      movies: movieItems.length,
      series: seriesItems.length,
      seriesFolders: seriesFolderCount,
      seriesLimited,
      seriesMaxEpisodes,
      total: liveItems.length + movieItems.length + seriesItems.length
    },
    generatedAt: new Date().toISOString()
  };
}

// SMARTONE_XTREAM_END


module.exports = {
  buildSmartoneXtreamM3u,
  refreshContentCacheForClient,
  getCachedContentSection,
  getSeriesFoldersLite,
  getSeriesFolderByKey,
  getMovieCategoriesLite,
  getMovieCategoryByKey,
  searchContentItems,
  filterPayloadAdultContent
};
