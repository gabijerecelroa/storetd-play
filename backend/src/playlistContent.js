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

  const raw = await fetchPlaylist(client.playlist_url);
  const parsed = parseM3u(raw);
  const sections = splitSections(parsed);
  const tasks = [];
  const counts = {};

  if (shouldRefresh("live")) {
    tasks.push(
      saveSectionCache({
        activationCode: code,
        playlistUrl: client.playlist_url,
        section: "live",
        items: sections.live
      }).then((payload) => {
        counts.live = payload.itemCount;
      })
    );
  }

  if (shouldRefresh("movies")) {
    const movieCategoriesPayload = buildMovieCategoriesPayload({
      activationCode: code,
      playlistUrl: client.playlist_url,
      items: sections.movies
    });

    tasks.push(
      saveSectionCache({
        activationCode: code,
        playlistUrl: client.playlist_url,
        section: "movies",
        items: sections.movies
      }).then((payload) => {
        counts.movies = payload.itemCount;
      })
    );

    tasks.push(
      saveRawPayloadCache({
        activationCode: code,
        playlistUrl: client.playlist_url,
        section: "movie-categories",
        payload: movieCategoriesPayload
      }).then((payload) => {
        counts.movieCategories = payload.categoryCount;
      })
    );
  }

  if (shouldRefresh("series")) {
    const seriesFoldersPayload = buildSeriesFoldersPayload({
      activationCode: code,
      playlistUrl: client.playlist_url,
      items: sections.series
    });

    // Series usa carpetas lazy en la APK. Evitamos guardar también la lista plana
    // "series" porque con muchos episodios puede disparar timeout en Supabase.
    counts.series = Number(seriesFoldersPayload.itemCount || sections.series.length || 0);

    tasks.push(
      saveRawPayloadCache({
        activationCode: code,
        playlistUrl: client.playlist_url,
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
    activationCode: code,
    section: refreshSection,
    counts,
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




function filterAdultItems(items, includeAdult) {
  if (includeAdult) return items || [];
  return (items || []).filter((item) => !isAdult(item));
}

function isAdultLiteEntry(entry) {
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


async function getSeriesFoldersLite({ activationCode, autoRefresh = true }) {
  const result = await getCachedContentSection({
    activationCode,
    section: "series-folders",
    autoRefresh
  });

  if (!result.success) return result;

  const payload = result.payload || {};
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

  for (const folder of sourceFolders) {
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

  return {
    success: true,
    status: 200,
    fromCache: result.fromCache,
    payload: {
      section: "series-folders-lite",
      groupingVersion: "series-lite-endpoint-v14",
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
  const sourceFolders = Array.isArray(payload.folders) ? payload.folders : [];
  const folders = mergeGeneratedSeriesFolders(sourceFolders);
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
  getMovieCategoryByKey,
  filterPayloadAdultContent
};
