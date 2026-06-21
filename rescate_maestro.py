import os, re, subprocess

print("\n=== 🧹 1. PURGANDO ERRORES DEL REPRODUCTOR ===")
try:
    log = subprocess.check_output('git log --oneline android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt', shell=True).decode('utf-8')
    commits = log.strip().split('\n')
    # Viajamos al pasado hasta el último commit que NO tenga mis errores del Tractor
    clean_hash = None
    for c in commits:
        if "Tractor" not in c:
            clean_hash = c.split(' ')[0]
            break
    
    if clean_hash:
        os.system(f'git checkout {clean_hash} -- android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt')
        print(f"✅ Reproductor restaurado intacto a la versión estable ({clean_hash})")
except Exception as e:
    print(f"Error restaurando: {e}")

file_path = "android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Aplicamos SOLO la cura de la RAM (Sin tocar variables peligrosas)
    content = re.sub(r"300_000,\s*// Max[^\n]*", "45_000,   // Max: 45 seg (Anti-Colapso RAM TV Box)", content)
    content = re.sub(r"20_000,\s*// Min[^\n]*", "15_000,   // Min: 15 seg", content)
    
    if "setPrioritizeTimeOverSizeThresholds" not in content:
        content = content.replace(".setBufferDurationsMs(", ".setPrioritizeTimeOverSizeThresholds(true)\n            .setBufferDurationsMs(")

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("✅ Búfer reducido a 45s con éxito. ¡Evitará que la Dinax se cuelgue!")

print("\n=== 🔎 2. RASTREANDO EL ACTUALIZADOR PARA SALVAR A TUS CLIENTES ===")
os.system("grep -rn -B 2 -A 5 'No se pudo iniciar la descarga' android/app/src/main/java/ || true")
os.system("grep -rn -B 2 -A 5 'getSystemService' android/app/src/main/java/ | grep -C 3 'DOWNLOAD_SERVICE' || true")
