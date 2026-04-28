const express = require("express");
const cors = require("cors");
const path = require("path");
const { supabase, isDatabaseConfigured } = require("./db");

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
    .select("device_code")
    .eq("activation_code", activationCode)
    .order("last_seen_at", { ascending: false });

  if (error) throw error;
  return data || [];
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
    const alreadyRegistered = devices.some((item) => item.device_code === deviceCode);
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
        devices: devices.map((item) => item.device_code)
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

app.listen(port, () => {
  console.log("StoreTD Play backend running on port " + port);
});
