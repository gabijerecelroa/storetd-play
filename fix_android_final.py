import os

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Agregamos 'posterUrl' al Data Class SeriesFolder
    old_data_class = """private data class SeriesFolder(
    val key: String,
    val title: String,
    val group: String,
    val logoUrl: String?,"""
    
    new_data_class = """private data class SeriesFolder(
    val key: String,
    val title: String,
    val group: String,
    val logoUrl: String?,
    val posterUrl: String? = null,"""
    
    if old_data_class in content:
        content = content.replace(old_data_class, new_data_class)
        print("✅ [MODELO] 'posterUrl' agregado a SeriesFolder.")
    else:
        print("⚠️ [MODELO] No se pudo encontrar o ya se actualizó el Data Class SeriesFolder.")

    # 2. Actualizamos cómo se construye SeriesFolder desde la API Lite
    old_mapper = """                                    SeriesFolder(
                                        key = lite.key,
                                        title = lite.title,
                                        group = lite.group,
                                        logoUrl = lite.posterUrl,
                                        episodes = emptyList() // Se carga on-demand
                                    )"""
    new_mapper = """                                    SeriesFolder(
                                        key = lite.key,
                                        title = lite.title,
                                        group = lite.group,
                                        logoUrl = lite.posterUrl,
                                        posterUrl = lite.posterUrl,
                                        episodes = emptyList() // Se carga on-demand
                                    )"""
    if old_mapper in content:
        content = content.replace(old_mapper, new_mapper)
        print("✅ [MAPPER] Asignación de 'posterUrl' corregida.")
    else:
        print("⚠️ [MAPPER] No se encontró el mapeo de SeriesFolder.")

    # 3. Actualizamos la visualización (AsyncImage) en las carpetas de Series
    old_image_render = """        ) {
            if (!folder.logoUrl.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(folder.logoUrl),
                    contentDescription = folder.title,
                    modifier = Modifier.fillMaxSize()
                )
            } else {"""
            
    new_image_render = """        ) {
            val imageToUse = folder.posterUrl ?: folder.logoUrl
            if (!imageToUse.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(imageToUse),
                    contentDescription = folder.title,
                    modifier = Modifier.fillMaxSize()
                )
            } else {"""

    if old_image_render in content:
        content = content.replace(old_image_render, new_image_render)
        print("✅ [UI] Renderizado de Pósters (AsyncImage) actualizado.")
    else:
        print("⚠️ [UI] No se encontró el bloque de renderizado de la imagen. (Puede que ya esté corregido).")

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("\n🚀 CIRUGÍA KOTLIN COMPLETADA.\n")
else:
    print("❌ Error: No se encontró LiveTvScreen.kt")
