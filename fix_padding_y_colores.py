import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/home/HomeScreen.kt"

if os.path.exists(path):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. ELIMINAR EL ERROR DE COMPILACIÓN (Padding estricto)
    content = content.replace(
        "Modifier.padding(horizontal = 48.dp, bottom = 16.dp)",
        "Modifier.padding(start = 48.dp, end = 48.dp, bottom = 16.dp)"
    )

    # 2. INYECTAR LOS CÓDIGOS HEX EXACTOS DE APPCOLORS.KT
    content = content.replace("Color(0xFF3B82F6)", "Color(0xFF69A8FF)") # Brand Selection
    content = content.replace("Color(0xFF2563EB)", "Color(0xFF69A8FF)") # Brand Selection
    content = content.replace("Color(0xFF1E1E1E)", "Color(0xFF162338)") # SurfaceElevated (Tarjetas)
    content = content.replace("Color(0xFF0F172A)", "Color(0xFF162338)") # SurfaceElevated (Tarjetas)
    content = content.replace("Color(0xFF94A3B8)", "Color(0xFFBBC6D8)") # TextSecondary
    content = content.replace("Color(0xFF64748B)", "Color(0xFF7F8DA5)") # TextTertiary
    content = content.replace("Color(0xFFFACC15)", "Color(0xFFFFC766)") # Warning/Star (Estrellas)

    # 3. PINTAR EL FONDO GLOBAL CON EL COLOR "CANVAS" DE STREAMVAULT
    if "Modifier.fillMaxSize()" in content and "Color(0xFF07111B)" not in content:
        content = content.replace(
            "modifier = Modifier.fillMaxSize(),",
            "modifier = Modifier.fillMaxSize().background(Color(0xFF07111B)),"
        )

    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

    print("✅ ¡Sintaxis reparada y paleta de colores oficial de StreamVault inyectada!")
else:
    print("⚠️ No se encontró HomeScreen.kt")
