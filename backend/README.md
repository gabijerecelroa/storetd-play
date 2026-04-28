# StoreTD Play Backend

Backend basico para activacion de clientes StoreTD Play.

## Ejecutar local

npm install
npm run dev

## Endpoints

GET /health

POST /auth/activate

Body ejemplo:

{
  "customerName": "Jose",
  "activationCode": "TEST1234",
  "deviceCode": "ABC123",
  "appVersion": "1.0.0"
}

## Clientes de prueba

- TEST1234 = Activa
- DEMO1234 = Prueba
- SUSPENDIDO = Suspendida
- VENCIDO = Vencida

## Configurar listas por cliente

Edita backend/src/server.js y cambia playlistUrl y epgUrl por URLs autorizadas.

Importante: usa solamente listas y EPG autorizadas.
