# StoreTD Play Backend

Backend basico para probar activacion de dispositivos.

## Instalar

npm install

## Ejecutar

npm run dev

## Endpoint

POST /auth/activate

Body:

{
  "customerName": "Jose",
  "activationCode": "TEST1234",
  "deviceCode": "ABC123",
  "appVersion": "1.0.0"
}

Codigos validos de prueba:

- TEST1234
- DEMO1234
- CLIENTE2026
- STORETD2026
