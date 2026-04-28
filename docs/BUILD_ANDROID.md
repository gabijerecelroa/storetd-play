# Compilacion Android

## Requisitos

- Android Studio reciente.
- JDK 17.
- Android SDK con compileSdk configurado.
- Internet para descargar dependencias.

## Abrir en Android Studio

1. Abre Android Studio.
2. Selecciona `Open`.
3. Elige la carpeta `android`.
4. Espera la sincronizacion de Gradle.
5. Ejecuta `app`.

## Compilar debug por terminal

```bash
cd android
./gradlew assembleDebug
```

APK:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Compilar release

```bash
cd android
./gradlew assembleRelease
```

Antes debes configurar firma. Ver `SIGNING.md`.
