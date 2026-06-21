import re

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/home/HomeScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Reemplazamos la función de los botones viejos por el nuevo diseño "Pill"
old_func = r'@Composable\s+fun QuickButton.*?\}\s+\}'
new_func = """@Composable
fun TopBarItem(text: String, icon: String, isSelected: Boolean = false, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = if (isFocused || isSelected) Color(0xFF2563EB) else Color.Transparent // Azul StreamVault
    val contentColor = if (isFocused || isSelected) Color.White else Color(0xFF94A3B8) // Gris claro elegante

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50)) // Borde 100% circular (Píldora)
            .background(bgColor)
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(icon, color = contentColor, fontSize = 16.sp)
        Text(text, color = contentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}"""
content = re.sub(old_func, new_func, content, flags=re.DOTALL)

# 2. Inyectamos la estructura exacta del menú del video
new_buttons = """TopBarItem("Inicio", "🏠", isSelected = true, onFocused = { heroTitle = "Inicio"; heroSubtitle = "Panel Principal"; currentBgUrl = null }) { }
                        TopBarItem("TV en vivo", "▶", onFocused = { heroTitle = "TV en Vivo"; heroSubtitle = "Canales en directo"; currentBgUrl = null }) { onOpenLiveTv() }
                        TopBarItem("Películas", "★", onFocused = { heroTitle = "Películas"; heroSubtitle = "Explorá todo nuestro catálogo"; currentBgUrl = null }) { onOpenMovies() }
                        TopBarItem("Serie", "≡", onFocused = { heroTitle = "Series"; heroSubtitle = "Tus temporadas favoritas"; currentBgUrl = null }) { onOpenSeries() }
                        TopBarItem("Downloads", "↓", onFocused = { heroTitle = "Descargas"; heroSubtitle = "Contenido sin conexión"; currentBgUrl = null }) { }
                        TopBarItem("Guía", "ℹ", onFocused = { heroTitle = "Guía"; heroSubtitle = "Programación de TV"; currentBgUrl = null }) { onOpenEpg() }
                        TopBarItem("Buscar", "🔍", onFocused = { heroTitle = "Buscar"; heroSubtitle = "Encuentra contenido"; currentBgUrl = null }) { }
                        TopBarItem("Favoritos", "❤️", onFocused = { heroTitle = "Favoritos"; heroSubtitle = "Tu lista guardada"; currentBgUrl = null }) { onOpenFavorites() }"""

# Remplazamos los botones viejos
content = re.sub(r'QuickButton\("📺 TV en Vivo".*?onOpenFavorites\(\)\s*\}', new_buttons, content, flags=re.DOTALL)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("✅ Menú superior idéntico a StreamVault inyectado con éxito.")
