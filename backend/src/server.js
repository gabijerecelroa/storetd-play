const express = require("express");
const cors = require("cors");
const fs = require("fs");
const path = require("path");

const app = express();
const port = process.env.PORT || 3000;
const adminKey = process.env.ADMIN_KEY || "admin1234";

const dataDir = path.join(__dirname, "..", "data");
const clientsFile = path.join(dataDir, "clients.json");
const reportsFile = path.join(dataDir, "reports.json");

app.use(cors());
app.use(express.json({ limit: "1mb" }));
app.use(express.static(path.join(__dirname, "..", "public")));

const activatedDevicesByCode = new Map();

function ensureDataFiles() {
  if (!fs.existsSync(dataDir)) {
    fs.mkdirSync(dataDir, { recursive: true });
  }

  if (!fs.existsSync(clientsFile)) {
    fs.writeFileSync(clientsFile, "[]", "utf8");
  }

  if (!fs.existsSync(reportsFile)) {
    fs.writeFileSync(reportsFile, "[]", "utf8");
  }
}

function readJson(file, fallback) {
  ensureDataFiles();

  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (error) {
    console.error("Could not read JSON:", file, error);
    return fallback;
  }
}

function writeJson(file, value) {
  ensureDataFiles();
  fs.writeFileSync(file, JSON.stringify(value, null, 2), "utf8");
}

function loadClients() {
  return readJson(clientsFile, []);
}

function saveClients(clients) {
  writeJson(clientsFile, clients);
}

function loadReports() {
  return readJson(reportsFile, []);
}

function saveReports(reports) {
  writeJson(reportsFile, reports);
}

function normalizeCode(code) {
  return String(code || "").trim().toUpperCase();
}

function isExpired(expiresAt) {
  if (!expiresAt) return false;
  const today = new Date().toISOString().slice(0, 10);
  return expiresAt < today;
}

function getDeviceCount(code) {
  return activatedDevicesByCode.get(code)?.size || 0;
}

function getDevices(code) {
  return Array.from(activatedDevicesByCode.get(code) || []);
}

