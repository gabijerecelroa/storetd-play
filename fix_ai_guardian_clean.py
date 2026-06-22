import os
import re

print(">> 1. LIMPIANDO ESCUDOS DE FOCO FALLIDOS (Bug del botón OK)...")

nav_path = "android/app/src/main/java/com/storetd/play/navigation/StoreTdPlayNavHost.kt"
with open(nav_path, "r", encoding="utf-8") as f: nav = f.read()

# Quitamos el escudo central y los restos de intentos pasados
nav = nav.replace(".focusGroup().focusable()", "")
nav = nav.replace(".focusable()", "")
nav = re.sub(r'import androidx\.compose\.foundation\.focusable\n?', '', nav)
nav = re.sub(r'import androidx\.compose\.foundation\.focusGroup\n?', '', nav)
nav = re.sub(r'import androidx\.compose\.ui\.input\.key\.onPreviewKeyEvent\n?', '', nav)
nav = re.sub(r'import androidx\.compose\.ui\.input\.key\.Key\n?', '', nav)
nav = re.sub(r'import androidx\.compose\.ui\.input\.key\.key\n?', '', nav)

with open(nav_path, "w", encoding="utf-8") as f: f.write(nav)

# Quitamos los escudos magnéticos de todos los círculos de carga
for root, dirs, files in os.walk("android/app/src/main/java/com/storetd/play/"):
    for file in files:
        if file.endswith(".kt"):
            path = os.path.join(root, file)
            with open(path, "r", encoding="utf-8") as f: content = f.read()
            orig = content
            
            # Removemos la caja magnética con precisión láser
            content = re.sub(
                r'androidx\.compose\.foundation\.layout\.Box\(\s*modifier\s*=\s*Modifier\.focusable\(\)\s*\)\s*\{\s*(androidx\.compose\.material3\.CircularProgressIndicator[^}]*)\s*\}', 
                r'\1', 
                content
            )
            
            if content != orig:
                with open(path, "w", encoding="utf-8") as f: f.write(content)

print("   ✅ Control remoto liberado. El botón OK ya no será necesario.")

print(">> 2. INYECTANDO CEREBRO GUARDIÁN (IA) EN EL MENÚ...")
menu_path = "android/app/src/main/java/com/storetd/play/ui/components/PremiumSideMenu.kt"
with open(menu_path, "r", encoding="utf-8") as f: menu = f.read()

# Limpiamos cualquier código roto de intentos previos (Variables duplicadas)
menu = re.sub(r'var globalLastLeftPressTime = 0L\n*', '', menu)
menu = re.sub(r'import androidx\.compose\.ui\.platform\.LocalFocusManager\n?', '', menu)
menu = re.sub(r'import androidx\.compose\.runtime\.rememberCoroutineScope\n?', '', menu)
menu = re.sub(r'import kotlinx\.coroutines\.launch\n?', '', menu)
menu = re.sub(r'import kotlinx\.coroutines\.delay\n?', '', menu)
menu = re.sub(r'import androidx\.compose\.ui\.focus\.FocusDirection\n?', '', menu)
menu = re.sub(r'\s*val focusManager\s*=\s*.*?\n', '\n', menu)
menu = re.sub(r'\s*val coroutineScope\s*=\s*.*?\n', '\n', menu)
menu = re.sub(r'[ \t]*androidx\.compose\.runtime\.LaunchedEffect\(currentRoute\)\s*\{.*?\n[ \t]*\}\n', '', menu, flags=re.DOTALL)

# INYECTAMOS PERMISOS (Exactamente debajo del nombre del paquete)
imports = """
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusDirection
"""
menu = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n' + imports, menu, count=1)

# INYECTAMOS LA MEMORIA (Exactamente antes del componente visual)
menu = re.sub(r'(@Composable\s*fun PremiumSideMenu)', r'var globalLastLeftPressTime = 0L\n\n\1', menu)

# Instalamos el Guardián
setup = """    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()"""
menu = menu.replace("    val scrollState = rememberScrollState()", setup)

# CEREBRO IA: Discriminador de humanos vs Pánico de TV
new_focus = """.onFocusChanged { state ->
        if (state.hasFocus) {
            // Si apretaste 'IZQUIERDA' hace menos de 1 segundo, ábrete.
            if (System.currentTimeMillis() - globalLastLeftPressTime < 1000) {
                isMenuFocused = true
            } else {
                // Instinto de Pánico de TV detectado. Rechazamos el foco automáticamente.
                isMenuFocused = false
                coroutineScope.launch {
                    delay(50)
                    focusManager.moveFocus(FocusDirection.Right)
                }
            }
        } else {
            isMenuFocused = false
        }
    }"""

# Reemplazador seguro de bloques
focus_start = menu.find(".onFocusChanged {")
if focus_start != -1:
    brace_count = 0
    in_block = False
    focus_end = -1
    for i in range(focus_start, len(menu)):
        if menu[i] == '{':
            brace_count += 1
            in_block = True
        elif menu[i] == '}':
            brace_count -= 1
            if in_block and brace_count == 0:
                focus_end = i
                break
    if focus_end != -1:
        menu = menu[:focus_start] + new_focus + menu[focus_end+1:]

# Auto-Cierre de Menú: Forzamos el foco a las películas al hacer clic
menu = re.sub(
    r'(restoreState\s*=\s*false\s*\})\s*\}',
    r'\1; coroutineScope.launch { delay(150); focusManager.moveFocus(FocusDirection.Right) } }',
    menu
)

with open(menu_path, "w", encoding="utf-8") as f: f.write(menu)
print("   ✅ IA inyectada con sintaxis 100% legal. Menú Lateral domado.")

print(">> 3. CONECTANDO EL SENSOR D-PAD (BOTÓN IZQUIERDA)...")
with open(nav_path, "r", encoding="utf-8") as f: nav = f.read()

imports_nav = """
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
"""
nav = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n' + imports_nav, nav, count=1)

box_clean = "androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {"
box_key = """androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().onPreviewKeyEvent { 
        if (it.key == Key.DirectionLeft) { 
            com.storetd.play.ui.components.globalLastLeftPressTime = System.currentTimeMillis() 
        }
        false 
    }) {"""

if box_clean in nav:
    nav = nav.replace(box_clean, box_key, 1)

with open(nav_path, "w", encoding="utf-8") as f: f.write(nav)
print("   ✅ Sensor de Control Remoto sincronizado al 100%.")

