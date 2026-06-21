import os

# --- 1. ARREGLO DE PADDING EN TV EN VIVO ---
path_tv = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
if os.path.exists(path_tv):
    with open(path_tv, "r", encoding="utf-8") as f:
        content_tv = f.read()
    
    # Cambiamos "horizontal" por "start" y "end" para que Kotlin esté feliz
    content_tv = content_tv.replace(
        "padding(horizontal = 24.dp, top = 32.dp, bottom = 12.dp)",
        "padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 12.dp)"
    )
    
    with open(path_tv, "w", encoding="utf-8") as f:
        f.write(content_tv)
    print("✅ ¡Sintaxis de padding en TV en Vivo corregida!")

# --- 2. EXTIRPACIÓN DEL MENÚ INTRUSO EN INICIO ---
path_home = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/home/HomeScreen.kt"
if os.path.exists(path_home):
    with open(path_home, "r", encoding="utf-8") as f:
        content_home = f.read()
    
    # Buscamos la función TopBarItem y la dejamos vacía
    idx = content_home.find("@Composable\nfun TopBarItem")
    if idx != -1:
        b_start = content_home.find("{", idx)
        b_count = 1
        i = b_start + 1
        while b_count > 0 and i < len(content_home):
            if content_home[i] == '{': b_count += 1
            elif content_home[i] == '}': b_count -= 1
            i += 1
        
        # Reemplazamos toda la función por una versión invisible
        codigo_invisible = "@Composable\nfun TopBarItem(text: String, icon: String, isSelected: Boolean = false, onFocused: () -> Unit, onClick: () -> Unit) { /* Menú central extirpado */ }"
        content_home = content_home[:idx] + codigo_invisible + content_home[i:]
        
        with open(path_home, "w", encoding="utf-8") as f:
            f.write(content_home)
        print("✅ ¡Menú central intruso destruido!")
