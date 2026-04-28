# StoreTD Play Backend

Backend basico con activacion, panel admin, clientes y reportes de canales.

## Ejecutar local

npm install
npm run dev

## Endpoints publicos

GET /
GET /health
POST /auth/activate
POST /reports/channel

## Panel admin

Ruta:

/admin

Clave admin por defecto:

admin1234

En produccion configura variable de entorno:

ADMIN_KEY=tu_clave_segura

## API admin

Clientes:

GET /admin/api/clients
POST /admin/api/clients
PUT /admin/api/clients/:code
DELETE /admin/api/clients/:code
POST /admin/api/clients/:code/unlink-devices

Reportes:

GET /admin/api/reports
PUT /admin/api/reports/:id
DELETE /admin/api/reports/:id

Estadisticas:

GET /admin/api/stats

Header requerido:

x-admin-key: tu_clave

## Nota importante

Este backend usa archivos JSON para MVP. Para produccion grande se recomienda migrar a base de datos real.
