import os
import shutil
import re

print("➤ 🧹 Eliminando componentes tóxicos de StreamVault...")
toxic_dir = "/root/storetd-play/android/app/src/main/java/com/storetd/play/ui/streamvault"
if os.path.exists(toxic_dir):
    shutil.rmtree(toxic_dir)

print("➤ 🔧 Restaurando dependencias en MainActivity...")
main_path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/MainActivity.kt"
if os.path.exists(main_path):
    with open(main_path, "r", encoding="utf-8") as f:
        main_content = f.read()
    main_content = main_content.replace("import com.storetd.play.ui.streamvault.theme.StreamVaultTheme\n", "")
    main_content = main_content.replace("StreamVaultTheme {", "StoreTdPlayTheme {")
    with open(main_path, "w", encoding="utf-8") as f:
        f.write(main_content)

print("➤ 🎨 Inyectando réplica de diseño nativo en HomeScreen...")
home_path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/home/HomeScreen.kt"
if os.path.exists(home_path):
    with open(home_path, "r", encoding="utf-8") as f:
        home_content = f.read()

    # Limpiar los imports rotos
    home_content = home_content.replace("import com.storetd.play.ui.streamvault.components.CategoryRow\n", "")
    home_content = home_content.replace("import com.storetd.play.ui.streamvault.components.PosterCard\n", "")

    # Reemplazar desde CarouselSection hacia abajo con las tarjetas réplica
    pattern = r'@Composable\s+fun CarouselSection\(.*'
    match = re.search(pattern, home_content, flags=re.DOTALL)

    new_ui = """@Composable
fun <T> AppCategoryRow(title: String, items: List<T>, keySelector: (T) -> Any, itemContent: @Composable (T) -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)) {
        Text(text = title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 48.dp, bottom = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items.size, key = { index -> keySelector(items[index]) }) { index ->
                itemContent(items[index])
            }
        }
    }
}

@Composable
fun AppPosterCard(imageUrl: String?, title: String, subtitle: String?, isLandscape: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")

    Column(
        modifier = Modifier
            .width(if (isLandscape) 240.dp else 140.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isLandscape) 16f/9f else 2f/3f)
                .clip(RoundedCornerShape(12.dp))
                .border(3.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(text = title, color = if (isFocused) Color.White else Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(text = subtitle, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun CarouselSection(title: String, items: List<Channel>, onFocused: (Channel) -> Unit, onClick: (Channel) -> Unit) {
    AppCategoryRow(title = title, items = items, keySelector = { it.id }) { item ->
        val finalLogo = if (item.logoUrl.isNullOrBlank() || item.logoUrl == "-" || item.logoUrl.lowercase().contains("default")) {
            "http://82.39.109.213:5000/api/tmdb/poster?title=${android.net.Uri.encode(item.name)}"
        } else item.logoUrl

        AppPosterCard(imageUrl = finalLogo, title = item.name, subtitle = null, isLandscape = false, onFocused = { onFocused(item) }) { onClick(item) }
    }
}

@Composable
fun CarouselSectionSaved(title: String, items: List<SavedChannel>, onFocused: (SavedChannel) -> Unit, onClick: (SavedChannel) -> Unit) {
    AppCategoryRow(title = title, items = items, keySelector = { it.id }) { item ->
        val url = item.streamUrl.lowercase()
        val group = (item.group ?: "").lowercase()
        val isLiveTv = url.contains("/live/") || url.contains("m3uts") || url.contains("tvclub") || group.contains("tv") || group.contains("vivo")

        AppPosterCard(imageUrl = item.logoUrl, title = item.name, subtitle = if (isLiveTv) "TV en Vivo" else null, isLandscape = isLiveTv, onFocused = { onFocused(item) }) { onClick(item) }
    }
}
"""
    if match:
        home_content = home_content[:match.start()] + new_ui
        with open(home_path, "w", encoding="utf-8") as f:
            f.write(home_content)
        print("✅ Diseño inyectado con éxito.")

print("🚀 Limpieza finalizada.")
