import re

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/home/HomeScreen.kt"
with open(path, "r", encoding="utf-8") as f: content = f.read()

# Buscamos la tarjeta actual que creamos y la mejoramos con el diseño del video
old_card = r'@Composable\s+fun AppPosterCard.*?\}\s+\}\s+\}'
new_card = """@Composable
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
                .clip(RoundedCornerShape(8.dp))
                .border(if (isFocused) 3.dp else 1.dp, if (isFocused) Color(0xFF3B82F6) else Color.White.copy(alpha=0.1f), RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A)) // El azul oscuro elegante de StreamVault
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 🔥 LA INSIGNIA ESTILO STREAMVAULT (Arriba a la izquierda)
            if (!isLandscape) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.TopStart)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("★", fontSize = 10.sp, color = Color(0xFFFACC15)) // Estrella amarilla
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("8.5/10", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(text = title, color = if (isFocused) Color.White else Color(0xFF94A3B8), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(text = subtitle, color = Color(0xFF64748B), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}"""

content = re.sub(old_card, new_card, content, flags=re.DOTALL)

with open(path, "w", encoding="utf-8") as f: f.write(content)
print("✅ Insignias (Badges) y Colores de StreamVault inyectados.")
