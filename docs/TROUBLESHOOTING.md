# Solucion de errores comunes

## La lista no carga

- Verifica que la URL sea http/https.
- Verifica que responda desde el dispositivo.
- Verifica que el contenido empiece con `#EXTM3U`.
- Revisa si requiere token o cabeceras especiales.

## El canal no reproduce

- Prueba la URL en un reproductor externo autorizado.
- Revisa si el stream es HLS valido.
- Verifica si hay bloqueo geografico.
- Revisa timeout o error HTTP.

## El APK no compila

- Verifica JDK 17.
- Actualiza Android SDK.
- Ejecuta `./gradlew --version`.
- Limpia cache: `./gradlew clean`.
