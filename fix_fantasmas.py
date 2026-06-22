import os
import re

print(">> 1. INSTALANDO EL GUARDIÁN DE FOCO (Cero Menús Fantasmas)...")
menu_path = "android/app/src/main/java/com/storetd/play/ui/components/PremiumSideMenu.kt"
with open(menu_path, "r", encoding="utf-8") as f: menu = f.read()

# Inyectamos el pateador de foco que vigilará cada cambio de pantalla
kicker = """    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.compose.runtime.LaunchedEffect(currentRoute) {
        var retries = 0
        while (retries < 4) {
            kotlinx.coroutines.delay(150)
            if (isMenuFocused) {
                focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)
            } else {
                break
            }
            retries++
        }
    }
"""

if "LocalFocusManager.current" not in menu:
    menu = menu.replace(
        "val scrollState = rememberScrollState()",
        "val scrollState = rememberScrollState()\n" + kicker
    )
    with open(menu_path, "w", encoding="utf-8") as f: f.write(menu)
    print("   ✅ Guardián de foco activado y patrullando.")

print(">> 2. INCINERANDO EL HISTORIAL (Orden Nuclear popUpTo 0)...")
nav_path = "android/app/src/main/java/com/storetd/play/navigation/StoreTdPlayNavHost.kt"
with open(nav_path, "r", encoding="utf-8") as f: nav = f.read()

# Buscamos todos los rastros de guardado de historial débiles y los volvemos Nucleares
patterns = [
    r'popUpTo\([^)]+\)\s*\{\s*saveState\s*=\s*(?:true|false)\s*\}\s*;\s*launchSingleTop\s*=\s*true\s*;\s*restoreState\s*=\s*(?:true|false)',
    r'popUpTo\([^)]+\)\s*\{\s*inclusive\s*=\s*(?:true|false)\s*\}\s*;\s*launchSingleTop\s*=\s*true',
    r'popUpTo\([^)]+\)\s*\{\s*saveState\s*=\s*true\s*\}\s*;\s*launchSingleTop\s*=\s*true\s*;\s*restoreState\s*=\s*true'
]
new_nav = r'popUpTo(0) { inclusive = true }; launchSingleTop = true'

for p in patterns:
    nav = re.sub(p, new_nav, nav)
with open(nav_path, "w", encoding="utf-8") as f: f.write(nav)

# Hacemos lo mismo en el Menú Lateral
with open(menu_path, "r", encoding="utf-8") as f: menu = f.read()
for p in patterns:
    menu = re.sub(p, new_nav, menu)
with open(menu_path, "w", encoding="utf-8") as f: f.write(menu)

print("   ✅ BackStack aniquilado. El botón Atrás ahora es definitivo.")
