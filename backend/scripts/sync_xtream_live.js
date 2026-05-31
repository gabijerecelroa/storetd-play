#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

function loadEnvFile() {
  const envPath = path.join(__dirname, "..", ".env");

  if (!fs.existsSync(envPath)) return;

  const lines = fs.readFileSync(envPath, "utf8").split(/\r?\n/);

  for (const line of lines) {
    const clean = line.trim();

    if (!clean || clean.startsWith("#") || !clean.includes("=")) continue;

    const index = clean.indexOf("=");
    const key = clean.slice(0, index).trim();
    const value = clean.slice(index + 1).trim();

    if (key && process.env[key] === undefined) {
      process.env[key] = value;
    }
  }
}

loadEnvFile();

const { supabase } = require("../src/db");

const CACHE_TABLE = "playlist_cache";

function normalizeCode(value) {
  return String(value || "").trim().toUpperCase();
}

function maskUrl(url) {
  const value = String(url || "");
  if (!value) return "";
  return value
    .replace(/(username=)[^&]+/i, "$1***")
    .replace(/(password=)[^&]+/i, "$1***");
}

function getXtreamConfig() {
  const rawUrl = String(process.env.XTREAM_LIVE_API_URL || "").trim();

  if (!rawUrl) {
    throw new Error("Falta XTREAM_LIVE_API_URL en .env");
  }

  const parsed = new URL(rawUrl);
  const username = parsed.searchParams.get("username") || "";
  const password = parsed.searchParams.get("password") || "";

  if (!username || !password) {
    throw new Error("XTREAM_LIVE_API_URL no tiene username/password");
  }

  return {
    rawUrl,
    baseUrl: `${parsed.protocol}//${parsed.host}`,
    username,
    password
  };
}

async function fetchXtream(action) {
  const config = getXtreamConfig();
  const url = new URL(config.rawUrl);
  url.searchParams.set("action", action);

  console.log("Descargando:", action);

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

function buildStreamUrl(config, streamId) {
  return `${config.baseUrl}/live/${encodeURIComponent(config.username)}/${encodeURIComponent(config.password)}/${encodeURIComponent(String(streamId))}.m3u8`;
}

async function main() {
  const activationCode = normalizeCode(process.argv[2]);

  if (!activationCode) {
    console.error("Uso: node scripts/sync_xtream_live.js CODIGO");
    process.exit(1);
  }

  const config = getXtreamConfig();

  console.log("============================================");
  console.log("SYNC XTREAM LIVE");
  console.log("Activation code:", activationCode);
  console.log("Base:", config.baseUrl);
  console.log("Usuario:", config.username);
  console.log("============================================");

  const [categoriesRaw, streamsRaw] = await Promise.all([
    fetchXtream("get_live_categories").catch(() => []),
    fetchXtream("get_live_streams")
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

  const items = streams
    .filter((stream) => stream && stream.stream_id)
    .map((stream) => {
      const categoryId = String(stream.category_id || "").trim();
      const group = categoryMap.get(categoryId) || stream.category_name || "TV en vivo";

      return {
        name: String(stream.name || `Canal ${stream.stream_id}`).trim(),
        group: String(group || "TV en vivo").trim(),
        tvgId: stream.epg_channel_id || stream.tvg_id || null,
        logoUrl: stream.stream_icon || stream.logo || "",
        streamUrl: buildStreamUrl(config, stream.stream_id)
      };
    });

  const groups = [
    "Todos",
    ...Array.from(new Set(items.map((item) => item.group || "TV en vivo"))).sort()
  ];

  const payload = {
    success: true,
    activationCode,
    playlistUrlMasked: maskUrl(config.rawUrl),
    section: "live",
    itemCount: items.length,
    groups,
    items
  };

  const now = new Date().toISOString();

  const { error } = await supabase
    .from(CACHE_TABLE)
    .upsert(
      {
        activation_code: activationCode,
        playlist_url: config.rawUrl,
        section: "live",
        payload,
        updated_at: now
      },
      {
        onConflict: "activation_code,section"
      }
    );

  if (error) {
    throw error;
  }

  console.log("Cache actualizado: live | items:", items.length);
  console.log("============================================");
  console.log("FINALIZADO");
  console.log("Canales:", items.length);
  console.log("============================================");
}

main().catch((error) => {
  console.error("ERROR:", error);
  process.exit(1);
});
