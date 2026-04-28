# StoreTD Play Backend

Backend con activacion, panel admin, clientes, dispositivos y reportes usando Supabase/PostgreSQL.

## Variables requeridas

ADMIN_KEY=tu_clave_admin
SUPABASE_URL=https://tu-proyecto.supabase.co
SUPABASE_SERVICE_ROLE_KEY=tu_service_role_key

## Ejecutar local

npm install
npm run dev

## Endpoints publicos

GET /
GET /health
POST /auth/activate
POST /reports/channel

## Panel admin

/admin

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
