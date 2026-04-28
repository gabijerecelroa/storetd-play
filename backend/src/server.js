const express = require("express");
const cors = require("cors");

const app = express();
const port = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

/**
 * Base simple de clientes.
 *
 * IMPORTANTE:
 * - playlistUrl debe ser una lista autorizada.
 * - No pongas listas ilegales ni contenido no autorizado.
 * - En una version futura esto se movera a base de datos y panel admin.
 */
const clients = [
  {
    customerName: "Jose",
    activationCode: "TEST1234",
    status: "Activa",
    expiresAt: "2026-12-31",
    maxDevices: 2,
    playlistUrl: "https://example.com/lista-autorizada.m3u",
    epgUrl: "https://example.com/epg.xml"
  },
  {
    customerName: "Cliente Demo",
    activationCode: "DEMO1234",
    status: "Prueba",
    expiresAt: "2026-12-31",
    maxDevices: 5,
    playlistUrl: "https://example.com/lista-demo.m3u",
    epgUrl: ""
  },
  {
    customerName: "Cliente Suspendido",
    activationCode: "SUSPENDIDO",
    status: "Suspendida",
    expiresAt: "2026-12-31",
    maxDevices: 1,
    playlistUrl: "",
    epgUrl: ""
  },
  {
    customerName: "Cliente Vencido",
    activationCode: "VENCIDO",
    status: "Vencida",
    expiresAt: "2025-01-01",
    maxDevices: 1,
    playlistUrl: "",
    epgUrl: ""
  }
];

/**
 * Registro basico en memoria.
 * En Render puede reiniciarse cuando el servicio duerme o se redeploya.
 * Para produccion real, esto debe ir a PostgreSQL/Supabase/Firebase/etc.
 */
const activatedDevicesByCode = new Map();

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

function registerDevice(code, deviceCode) {
  if (!activatedDevicesByCode.has(code)) {
    activatedDevicesByCode.set(code, new Set());
  }

  activatedDevicesByCode.get(code).add(deviceCode);
}

app.get("/", (req, res) => {
  res.json({
    name: "StoreTD Play Backend",
    status: "ok",
    version: "1.1.0"
  });
});

app.get("/health", (req, res) => {
  res.json({
    status: "ok",
    clients: clients.length
  });
});

app.post("/auth/activate", (req, res) => {
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

  if (!alreadyRegistered && currentDevices.size >= client.maxDevices) {
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
    maxDevices: client.maxDevices,
    deviceCount: getDeviceCount(normalizedCode),
    deviceCode,
    appVersion
  });
});

app.listen(port, () => {
  console.log("StoreTD Play backend running on port " + port);
});
