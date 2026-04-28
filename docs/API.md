# API base

## Auth

```text
POST /auth/login
POST /auth/activate
POST /auth/refresh
POST /auth/logout
```

## App

```text
GET /app/config
GET /app/messages
GET /app/version
```

## Listas

```text
GET /playlists
GET /playlists/:id
POST /playlists/:id/refresh
```

## Reportes

```text
POST /reports/channel
```

## Admin

```text
GET /admin/customers
POST /admin/customers
PUT /admin/customers/:id
GET /admin/playlists
POST /admin/playlists
GET /admin/reports
PUT /admin/reports/:id/status
GET /admin/app-config
PUT /admin/app-config
```
