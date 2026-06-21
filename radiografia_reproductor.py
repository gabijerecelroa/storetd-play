import os

print("\n=== 🔎 1. BUSCANDO ARCHIVOS DEL REPRODUCTOR ===")
os.system("find android/app/src/main/ -type f -iname '*player*.kt' -o -iname '*video*.kt' || true")

print("\n=== 🔎 2. ESCANEANDO CONFIGURACIÓN DEL EXOPLAYER ===")
os.system("grep -rn -A 15 'ExoPlayer.Builder' android/app/src/main/java/ || true")

print("\n=== 🔎 3. CONFIGURACIÓN DE RENDER Y BUFFER ===")
os.system("grep -rn -A 5 'DefaultRenderersFactory' android/app/src/main/java/ || true")
os.system("grep -rn -A 5 'DefaultLoadControl' android/app/src/main/java/ || true")

print("\n=== 🔎 4. CREADOR DEL MEDIAITEM ===")
os.system("grep -rn -C 2 'MediaItem.fromUri' android/app/src/main/java/ || true")
