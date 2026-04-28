# Backend setup

## Requisitos

- Node.js 20+
- PostgreSQL
- npm

## Instalacion

```bash
cd backend
cp .env.example .env
npm install
npx prisma generate
npx prisma migrate dev
npm run start:dev
```

API local:

```text
http://localhost:4000
```
