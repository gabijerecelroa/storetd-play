import os
import re

print(">> 2. INYECTANDO CEREBRO IA EN EL MENÚ LATERAL...")
menu_path = "android/app/src/main/java/com/storetd/play/ui/components/PremiumSideMenu.kt"
with open(menu_path, "r", encoding="utf-8") as f:
    menu = f.read()

# A. Imports legales (Estrictamente en la cabecera)
imports = """
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusDirection
"""
if "import androidx.compose.ui.platform.LocalFocusManager" not in menu:
    menu = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n' + imports, menu, count=1)

# B. Variable global de memoria (Justo antes de la función UI)
if "var globalLastLeftPressTime = 0L" not in menu:
    menu = re.sub(r'(@Composable\s*fun PremiumSideMenu)', r'var globalLastLeftPressTime = 0L\n\n\1', menu, count=1)

# C. Herramientas de control
setup = """    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()"""
menu = menu.replace("    val scrollState = rememberScrollState()", setup, 1)

# D. Cerebro IA (Rechazo de Pánico)
old_focus = ".onFocusChanged { isMenuFocused = it.hasFocus }"
new_focus = """.onFocusChanged { state ->
                if (state.hasFocus) {
                    if (System.currentTimeMillis() - globalLastLeftPressTime < 1000) {
                        isMenuFocused = true
                    } else {
                        // IA: Instinto de pánico detectado, rechazar foco
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
menu = menu.replace(old_focus, new_focus)

# E. Botón Atrás Nuclear y Auto-Cierre
menu = re.sub(
    r'popUpTo\([^)]+\)\s*\{\s*saveState\s*=\s*false\s*\}\s*;\s*launchSingleTop\s*=\s*true\s*;\s*restoreState\s*=\s*false\s*\}',
    r'popUpTo(0) { inclusive = true }; launchSingleTop = true }; coroutineScope.launch { delay(150); focusManager.moveFocus(FocusDirection.Right) }',
    menu
)

with open(menu_path, "w", encoding="utf-8") as f:
    f.write(menu)
print("   ✅ IA conectada con sintaxis perfecta.")


print(">> 3. CONECTANDO SENSOR HUMANO (D-PAD) AL MOTOR CENTRAL...")
nav_path = "android/app/src/main/java/com/storetd/play/navigation/StoreTdPlayNavHost.kt"
with open(nav_path, "r", encoding="utf-8") as f:
    nav = f.read()

# A. Imports
nav_imports = """
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import com.storetd.play.ui.components.globalLastLeftPressTime
"""
if "import androidx.compose.ui.input.key.Key" not in nav:
    nav = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n' + nav_imports, nav, count=1)

# B. Envoltorio Sensor Teclado
box_old = "androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {"
box_new = """androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().onPreviewKeyEvent { 
        if (it.key == Key.DirectionLeft) { 
            globalLastLeftPressTime = System.currentTimeMillis() 
        }
        false 
    }) {"""
nav = nav.replace(box_old, box_new, 1)

# C. Botón Atrás Nuclear en el Menú Superior
nav = re.sub(
    r'popUpTo\([^)]+\)\s*\{\s*saveState\s*=\s*false\s*\}\s*;\s*launchSingleTop\s*=\s*true\s*;\s*restoreState\s*=\s*false',
    r'popUpTo(0) { inclusive = true }; launchSingleTop = true',
    nav
)

with open(nav_path, "w", encoding="utf-8") as f:
    f.write(nav)
print("   ✅ Sensor D-PAD y Botón Atrás instalados. Todo listo para la victoria.")

