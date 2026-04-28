# StoreTD Play Backend

Backend basico con activacion y panel admin.

## Ejecutar local

npm install
npm run dev

## Endpoints publicos

GET /
GET /health
POST /auth/activate

## Panel admin

Ruta:

/admin

Clave admin por defecto:

admin1234

En produccion configura variable de entorno:

ADMIN_KEY=tu_clave_segura

## API admin

GET /admin/api/clients
POST /admin/api/clients
PUT /admin/api/clients/:code
DELETE /admin/api/clients/:code
POST /admin/api/clients/:code/unlink-devices

Header requerido:

x-admin-key: tu_clave
