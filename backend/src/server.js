const express = require("express");
const cors = require("cors");
const path = require("path");
const { supabase, isDatabaseConfigured } = require("./db");
const { getAppConfig, updateAppConfig } = require("./appConfig");

const app = express();
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


app.listen(port, () => {
  console.log("StoreTD Play backend running on port " + port);
});
