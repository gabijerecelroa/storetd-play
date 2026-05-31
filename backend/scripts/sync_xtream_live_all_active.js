#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

function loadEnvFile() {
  const envPath = path.join(__dirname, "..", ".env");
  if (!fs.existsSync(envPath)) return;

  for (const line of fs.readFileSync(envPath, "utf8").split(/\r?\n/)) {
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

function isActiveClient(row) {
  const code = normalizeCode(row.activation_code);
  if (!code) return false;

  const status = String(row.status || "").trim().toLowerCase();
  if (status && !["activa", "activo", "active"].includes(status)) return false;

  if (row.expires_at) {
    const today = new Date().toISOString().slice(0, 10);
    const expires = String(row.expires_at).slice(0, 10);
    if (expires < today) return false;
  }

  return true;
}

async function main() {
  const backendRoot = path.join(__dirname, "..");
  const sourceCode = normalizeCode(process.argv[2] || process.env.DEFAULT_LIVE_SOURCE_CODE || "253698");

  console.log("============================================");
  console.log("SYNC LIVE XTREAM PARA TODOS LOS USUARIOS");
  console.log("Código base:", sourceCode);
  console.log("============================================");

  console.log("Actualizando cache base desde Xtream...");
  const syncResult = spawnSync(
    process.execPath,
    [path.join(backendRoot, "scripts", "sync_xtream_live.js"), sourceCode],
    {
      cwd: backendRoot,
      env: process.env,
      stdio: "inherit"
    }
  );

  if (syncResult.status !== 0) {
    throw new Error("Falló la sincronización del código base " + sourceCode);
  }

  const { data: sourceCache, error: sourceError } = await supabase
    .from(CACHE_TABLE)
    .select("payload, playlist_url")
    .eq("activation_code", sourceCode)
    .eq("section", "live")
    .maybeSingle();

  if (sourceError) throw sourceError;
  if (!sourceCache || !sourceCache.payload) {
    throw new Error("No encontré cache live del código base " + sourceCode);
  }

  const basePayload = sourceCache.payload;
  const itemCount = Array.isArray(basePayload.items) ? basePayload.items.length : Number(basePayload.itemCount || 0);

  console.log("Canales base:", itemCount);

  const { data: clients, error: clientsError } = await supabase
    .from("clients")
    .select("activation_code,status,expires_at");

  if (clientsError) throw clientsError;

  const activeClients = (clients || []).filter(isActiveClient);

  console.log("Usuarios activos:", activeClients.length);

  const now = new Date().toISOString();
  let ok = 0;
  let failed = 0;

  for (const client of activeClients) {
    const code = normalizeCode(client.activation_code);

    const payload = {
      ...basePayload,
      activationCode: code,
      section: "live",
      itemCount
    };

    const { error } = await supabase
      .from(CACHE_TABLE)
      .upsert(
        {
          activation_code: code,
          playlist_url: sourceCache.playlist_url || process.env.XTREAM_LIVE_API_URL || "",
          section: "live",
          payload,
          updated_at: now
        },
        {
          onConflict: "activation_code,section"
        }
      );

    if (error) {
      failed++;
      console.error("ERROR:", code, error.message);
    } else {
      ok++;
      console.log("OK:", code, "| canales:", itemCount);
    }
  }

  console.log("============================================");
  console.log("FINALIZADO");
  console.log("Usuarios actualizados:", ok);
  console.log("Errores:", failed);
  console.log("Canales por usuario:", itemCount);
  console.log("============================================");
}

main().catch((error) => {
  console.error("ERROR GENERAL:", error);
  process.exit(1);
});
