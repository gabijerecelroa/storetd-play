import os
import re

print(">> 1. LIMPIANDO LA MEMORIA DE NAVEGACIÓN...")
nav_path = "android/app/src/main/java/com/storetd/play/navigation/StoreTdPlayNavHost.kt"
with open(nav_path, "r", encoding="utf-8") as f: nav = f.read()

# Apagamos el guardado de historial fantasma para que "Atrás" sea limpio y directo
nav = nav.replace("saveState = true", "saveState = false")
nav = nav.replace("restoreState = true", "restoreState = false")

with open(nav_path, "w", encoding="utf-8") as f: f.write(nav)
print("   ✅ Botón Atrás directo (sin laberintos).")

print(">> 2. ELIMINANDO EL LAG DEL MENÚ (Corte Instantáneo)...")
menu_path = "android/app/src/main/java/com/storetd/play/ui/components/PremiumSideMenu.kt"
with open(menu_path, "r", encoding="utf-8") as f: menu = f.read()

# Forzamos a 0 milisegundos las animaciones del menú para no asfixiar a la TV
menu = re.sub(
    r'animateDpAsState\(if\s*\([^)]+\)\s*220\.dp\s*else\s*65\.dp[^)]*\)',
    r'animateDpAsState(if (isMenuFocused) 220.dp else 65.dp, animationSpec = androidx.compose.animation.core.tween(0))',
    menu
)

menu = re.sub(
    r'animateFloatAsState\(if\s*\([^)]+\)\s*1\.05f\s*else\s*1f[^)]*\)',
    r'animateFloatAsState(if (isFocused) 1.05f else 1f, animationSpec = androidx.compose.animation.core.tween(0))',
    menu
)

with open(menu_path, "w", encoding="utf-8") as f: f.write(menu)
print("   ✅ Animaciones apagadas (0 Lag, rendimiento absoluto a 60 FPS).")

