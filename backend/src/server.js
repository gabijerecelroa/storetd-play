const express = require("express");
const cors = require("cors");
const fs = require("fs");
const path = require("path");

const app = express();
const port = process.env.PORT || 3000;
const adminKey = process.env.ADMIN_KEY || "admin1234";

const dataDir = path.join(__dirname, "..", "data");
const clientsFile = path.join(dataDir, "clients.json");

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, "..", "public")));

const activatedDevicesByCode = new Map();

function ensureDataFile() {
  if (!fs.existsSync(dataDir)) {
    fs.mkdirSync(dataDir, { recursive: true });
  }

  if (!fs.existsSync(clientsFile)) {
    fs.writeFileSync(clientsFile, "[]", "utf8");
  }
}

function loadClients() {
  ensureDataFile();

  try {
    const raw = fs.readFileSync(clientsFile, "utf8");
    return JSON.parse(raw);
  } catch (error) {
    console.error("Could not read clients.json:", error);
    return [];
  }
}

function saveClients(clients) {
  ensureDataFile();
  fs.writeFileSync(clientsFile, JSON.stringify(clients, null, 2), "utf8");
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

app.get("/", (req, res) => {
  res.json({
    name: "StoreTD Play Backend",
    status: "ok",
    version: "1.2.0"
  });
});

app.get("/health", (req, res) => {
  const clients = loadClients();

  res.json({
    status: "ok",
    clients: clients.length,
    version: "1.2.0"
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

app.listen(port, () => {
  console.log("StoreTD Play backend running on port " + port);
});
