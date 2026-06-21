import os, re

print("\n⚙️ INICIANDO CIRUGÍA APK: FASE 1 (NAVEGACIÓN Y PÓSTERS)...\n")

# --- 1. FIX PÓSTERS EN SERIES (LiveTvScreen.kt) ---
tv_screen = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
if os.path.exists(tv_screen):
    with open(tv_screen, "r", encoding="utf-8") as f:
        content = f.read()

    # Buscamos el renderizado de la imagen de la carpeta y lo actualizamos para soportar posterUrl
    old_folder_img = """        ) {
            if (!folder.logoUrl.isNullOrBlank()) {"""
    
    new_folder_img = """        ) {
            val imageToUse = folder.posterUrl ?: folder.logoUrl
            if (!imageToUse.isNullOrBlank()) {"""

    if old_folder_img in content:
        content = content.replace(old_folder_img, new_folder_img)
        # Actualizamos también la llamada a AsyncImage para usar la nueva variable
        content = content.replace(
            "painter = rememberAsyncImagePainter(folder.logoUrl),",
            "painter = rememberAsyncImagePainter(imageToUse),"
        )
        with open(tv_screen, "w", encoding="utf-8") as f:
            f.write(content)
        print("✅ [PÓSTERS] Lógica de imágenes en Series (Carpetas) actualizada.")
    else:
        print("⚠️ [PÓSTERS] No se encontró el bloque exacto en LiveTvScreen.kt. (Puede que ya esté corregido).")
else:
    print("❌ Error: No se encontró LiveTvScreen.kt")


# --- 2. FIX NAVEGACIÓN (StoreTdPlayNavHost.kt) ---
nav_file = "android/app/src/main/java/com/storetd/play/navigation/StoreTdPlayNavHost.kt"
if os.path.exists(nav_file):
    with open(nav_file, "r", encoding="utf-8") as f:
        nav_content = f.read()

    # Modificamos las llamadas de navegación para evitar el apilamiento excesivo
    # Buscamos patrones comunes de navegación hacia detalles y agregamos launchSingleTop
    
    # Ejemplo: Reemplazar navController.navigate("ruta") con navController.navigate("ruta") { launchSingleTop = true }
    # Nota: Esta es una aproximación general. La implementación exacta depende de cómo estén escritas las funciones de navegación.
    
    updated_nav = re.sub(
        r'navController\.navigate\((.*?)\)', 
        r'navController.navigate(\1) { launchSingleTop = true }', 
        nav_content
    )
    
    if updated_nav != nav_content:
        with open(nav_file, "w", encoding="utf-8") as f:
            f.write(updated_nav)
        print("✅ [NAVEGACIÓN] Comportamiento de ventanas (SingleTop) aplicado a la navegación principal.")
    else:
        print("⚠️ [NAVEGACIÓN] No se detectaron llamadas navigate() simples para modificar. (Puede que ya usen lambdas).")

else:
     print("❌ Error: No se encontró StoreTdPlayNavHost.kt")

print("\n🚀 FASE 1 COMPLETADA.\n")
