import os

print("\n=== 🔎 1. BUSCANDO LA AMNESIA EN EL NAVEGADOR ===")
os.system("grep -n -A 15 'composable(route = StoreTdPlayScreens.LiveTv.name)' android/app/src/main/java/com/storetd/play/navigation/StoreTdPlayNavHost.kt || true")

print("\n=== 🔎 2. BUSCANDO EL DIBUJO DE LAS CAJAS MORADAS ===")
os.system("grep -n -B 2 -A 10 'AsyncImage(' android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt | head -n 45 || true")
