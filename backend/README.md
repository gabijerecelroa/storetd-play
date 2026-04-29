# StoreTD Play Backend

Backend con activacion, panel admin, clientes, dispositivos, reportes, configuracion remota, EPG Proxy y Playlist Proxy usando Supabase/PostgreSQL.

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
GET /epg/proxy
GET /playlist/proxy
POST /auth/activate
POST /reports/channel

## Panel admin

/admin

## Dispositivos

/admin/devices

Permite:

- Ver dispositivos activados
- Ver último uso
- Agregar alias interno
- Bloquear dispositivo
- Desbloquear dispositivo
- Desvincular dispositivo individual

## Configuracion comercial remota

/admin/config

Incluye EPG Proxy:

- URL EPG fuente
- Palabras clave de canales a conservar
- Boton para probar y refrescar EPG Proxy

## API admin

Clientes:

GET /admin/api/clients
POST /admin/api/clients
PUT /admin/api/clients/:code
DELETE /admin/api/clients/:code
POST /admin/api/clients/:code/unlink-devices

Dispositivos:

GET /admin/api/devices
PUT /admin/api/devices/:activationCode/:deviceCode
DELETE /admin/api/devices/:activationCode/:deviceCode

Reportes:

GET /admin/api/reports
PUT /admin/api/reports/:id
DELETE /admin/api/reports/:id

Estadisticas:

GET /admin/api/stats

Configuracion app:

GET /admin/api/app-config
PUT /admin/api/app-config

EPG Proxy:

GET /admin/api/epg-proxy/refresh

Header requerido:

x-admin-key: tu_clave
