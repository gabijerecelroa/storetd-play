import os

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
if not os.path.exists(file_path):
    print("❌ Archivo no encontrado")
else:
    with open(file_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    def grep(keyword, before, after):
        print(f"\n=== 🔎 BUSCANDO: {keyword} ===")
        count = 0
        for i, line in enumerate(lines):
            if keyword in line:
                start = max(0, i - before)
                end = min(len(lines), i + after + 1)
                for j in range(start, end):
                    print(f"{j+1}: {lines[j].rstrip()}")
                print("-" * 40)
                count += 1
                if count >= 2: break

    grep("var lazySeriesFolders", 1, 4)
    grep("var lazySeriesEpisodes", 1, 4)
    grep("val posterUrl =", 8, 8)
    grep("BackHandler", 1, 20)
