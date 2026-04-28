const express = require("express");
const cors = require("cors");

const app = express();
const port = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

const validCodes = new Set([
  "TEST1234",
  "DEMO1234",
  "CLIENTE2026",
  "STORETD2026"
]);

function expirationDate(days = 30) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

app.get("/", (req, res) => {
  res.json({
    name: "StoreTD Play Backend",
    status: "ok"
  });
});

app.post("/auth/activate", (req, res) => {
  const { customerName, activationCode, deviceCode, appVersion } = req.body || {};

  if (!customerName || !activationCode || !deviceCode) {
    return res.status(400).json({
      success: false,
      message: "Faltan datos para activar el dispositivo."
    });
  }

  const normalizedCode = String(activationCode).trim().toUpperCase();

  if (!validCodes.has(normalizedCode)) {
    return res.status(401).json({
      success: false,
      message: "Codigo de activacion invalido o vencido."
    });
  }

  return res.json({
    success: true,
    message: "Dispositivo activado correctamente.",
    customerName: String(customerName).trim(),
    activationCode: normalizedCode,
    status: "Activa",
    expiresAt: expirationDate(30),
    deviceCode,
    appVersion
  });
});

app.listen(port, () => {
  console.log("StoreTD Play backend running on port " + port);
});
