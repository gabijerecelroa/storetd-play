import os
import re

print(">> 1. REESTRUCTURANDO EL MOTOR PRINCIPAL A CAPAS NATIVAS...")
nav_path = "android/app/src/main/java/com/storetd/play/navigation/StoreTdPlayNavHost.kt"
with open(nav_path, "r", encoding="utf-8") as f: nav = f.read()

# Inyectamos permisos de espaciado nativos de Android (100% seguros)
if "import androidx.compose.foundation.layout.padding" not in nav:
    nav = nav.replace("import androidx.compose.runtime.Composable", "import androidx.compose.runtime.Composable\nimport androidx.compose.foundation.layout.padding\nimport androidx.compose.ui.unit.dp")

# Extraemos el código de tu Menú y borramos el Row excavadora
menu_regex = r'\s*if\s*\(\s*showSideMenu\s*\)\s*\{\s*com\.storetd\.play\.ui\.components\.PremiumSideMenu\([^}]+\)\s*\}'
match = re.search(menu_regex, nav)

if match:
    menu_block = match.group(0)
    nav = nav[:match.start()] + nav[match.end():]
    
    # Transformamos la Fila (Row) en una Caja de Capas 3D (Box)
    nav = nav.replace("Row(modifier = Modifier.fillMaxSize()) {", "androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {")
    
    # Anclamos las pantallas al fondo con 65 píxeles fijos (Cero Empuje = Cero Lag)
    nav = nav.replace("Box(modifier = Modifier.weight(1f)) {", "androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(start = if (showSideMenu) 65.dp else 0.dp)) {")
    
    # Buscamos el final de nuestra Caja para inyectar el menú por encima de todo
    idx = nav.find("androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {")
    brace_idx = nav.find("{", idx)
    b_count = 1
    i = brace_idx + 1
    in_string = False
    escape = False
    while b_count > 0 and i < len(nav):
        c = nav[i]
        if escape: escape = False
        elif c == '\\': escape = True
        elif c == '"': in_string = not in_string
        elif not in_string:
            if c == '{': b_count += 1
            elif c == '}': b_count -= 1
        i += 1
        
    end_box = i - 1
    nav = nav[:end_box] + menu_block + "\n    " + nav[end_box:]
    
    with open(nav_path, "w", encoding="utf-8") as f:
        f.write(nav)
    print("   ✅ NavHost transformado en Capas 3D. El menú flotará naturalmente.")
else:
    print("   ⚠️ No se encontró la Fila. Estructura probablemente ya cambiada.")

print(">> 2. EXTERMINANDO EL LABERINTO DEL BOTÓN ATRÁS...")
menu_path = "android/app/src/main/java/com/storetd/play/ui/components/PremiumSideMenu.kt"
with open(menu_path, "r", encoding="utf-8") as f: menu = f.read()

# Limpiamos rastros de zIndex de intentos fallidos
menu = menu.replace(".width(65.dp).requiredWidth(width).zIndex(100f)", ".width(width)")
menu = re.sub(r'import androidx\.compose\.ui\.zIndex\.zIndex\n?', '', menu)
menu = re.sub(r'import androidx\.compose\.foundation\.layout\.requiredWidth\n?', '', menu)

# Reemplazamos la orden de "apilar" por la orden de "limpiar memoria" en TODOS los botones del menú
menu = re.sub(
    r'navController\.navigate\((Routes\.[a-zA-Z0-9_]+)\)\s*\{\s*launchSingleTop\s*=\s*true\s*\}',
    r'navController.navigate(\1) { popUpTo(navController.graph.startDestinationId) { saveState = false }; launchSingleTop = true; restoreState = false }',
    menu
)
with open(menu_path, "w", encoding="utf-8") as f: f.write(menu)
print("   ✅ Botones del menú programados para saltos directos.")

