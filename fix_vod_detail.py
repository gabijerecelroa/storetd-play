import re
import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/vod/VodDetailScreen.kt"

if os.path.exists(path):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Buscamos el punto exacto donde termina tu lógica de backend para no romper nada
    marker = "// MAGMA_FRESH_SOURCE_SELECTOR_END\n    }"
    idx = content.find(marker)

    if idx != -1:
        # Mantenemos todo el archivo hasta este punto exacto
        new_content = content[:idx + len(marker)]

        # Inyectamos el diseño idéntico a StreamVault
        new_ui = """

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07111B)) // StreamVault Canvas (Fondo oficial)
    ) {
        val backdropUrl = info?.backdropPath?.let { "https://image.tmdb.org/t/p/original$it" } ?: logoUrl

        // 1. IMAGEN DE FONDO GIGANTE (Backdrop)
        AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(backdropUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.5f) // Difuminado cinematográfico
        )

        // 2. DEGRADADO OSCURO ESTILO STREAMVAULT (HeroTop a HeroBottom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x1A07111B), // Casi transparente arriba
                            Color(0xCC07111B), // HeroTop
                            Color(0xFF07111B)  // Canvas sólido abajo
                        ),
                        startY = 0f,
                        endY = 1400f
                    )
                )
        )

        // 3. CONTENIDO DE LA PELÍCULA (Textos y Botones)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(56.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Insignias (Badges)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = info?.releaseDate?.take(4) ?: "2024",
                    color = Color(0xFFBBC6D8), // TextSecondary
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "★ ${info?.voteAverage ?: "8.5"}",
                    color = Color(0xFFFFC766), // Warning (StreamVault Star)
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .border(1.dp, Color(0x264C6D95), RoundedCornerShape(4.dp)) // Outline Color
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "4K HDR",
                        color = Color(0xFFBBC6D8), // TextSecondary
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Título
            Text(
                text = info?.title ?: channelName,
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 48.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sinopsis
            Text(
                text = info?.overview ?: "Sin descripción disponible por el momento.",
                color = Color(0xFFBBC6D8), // TextSecondary
                fontSize = 16.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(if (isLandscape) 0.6f else 1f),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Botones Píldora
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var isPlayFocused by remember { mutableStateOf(false) }
                var isBackFocused by remember { mutableStateOf(false) }

                // Botón Reproducir
                Button(
                    onClick = { handlePlayRequest() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayFocused) Color.White else Color(0xFFF5F7FB).copy(alpha = 0.9f),
                        contentColor = Color(0xFF07111B)
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { isPlayFocused = it.isFocused || it.hasFocus }
                ) {
                    Text(
                        text = playButtonText,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Botón Volver
                Button(
                    onClick = { onBack() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBackFocused) Color.White else Color(0xFF223754), // SurfaceAccent
                        contentColor = if (isBackFocused) Color(0xFF07111B) else Color.White
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.onFocusChanged { isBackFocused = it.isFocused || it.hasFocus }
                ) {
                    Text(
                        text = "Volver al menú",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
"""
        new_content += new_ui

        with open(path, "w", encoding="utf-8") as f:
            f.write(new_content)
        print("✅ Pantalla de detalles de StreamVault inyectada correctamente.")
    else:
        print("⚠️ No se encontró el marcador del backend. Revisa el archivo.")
else:
    print("⚠️ No se encontró VodDetailScreen.kt")
