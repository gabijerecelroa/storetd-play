import os

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
if not os.path.exists(file_path):
    print("❌ Archivo no encontrado")
else:
    with open(file_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    def grep(name, keyword, before, after):
        print(f"\n=== 🔎 {name} ===")
        found = False
        for i, line in enumerate(lines):
            if keyword in line:
                for j in range(max(0, i-before), min(len(lines), i+after+1)):
                    print(f"{j+1}: {lines[j].rstrip()}")
                found = True
                break
        if not found: print("❌ No encontrado")

    grep("1. EL ASESINO DE LA AMNESIA", "lazySeriesEpisodes = emptyList()", 15, 5)
    grep("2. EL CACHÉ (CABALLO DE TROYA)", "fun getSeriesPoster", 2, 8)
    grep("3. BOTÓN ATRÁS", "BackHandler(", 2, 10)
