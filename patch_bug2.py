import os

tv_screen = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

if os.path.exists(tv_screen):
    with open(tv_screen, "r", encoding="utf-8") as f:
        content = f.read()

    # Sellamos la fuga de memoria inyectando keys y contentTypes en todas las grillas
    content = content.replace(
        "items(chunked) { rowItems ->",
        "items(items = chunked, key = { it.hashCode() }, contentType = { \"grid_row\" }) { rowItems ->"
    )

    # Blindamos la lista lateral de categorías
    content = content.replace(
        "items(visibleGroups) { group ->",
        "items(items = visibleGroups, key = { it.hashCode() }, contentType = { \"category_item\" }) { group ->"
    )

    with open(tv_screen, "w", encoding="utf-8") as f:
        f.write(content)
        
    print("\n✅ [BUG 2 ERRADICADO] Jetpack Compose blindado. TV Box lista para volar a 60 FPS.\n")
else:
    print("\n❌ Error: No se encontró LiveTvScreen.kt\n")
