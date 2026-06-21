import re

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/home/HomeScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Inyectamos los imports de StreamVault si no existen
if "import com.storetd.play.ui.streamvault.components.CategoryRow" not in content:
    imports_to_add = """
import com.storetd.play.ui.streamvault.components.CategoryRow
import com.storetd.play.ui.streamvault.components.PosterCard
"""
    content = content.replace("import com.storetd.play.core.storage.SavedChannel", "import com.storetd.play.core.storage.SavedChannel\n" + imports_to_add)

# 2. Reemplazamos todo desde CarouselSection hacia abajo con la nueva UI híbrida
pattern = r'@Composable\s+fun CarouselSection\(.*'
match = re.search(pattern, content, flags=re.DOTALL)

if match:
    new_ui = """@Composable
fun CarouselSection(title: String, items: List<Channel>, onFocused: (Channel) -> Unit, onClick: (Channel) -> Unit) {
    CategoryRow(
        title = title,
        items = items,
        keySelector = { it.id }, // 🔥 Mantenemos tu sistema anti-temblores
        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
    ) { item ->
        val finalLogo = if (item.logoUrl.isNullOrBlank() || item.logoUrl == "-" || item.logoUrl.lowercase().contains("default")) {
            "http://82.39.109.213:5000/api/tmdb/poster?title=${android.net.Uri.encode(item.name)}"
        } else {
            item.logoUrl
        }
        
        // 🔥 La tarjeta de lujo de StreamVault
        PosterCard(
            imageUrl = finalLogo,
            title = item.name,
            subtitle = null,
            modifier = Modifier
                .width(140.dp)
                .onFocusChanged { if (it.isFocused || it.hasFocus) onFocused(item) }
                .clickable { onClick(item) }
        )
    }
}

@Composable
fun CarouselSectionSaved(title: String, items: List<SavedChannel>, onFocused: (SavedChannel) -> Unit, onClick: (SavedChannel) -> Unit) {
    CategoryRow(
        title = title,
        items = items,
        keySelector = { it.id },
        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
    ) { item ->
        val url = item.streamUrl.lowercase()
        val group = (item.group ?: "").lowercase()
        val isLiveTv = url.contains("/live/") || url.contains("m3uts") || url.contains("tvclub") || group.contains("tv") || group.contains("vivo")

        // 🔥 Inteligencia de formato: Poster normal vs Paisaje (16:9)
        PosterCard(
            imageUrl = item.logoUrl,
            title = item.name,
            subtitle = if (isLiveTv) "TV en Vivo" else null,
            modifier = Modifier
                .width(if (isLiveTv) 240.dp else 140.dp)
                .onFocusChanged { if (it.isFocused || it.hasFocus) onFocused(item) }
                .clickable { onClick(item) }
        )
    }
}
"""
    content = content[:match.start()] + new_ui
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("✅ ¡CIRUGÍA EXITOSA! El ADN de StreamVault ha sido inyectado en el cerebro de StoreTD Play.")
else:
    print("⚠️ No se encontró CarouselSection. ¿Quizás ya se inyectó antes?")
