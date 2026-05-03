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
  filterPayloadAdultContent
} = require("./playlistContent");

const app = express();

app.use(express.json({ limit: "50mb" }));
app.use(express.urlencoded({ extended: true, limit: "50mb" }));
const port = process.env.PORT || 3000;
const adminKey = process.env.ADMIN_KEY || "admin1234";

app.use(cors());
app.use(express.json({ limit: "1mb" }));
app.use(express.static(path.join(__dirname, "..", "public")));

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
    activation_code: normalizeCode(fixedCode || input.activationCode),
    status: String(input.status || "Activa").trim(),
    expires_at: String(input.expiresAt || "").trim() || null,
    max_devices: Number(input.maxDevices || 1),
    playlist_url: String(input.playlistUrl || "").trim(),
    epg_url: String(input.epgUrl || "").trim(),
    updated_at: nowIso()
  };
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
      .select("*")
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
        deviceDetails: devices.map(dbDeviceToApi)
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
  const response = await fetch(`https://api.github.com/gists/${gistId}`, {
    method: "PATCH",
    headers: {
      "Accept": "application/vnd.github+json",
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json",
      "User-Agent": "StoreTD-Play-Admin"
    },
    body: JSON.stringify({
      files: {
        [filename]: {
          content
        }
      }
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

    if (runAsync) {
      refreshContentCacheForClient(activationCode, { section })
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

    const result = await refreshContentCacheForClient(activationCode, { section });
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

    const result = await refreshContentCacheForClient(activationCode, { section });
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
