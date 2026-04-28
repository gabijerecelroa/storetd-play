# StoreTD Play

Aplicación Android profesional para reproducir listas M3U/M3U8 autorizadas, con experiencia optimizada para Android TV, TV Box, tablets y celulares.

> **Aviso legal:** este repositorio no incluye listas IPTV, canales, películas, series ni contenido de terceros. La app está diseñada únicamente para reproducir contenido autorizado por el usuario, por la empresa o por proveedores con derechos válidos.

## Estado del proyecto

Este repositorio contiene una base MVP lista para subir a GitHub:

- App Android en Kotlin + Jetpack Compose.
- Reproductor interno con AndroidX Media3 / ExoPlayer.
- Parser M3U/M3U8 básico.
- Carga de lista por URL.
- Pantallas base: inicio, TV en vivo, reproductor, favoritos, historial, cuenta, soporte y configuración.
- Backend base en NestJS.
- Panel administrativo base en Next.js.
- GitHub Actions para APK debug y APK release firmado.
- Documentación de compilación, firma, branding, backend, EPG y publicación.

## Estructura

```text
storetd-play/
  android/              App Android
  backend/              API REST y panel de datos
  admin-panel/          Panel web administrativo
  docs/                 Documentación operativa
  .github/workflows/    Automatización CI/CD
```

## Tecnologías

### Android

- Kotlin
- Jetpack Compose
- Compose Material 3
- AndroidX Media3 / ExoPlayer
- Navigation Compose
- OkHttp
- Room/DataStore preparado para evolución
- Gradle Kotlin DSL

### Backend

- Node.js
- NestJS
- Prisma
- PostgreSQL
- JWT

### Panel administrativo

- Next.js
- React
- TypeScript

## Requisitos

### Para Android local

- Android Studio reciente.
- JDK 17.
- Android SDK instalado.
- Conexión a internet para descargar dependencias Gradle.

### Para backend/panel

- Node.js 20 o superior.
- PostgreSQL.
- npm o pnpm.

## Compilar APK debug localmente

```bash
cd android
./gradlew assembleDebug
```

APK generado:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Compilar APK release localmente

Primero genera un keystore y configura las variables de entorno. Ver:

```text
docs/SIGNING.md
```

Luego:

```bash
cd android
./gradlew assembleRelease
```

APK generado:

```text
android/app/build/outputs/apk/release/app-release.apk
```

## GitHub Actions

El workflow está en:

```text
.github/workflows/android-build.yml
```

Se ejecuta en:

- Push a `main` o `develop`.
- Pull request a `main` o `develop`.
- Tags tipo `v1.0.0`.
- Release creada.
- Ejecución manual desde GitHub Actions.

## GitHub Secrets

Configura en GitHub:

```text
KEYSTORE_BASE64
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
API_BASE_URL
APP_NAME
BRAND_PRIMARY_COLOR
BRAND_SECONDARY_COLOR
SUPPORT_WHATSAPP
SUPPORT_EMAIL
```

Guía completa:

```text
docs/GITHUB_ACTIONS.md
docs/SIGNING.md
```

## Personalización de marca

Puedes modificar:

- Nombre de la app.
- Logo.
- Ícono.
- Splash.
- Color principal.
- Color secundario.
- Texto de bienvenida.
- WhatsApp de soporte.
- Email de soporte.
- URL base del backend.

Ver:

```text
docs/BRANDING.md
```

## Backend

Configura el backend:

```bash
cd backend
cp .env.example .env
npm install
npx prisma migrate dev
npm run start:dev
```

Ver:

```text
docs/BACKEND_SETUP.md
docs/API.md
docs/DATABASE.md
```

## Panel administrativo

```bash
cd admin-panel
cp .env.example .env.local
npm install
npm run dev
```

Abre:

```text
http://localhost:3000
```

## Listas M3U/M3U8

La app permite cargar una lista por URL desde la pantalla "TV en vivo". En versión Pro, las listas se asignan desde el backend por cliente.

El parser soporta atributos comunes:

```text
#EXTINF:-1 tvg-id="canal1" tvg-name="Canal 1" tvg-logo="https://..." group-title="Noticias",Canal 1
https://servidor-autorizado.example/hls/canal1.m3u8
```

## EPG/XMLTV

La estructura está documentada en:

```text
docs/EPG.md
```

En el MVP se deja preparada para implementación Pro.

## Roadmap

Ver:

```text
docs/ROADMAP.md
```

## Aviso legal importante

StoreTD Play es un reproductor y plataforma de gestión. No debe usarse para distribuir, promover ni facilitar acceso a contenido sin autorización. No subas listas, claves, URLs privadas ni contenido protegido al repositorio.
