import os
import re

print(">> 1. REPARANDO EL NÚCLEO DE LOS BOTONES (Cerebro Enjambre)...")
menu_path = "android/app/src/main/java/com/storetd/play/ui/components/PremiumSideMenu.kt"
with open(menu_path, "r", encoding="utf-8") as f:
    menu = f.read()

# 1. Inteligencia Artificial de Bucle Constante (Misil Perseguidor)
ia_logic = """
    val smartFocus: () -> Unit = { 
        if (System.currentTimeMillis() - globalLastLeftPressTime < 1000) { 
            isMenuFocused = true // Orden humana: abrir menú
        } else { 
            isMenuFocused = false // Pánico de TV: Cerrar menú
            coroutineScope.launch { 
                var r = 0
                // Misil Perseguidor: Intenta mandar el foco a las películas 40 veces (espera hasta 4 seg a que carguen)
                while(r < 40) { 
                    kotlinx.coroutines.delay(100) 
                    if (focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)) break 
                    r++ 
                } 
            } 
        } 
    }"""

if "val smartFocus: () -> Unit" not in menu:
    menu = menu.replace("val coroutineScope = rememberCoroutineScope()", "val coroutineScope = rememberCoroutineScope()" + ia_logic)

# 2. EXTIRPAMOS la vieja orden ciega de CADA botoncito individual y conectamos la IA
menu = re.sub(r'\{\s*isMenuFocused\s*=\s*true\s*\}', 'smartFocus', menu)

# 3. Reparamos la Puerta Principal usando un buscador láser infalible
start_idx = menu.find(".onFocusChanged { state ->")
if start_idx != -1:
    brace_count = 0
    in_block = False
    end_idx = -1
    for i in range(start_idx, len(menu)):
        if menu[i] == '{':
            brace_count += 1
            in_block = True
        elif menu[i] == '}':
            brace_count -= 1
            if in_block and brace_count == 0:
                end_idx = i
                break
    if end_idx != -1 and "smartFocus()" not in menu[start_idx:end_idx]:
        new_block = ".onFocusChanged { state -> if (state.hasFocus) smartFocus() else isMenuFocused = false }"
        menu = menu[:start_idx] + new_block + menu[end_idx+1:]

with open(menu_path, "w", encoding="utf-8") as f:
    f.write(menu)

print("   ✅ Botones blindados y Misil Perseguidor instalado.")
