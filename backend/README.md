# StoreTD Play Backend

Backend con activacion, panel admin, clientes, dispositivos, reportes y configuracion remota usando Supabase/PostgreSQL.

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
GET /app/config
POST /auth/activate
POST /reports/channel

## Panel admin

/admin

## Configuracion comercial remota

/admin/config

Permite editar:

- Nombre de app
- Mensaje de bienvenida
- Mensaje del proveedor
- Modo mantenimiento
- WhatsApp soporte
- Email soporte
- URL renovacion
- Terminos
- Privacidad
- Version minima
- Forzar actualizacion
- Permitir carga manual de playlist

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

Configuracion app:

GET /admin/api/app-config
PUT /admin/api/app-config

Header requerido:

x-admin-key: tu_clave
