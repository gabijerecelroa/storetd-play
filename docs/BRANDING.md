# Guia de personalizacion de marca

## Cambiar nombre de la app

### Localmente

Edita:

```text
android/app/build.gradle.kts
```

Busca:

```kotlin
resValue("string", "app_name", System.getenv("APP_NAME") ?: "StoreTD Play")
```

Cambia `StoreTD Play` por tu nombre comercial.

### Con GitHub Actions

Agrega el secret:

```text
APP_NAME
```

## Cambiar colores

Los colores base estan en:

```text
android/app/src/main/java/com/storetd/play/ui/theme/Theme.kt
```

Cambia:

```kotlin
private val Primary = Color(0xFFE50914)
private val Secondary = Color(0xFF202124)
```

## Cambiar logo, icono y banner

Archivos actuales:

```text
android/app/src/main/res/drawable/ic_banner.xml
android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
```

Recomendado:

1. Genera icono con Android Studio > Image Asset.
2. Reemplaza los recursos generados.
3. Prueba en Android TV y celular.
4. Compila debug.
5. Haz commit.

## Personalizacion remota Pro

La version Pro debe leer desde:

```text
GET /app/config
```
