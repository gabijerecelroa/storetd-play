import os, re

print("\n⚙️ INICIANDO CIRUGÍA APK: FASE 1 (NAVEGACIÓN Y PÓSTERS)...\n")

# --- 1. FIX PÓSTERS EN SERIES ---
# Buscamos dónde se renderizan las series (probablemente en SeriesScreen.kt o un componente similar)
series_screen = "android/app/src/main/java/com/storetd/play/feature/vod/SeriesScreen.kt" 
# Nota: Si el archivo no se llama así, el script fallará limpiamente y buscaremos el correcto.
if os.path.exists(series_screen):
    with open(series_screen, "r", encoding="utf-8") as f:
        content = f.read()

    # Inyectamos el AsyncImage para que lea 'posterUrl'
    if "posterUrl" not in content:
        content = content.replace(
            "model = series.logoUrl", 
            "model = series.posterUrl ?: series.logoUrl"
        )
        content = content.replace(
            "model = ImageRequest.Builder(LocalContext.current).data(series.logoUrl)",
            "model = ImageRequest.Builder(LocalContext.current).data(series.posterUrl ?: series.logoUrl)"
        )
        with open(series_screen, "w", encoding="utf-8") as f:
            f.write(content)
        print("✅ [PÓSTERS] Lógica de imágenes en Series actualizada.")
else:
    print("⚠️ No se encontró SeriesScreen.kt. (Verificaremos luego el nombre del archivo).")

# --- 2. FIX NAVEGACIÓN (BOTÓN ATRÁS Y CARPETAS) ---
nav_file = "android/app/src/main/java/com/storetd/play/navigation/AppNavigation.kt"
if os.path.exists(nav_file):
    with open(nav_file, "r", encoding="utf-8") as f:
        nav_content = f.read()

    # Buscamos las llamadas a navigateTo y las reemplazamos por launchSingleTop
    # Esto evita que se apilen ventanas una sobre otra.
    if "launchSingleTop = true" not in nav_content:
        nav_content = nav_content.replace(
            "navController.navigate(route)",
            "navController.navigate(route) { launchSingleTop = true; restoreState = true }"
        )
        nav_content = nav_content.replace(
            "navController.popBackStack()",
            "navController.navigateUp() // Fix: Usar navigateUp para un comportamiento más natural en Android TV"
        )
        
        with open(nav_file, "w", encoding="utf-8") as f:
            f.write(nav_content)
        print("✅ [NAVEGACIÓN] Botón Atrás y comportamiento de ventanas corregido.")
else:
     print("⚠️ No se encontró AppNavigation.kt. (Verificaremos luego el nombre del archivo).")

print("\n🚀 FASE 1 COMPLETADA (En espera de verificación de archivos).\n")
