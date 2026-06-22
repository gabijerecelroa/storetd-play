import os

print("\n========== 1. BUSCANDO LA NAVEGACIÓN Y EL HISTORIAL ==========")
os.system("grep -rn 'navController.navigate(' android/app/src/main/java/com/storetd/play/ | grep -v 'import' | head -n 15")

print("\n========== 2. BUSCANDO LA BARRA LATERAL ==========")
os.system("find android/app/src/main/java/com/storetd/play/ -iname '*Side*.kt' -o -iname '*Drawer*.kt' -o -iname '*Nav*.kt' -o -iname '*Menu*.kt'")

print("\n========== 3. BUSCANDO EL REPRODUCTOR DE VIDEO ==========")
os.system("find android/app/src/main/java/com/storetd/play/ -iname '*Player*.kt' -o -iname '*Video*.kt'")
os.system("grep -rn 'ExoPlayer' android/app/src/main/java/com/storetd/play/ | head -n 10")
