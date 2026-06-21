import os

def check(file, keywords, context=5):
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
print(" 🕵️‍♂️ AUDITORÍA MAESTRA: PROYECTO NETFLIX 🍿")
print("="*50)

print("\n🔍 ===== 1. TARJETAS DE DISEÑO (Android) =====")
ui_path = "android/app/src/main/java/com/storetd/play/ui/components"
if os.path.exists(ui_path):
    for f in os.listdir(ui_path):
        if "Card.kt" in f or "Item.kt" in f:
            print(f"\n[{f}]")
            with open(f"{ui_path}/{f}", 'r', encoding='utf-8') as file:
                print("".join(file.readlines()[:45]))

print("\n🔍 ===== 2. HOME SCREEN (Android) =====")
check("android/app/src/main/java/com/storetd/play/feature/home/HomeScreen.kt", ["Estrenos y Recomendados", "Películas Populares", "Series"], 3)
check("android/app/src/main/java/com/storetd/play/feature/home/HomeViewModel.kt", ["fetch", "load", "shuffle", "random"], 3)

print("\n🔍 ===== 3. BACKEND (TMDB y Series) =====")
check("backend/src/server.js", ["tmdb", "get_series", "get_vod_streams"], 3)
check("backend/src/playlistContent.js", ["cover", "stream_icon", "logoUrl"], 2)

print("\n🔍 ===== 4. REPRODUCTOR (Siguiente Capítulo) =====")
check("android/app/src/main/java/com/storetd/play/feature/player/PlayerScreen.kt", ["onNext", "episode", "playlist", "next"], 3)
check("android/app/src/main/java/com/storetd/play/feature/vod/VodDetailScreen.kt", ["episodes", "season", "onPlay"], 3)

print("\n================ FIN DE LA AUDITORÍA ================\n")
