import os
import re

print(">> 1. REPARANDO EL HISTORIAL DE NAVEGACIÓN (BackStack)...")
nav_path = "android/app/src/main/java/com/storetd/play/navigation/StoreTdPlayNavHost.kt"
if os.path.exists(nav_path):
    with open(nav_path, "r", encoding="utf-8") as f: content = f.read()
    content = re.sub(
        r'navController\.navigate\((Routes\.[A-Za-z0-9_]+)\)(?!\s*\{)',
        r'navController.navigate(\1) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true }',
        content
    )
    with open(nav_path, "w", encoding="utf-8") as f: f.write(content)

print(">> 2. ACELERANDO EL MENÚ LATERAL...")
menu_path = "android/app/src/main/java/com/storetd/play/ui/components/PremiumSideMenu.kt"
if os.path.exists(menu_path):
    with open(menu_path, "r", encoding="utf-8") as f: menu_content = f.read()
    menu_content = re.sub(
        r'(animate[A-Za-z]+AsState\(\s*targetValue\s*=\s*[^,)]+)(?!,\s*animationSpec)',
        r'\1, animationSpec = androidx.compose.animation.core.tween(150)',
        menu_content
    )
    with open(menu_path, "w", encoding="utf-8") as f: f.write(menu_content)

print(">> 3. INYECTANDO EXOPLAYER Y AJUSTANDO TEXTOS...")
tv_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
if os.path.exists(tv_path):
    with open(tv_path, "r", encoding="utf-8") as f: tv = f.read()

    # Añadir Memoria del Clic (PreviewItem)
    if "var previewItem by" not in tv:
        tv = tv.replace(
            "val isTvMode = !isCompact",
            "val isTvMode = !isCompact\n        var previewItem by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Any?>(null) }"
        )

    # Inyectar Lógica de Doble Clic
    tv = re.sub(
        r'(modifier\s*=\s*Modifier\.weight\(0\.35f\)[^}]*\}[\s\S]*?)(onPlay\s*=\s*onPlay)',
        r'\1onPlay = { item -> if (previewItem == item) onPlay(item) else previewItem = item }',
        tv,
        count=1
    )

    # Reemplazar la caja vacía por la Caja Inteligente del Reproductor
    old_box = """androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color(0xFF0B1724)).border(1.dp, androidx.compose.ui.graphics.Color(0x264C6D95), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                            androidx.compose.material3.Text("Seleccione un canal", color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            androidx.compose.material3.Text("En modo Pro, el primer clic previsualiza la transmisión y el segundo abre la reproducción completa.", color = androidx.compose.ui.graphics.Color(0xFFBBC6D8), fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }"""
                    
    new_box = """androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color(0xFF0B1724)).border(1.dp, androidx.compose.ui.graphics.Color(0x264C6D95), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewItem != null) {
                            val itemString = previewItem.toString()
                            val urlRegex = "(?i)(?:url|stream_url|streamUrl)=([^,\\\\s)]+)".toRegex()
                            val match = urlRegex.find(itemString)
                            val itemUrl = match?.groups?.get(1)?.value?.trim()?.removeSurrounding("\\"")?.removeSurrounding("'")
                            
                            if (!itemUrl.isNullOrBlank() && itemUrl.startsWith("http")) {
                                MiniPlayerPreview(itemUrl)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                                    androidx.compose.material3.Text("Cargando conexión...", color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                                androidx.compose.material3.Text("Seleccione un canal", color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                androidx.compose.material3.Text("En modo Pro, el primer clic previsualiza la transmisión y el segundo abre la reproducción completa.", color = androidx.compose.ui.graphics.Color(0xFFBBC6D8), fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }"""
    tv = tv.replace(old_box, new_box)

    # El Motor Interno de ExoPlayer (La magia visual)
    player_fn = """
@androidx.compose.runtime.Composable
fun MiniPlayerPreview(itemUrl: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = androidx.compose.runtime.remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            volume = 0.5f // Arranca a mitad de volumen por elegancia
        }
    }
    androidx.compose.runtime.DisposableEffect(itemUrl) {
        val mediaItem = androidx.media3.common.MediaItem.fromUri(itemUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        onDispose { exoPlayer.stop() }
    }
    androidx.compose.runtime.DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                player = exoPlayer
                useController = true // Controles sutiles activados
                setShowNextButton(false)
                setShowPreviousButton(false)
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = androidx.compose.ui.Modifier.fillMaxSize()
    )
}
"""
    if "fun MiniPlayerPreview" not in tv:
        tv += player_fn

    # Achicamos y refinamos los textos
    tv = tv.replace("fontSize = 12.sp, lineHeight = 14.sp", "fontSize = 10.sp, lineHeight = 12.sp")
    tv = tv.replace("horizontal = 12.dp, vertical = 8.dp", "horizontal = 8.dp, vertical = 8.dp")
    tv = tv.replace("heightIn(min = 40.dp)", "heightIn(min = 34.dp)")

    with open(tv_path, "w", encoding="utf-8") as f: f.write(tv)
    print("✅ ¡ExoPlayer inyectado exitosamente y fluidez PRO activada!")

