# BITÁCORA COMPLETA - PÓSTERS DE SERIES (STORETD PLAY)
**Fecha:** Junio 2026  
**Usuario:** Gabriel (roa_gabii)  
**Objetivo:** Resolver cajas moradas / pósters que no se muestran en la grilla de series

## 1. Resumen Ejecutivo
- Backend OK: Devuelve `posterUrl` correcto (TMDB).
- Problema en App Android (`LiveTvScreen.kt`).
- Archivo `LiveTvScreen.kt` dañado varias veces por scripts de edición.
- Último estado: Restaurado correctamente con `git checkout`.

## 2. Evolución de Parches
- v4-v5: Inyectar `posterUrl` en `SeriesFolder`
- v6: Cambiar en `NetflixSeriesPosterCard` → `posterUrl ?: logoUrl`
- v7: Usar `PremiumContentSessionCache`
- Logs en backend: Confirmado que devuelve URLs reales

## 3. Diagnóstico Backend (Node.js + PM2)
- Carpeta: `/root/storetd-play/backend`
- Función clave: `getSeriesFoldersLite`
- Logs muestran `posterUrl` correcto.

## 4. Estado Actual
- Archivo restaurado (3355 líneas)
- Listo para agregar logs o fixes limpios

## 5. Comandos Clave
- Restaurar archivo: `git checkout -- android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt`
- Ver logs backend: `pm2 logs backend-gerardo`
- Reiniciar backend: `pm2 restart backend-gerardo`

## Próximos pasos recomendados
1. Agregar log simple en `NetflixSeriesPosterCard`
2. Compilar y probar APK
3. Analizar qué `posterUrl` llega realmente a la UI

---
**Fin de bitácora - 19 Junio 2026**
