import os

print("\n=== 🔎 1. BUSCANDO EL ERROR EN EL BACKEND ===")
os.system("grep -rn -C 2 'get_series' backend/src/ | head -n 30 || true")
os.system("grep -rn -C 2 'cover' backend/src/ | head -n 30 || true")
os.system("grep -rn 'stream_icon' backend/src/ | head -n 30 || true")

print("\n=== 🔎 2. VERIFICANDO LA CARPETA EN ANDROID ===")
os.system("grep -rn -A 10 'fun SeriesFolderItem' android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt || true")
