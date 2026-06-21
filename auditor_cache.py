import os

def check(file, keywords, context=4):
    if not os.path.exists(file): return
    with open(file, 'r', encoding='utf-8', errors='ignore') as f:
        lines = f.readlines()
    for kw in keywords:
        print(f"\n➤ Buscando '{kw}' en {file}:")
        found = False
        for i, l in enumerate(lines):
            if kw.lower() in l.lower():
                found = True
                start = max(0, i - context)
                end = min(len(lines), i + context + 1)
                for j in range(start, end):
                    print(f"  {j+1}: {lines[j].rstrip()}")
                print("  " + "-"*30)
        if not found:
            print("  (No encontrado)")

print("\n" + "="*50)
print(" 🕵️‍♂️ RADIOGRAFÍA DE CACHÉ Y ACTUALIZACIONES 🔄")
print("="*50)

print("\n🔍 ===== 1. BACKEND Y SUPABASE (Node.js) =====")
check("backend/src/server.js", ["cache", "refresh", "supabase", "/app/config", "/auth/status"])
check("backend/src/playlistContent.js", ["cache", "refresh", "clear"])

print("\n🔍 ===== 2. ANDROID (Peticiones del Televisor) =====")
check("android/app/src/main/java/com/storetd/play/core/api/OptimizedContentApi.kt", ["autoRefresh", "cache", "clear"])
check("android/app/src/main/java/com/storetd/play/feature/home/HomeViewModel.kt", ["refresh", "force"])
check("android/app/src/main/java/com/storetd/play/feature/settings/SettingsScreen.kt", ["limpiar", "cache", "actualizar"])

print("\n================ FIN DE LA RADIOGRAFÍA ================\n")