function registerDevice(code, deviceCode) {
  if (!activatedDevicesByCode.has(code)) {
    activatedDevicesByCode.set(code, new Set());
  }

  activatedDevicesByCode.get(code).add(deviceCode);
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

function sanitizeClient(input) {
  return {
    customerName: String(input.customerName || "").trim(),
    activationCode: normalizeCode(input.activationCode),
    status: String(input.status || "Activa").trim(),
    expiresAt: String(input.expiresAt || "").trim(),
    maxDevices: Number(input.maxDevices || 1),
    playlistUrl: String(input.playlistUrl || "").trim(),
    epgUrl: String(input.epgUrl || "").trim()
  };
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

function nowIso() {
  return new Date().toISOString();
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

app.get("/", (req, res) => {
  res.json({
    name: "StoreTD Play Backend",
    status: "ok",
    version: "1.3.0"
  });
});

app.get("/health", (req, res) => {
  const clients = loadClients();
  const reports = loadReports();

  res.json({
    status: "ok",
    clients: clients.length,
    reports: reports.length,
    version: "1.3.0"
  });
});

app.get("/admin", (req, res) => {
  res.sendFile(path.join(__dirname, "..", "public", "admin.html"));
});

app.post("/auth/activate", (req, res) => {
  const clients = loadClients();
  const { customerName, activationCode, deviceCode, appVersion } = req.body || {};
  const normalizedCode = normalizeCode(activationCode);

  if (!customerName || !normalizedCode || !deviceCode) {
    return res.status(400).json({
      success: false,
      message: "Faltan datos para activar el dispositivo."
    });
  }

  const client = clients.find((item) => normalizeCode(item.activationCode) === normalizedCode);

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

  if (client.status === "Vencida" || isExpired(client.expiresAt)) {
    return res.status(403).json({
      success: false,
      message: "La cuenta esta vencida. Renueva el servicio para continuar."
    });
  }

  const currentDevices = activatedDevicesByCode.get(normalizedCode) || new Set();
  const alreadyRegistered = currentDevices.has(deviceCode);

  if (!alreadyRegistered && currentDevices.size >= Number(client.maxDevices || 1)) {
    return res.status(403).json({
      success: false,
      message: "Limite de dispositivos alcanzado para esta cuenta."
    });
  }

  registerDevice(normalizedCode, deviceCode);

  return res.json({
    success: true,
    message: "Dispositivo activado correctamente.",
    customerName: client.customerName || customerName,
    activationCode: normalizedCode,
    status: client.status,
    expiresAt: client.expiresAt,
    playlistUrl: client.playlistUrl || "",
    epgUrl: client.epgUrl || "",
    maxDevices: Number(client.maxDevices || 1),
    deviceCount: getDeviceCount(normalizedCode),
    deviceCode,
    appVersion
  });
});

app.post("/reports/channel", (req, res) => {
  const body = req.body || {};
  const reports = loadReports();

  const report = {
    id: "rep_" + Date.now() + "_" + Math.random().toString(16).slice(2),
    createdAt: nowIso(),
    status: "Pendiente",
    channelName: String(body.channelName || "Sin nombre").trim(),
    category: String(body.category || "").trim(),
    problemType: String(body.problemType || "Otro problema").trim(),
    streamUrlMasked: maskUrl(body.streamUrl || ""),
    customerName: String(body.customerName || "").trim(),
    activationCode: normalizeCode(body.activationCode || ""),
    deviceCode: String(body.deviceCode || "").trim(),
    appVersion: String(body.appVersion || "").trim(),
    androidVersion: String(body.androidVersion || "").trim(),
    deviceModel: String(body.deviceModel || "").trim(),
    playerError: String(body.playerError || "").trim(),
    internalComment: ""
  };

  if (!report.channelName) {
    return res.status(400).json({
      success: false,
      message: "Falta el nombre del canal."
    });
  }

  reports.unshift(report);
  saveReports(reports);

  res.json({
    success: true,
    message: "Reporte enviado correctamente.",
    reportId: report.id
  });
});

app.get("/admin/api/stats", requireAdmin, (req, res) => {
  const clients = loadClients();
  const reports = loadReports();

  res.json({
    success: true,
    stats: {
      clientsTotal: clients.length,
      clientsActive: clients.filter((c) => c.status === "Activa").length,
      clientsTrial: clients.filter((c) => c.status === "Prueba").length,
      clientsSuspended: clients.filter((c) => c.status === "Suspendida").length,
      clientsExpired: clients.filter((c) => c.status === "Vencida" || isExpired(c.expiresAt)).length,
      reportsTotal: reports.length,
      reportsPending: reports.filter((r) => r.status === "Pendiente").length,
      topReportedChannels: groupReportsByChannel(reports).slice(0, 10)
    }
  });
});

app.get("/admin/api/clients", requireAdmin, (req, res) => {
  const clients = loadClients().map((client) => {
    const code = normalizeCode(client.activationCode);

    return {
      ...client,
      activationCode: code,
      deviceCount: getDeviceCount(code),
      devices: getDevices(code)
    };
  });

  res.json({
    success: true,
    clients
  });
});

app.post("/admin/api/clients", requireAdmin, (req, res) => {
  const clients = loadClients();
  const client = sanitizeClient(req.body || {});

  if (!client.customerName) {
    return res.status(400).json({
      success: false,
      message: "Falta el nombre del cliente."
    });
  }

  if (!client.activationCode) {
    return res.status(400).json({
      success: false,
      message: "Falta el codigo de activacion."
    });
  }

  const exists = clients.some(
    (item) => normalizeCode(item.activationCode) === client.activationCode
  );

  if (exists) {
    return res.status(409).json({
      success: false,
      message: "Ya existe un cliente con ese codigo."
    });
  }

  clients.push(client);
  saveClients(clients);

  res.json({
    success: true,
    message: "Cliente creado.",
    client
  });
});

app.put("/admin/api/clients/:code", requireAdmin, (req, res) => {
  const clients = loadClients();
  const code = normalizeCode(req.params.code);
  const index = clients.findIndex(
    (item) => normalizeCode(item.activationCode) === code
  );

  if (index === -1) {
    return res.status(404).json({
      success: false,
      message: "Cliente no encontrado."
    });
  }

  const updated = sanitizeClient({
    ...clients[index],
    ...req.body,
    activationCode: code
  });

  clients[index] = updated;
  saveClients(clients);

  res.json({
    success: true,
    message: "Cliente actualizado.",
    client: updated
  });
});

app.delete("/admin/api/clients/:code", requireAdmin, (req, res) => {
  const clients = loadClients();
  const code = normalizeCode(req.params.code);
  const nextClients = clients.filter(
    (item) => normalizeCode(item.activationCode) !== code
  );

  if (nextClients.length === clients.length) {
    return res.status(404).json({
      success: false,
      message: "Cliente no encontrado."
    });
  }

  saveClients(nextClients);
  activatedDevicesByCode.delete(code);

  res.json({
    success: true,
    message: "Cliente eliminado."
  });
});

app.post("/admin/api/clients/:code/unlink-devices", requireAdmin, (req, res) => {
  const code = normalizeCode(req.params.code);
  activatedDevicesByCode.delete(code);

  res.json({
    success: true,
    message: "Dispositivos desvinculados."
  });
});

app.get("/admin/api/reports", requireAdmin, (req, res) => {
  const reports = loadReports();

  res.json({
    success: true,
    reports,
    grouped: groupReportsByChannel(reports)
  });
});

app.put("/admin/api/reports/:id", requireAdmin, (req, res) => {
  const reports = loadReports();
  const id = String(req.params.id || "");
  const index = reports.findIndex((report) => report.id === id);

  if (index === -1) {
    return res.status(404).json({
      success: false,
      message: "Reporte no encontrado."
    });
  }

  reports[index] = {
    ...reports[index],
    status: String(req.body.status || reports[index].status),
    internalComment: String(req.body.internalComment || "")
  };

  saveReports(reports);

  res.json({
    success: true,
    message: "Reporte actualizado.",
    report: reports[index]
  });
});

app.delete("/admin/api/reports/:id", requireAdmin, (req, res) => {
  const reports = loadReports();
  const id = String(req.params.id || "");
  const nextReports = reports.filter((report) => report.id !== id);

  if (nextReports.length === reports.length) {
    return res.status(404).json({
      success: false,
      message: "Reporte no encontrado."
    });
  }

  saveReports(nextReports);

  res.json({
    success: true,
    message: "Reporte eliminado."
  });
});

app.listen(port, () => {
  console.log("StoreTD Play backend running on port " + port);
});
