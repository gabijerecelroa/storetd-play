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

  const [live, movies, series] = await Promise.all([
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
    })
  ]);

  return {
    success: true,
    activationCode: code,
    counts: {
      live: live.itemCount,
      movies: movies.itemCount,
      series: series.itemCount
    },
    updatedAt: new Date().toISOString()
  };
}

async function getCachedContentSection({ activationCode, section, autoRefresh = true }) {
  const code = normalizeCode(activationCode);
  const safeSection = String(section || "").toLowerCase();

  if (!["live", "movies", "series"].includes(safeSection)) {
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

module.exports = {
  parseM3u,
  refreshContentCacheForClient,
  getCachedContentSection
};
