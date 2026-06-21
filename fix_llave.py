import re

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/feature/home/HomeScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Buscamos el final exacto de la tarjeta y limpiamos cualquier llave sobrante hasta el próximo bloque
pattern = r'overflow = TextOverflow\.Ellipsis\)\n[\s\}]*@Composable\s+fun CarouselSection'
replacement = "overflow = TextOverflow.Ellipsis)\n            }\n        }\n    }\n}\n\n@Composable\nfun CarouselSection"

content = re.sub(pattern, replacement, content)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ ¡Llave fantasma (Syntax Error) purgada con éxito!")
