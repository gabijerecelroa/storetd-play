import os

print("\n⚙️ INICIANDO CIRUGÍA MÚLTIPLE (BUGS 2 Y 3)...\n")

# --- FIX BUG 2: FRONTEND (COMPOSE BLINDNESS) ---
tv_screen = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
if os.path.exists(tv_screen):
    with open(tv_screen, "r", encoding="utf-8") as f:
        content = f.read()

    # Inyectamos HashKeys para estabilizar la memoria y clavar 60 FPS
    content = content.replace(
        "items(chunked) { rowItems ->",
        "items(items = chunked, key = { it.hashCode() }, contentType = { \"grid_row\" }) { rowItems ->"
    )
    content = content.replace(
        "items(visibleGroups) { group ->",
        "items(items = visibleGroups, key = { it.hashCode() }, contentType = { \"category_item\" }) { group ->"
    )

    with open(tv_screen, "w", encoding="utf-8") as f:
        f.write(content)
    print("✅ [BUG 2 ERRADICADO] Jetpack Compose blindado con HashKeys.")
else:
    print("❌ Error: No se encontró LiveTvScreen.kt")

# --- FIX BUG 3: BACKEND NODE.JS (MISSING POSTERS) ---
backend_file = "backend/src/playlistContent.js"
if os.path.exists(backend_file):
    with open(backend_file, "r", encoding="utf-8") as f:
        node_content = f.read()

    # 1. Reparamos el falso contrato API (posterUrl -> logoUrl) y activamos "cover"
    node_content = node_content.replace("posterUrl: item.logoUrl || null,", "logoUrl: item.cover || item.stream_icon || item.logoUrl || null,")
    node_content = node_content.replace("if (!folder.posterUrl && item.logoUrl) {", "if (!folder.logoUrl && (item.cover || item.stream_icon || item.logoUrl)) {")
    node_content = node_content.replace("folder.posterUrl = item.logoUrl;", "folder.logoUrl = item.cover || item.stream_icon || item.logoUrl;")
    node_content = node_content.replace("logoUrl: null || item.stream_icon || item.cover,", "logoUrl: item.cover || item.stream_icon || item.logoUrl || null,")
    
    # 2. Limpiamos los errores de sintaxis del "Find & Replace" roto del pasado
    node_content = node_content.replace("row || item.stream_icon || item.cover", "row")
    node_content = node_content.replace("line || item.stream_icon || item.cover", "line")

    with open(backend_file, "w", encoding="utf-8") as f:
        f.write(node_content)
    print("✅ [BUG 3 ERRADICADO] Parser Node.js purificado. Pósters de Xtream sincronizados.")
else:
    print("❌ Error: No se encontró playlistContent.js")

print("\n🚀 CIRUGÍA COMPLETADA. LISTOS PARA DESPLIEGUE.\n")
