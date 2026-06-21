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
        count = 0
        for i, line in enumerate(lines):
            if keyword in line:
                start = max(0, i-before)
                end = min(len(lines), i+after+1)
                for j in range(start, end):
                    print(f"{j+1}: {lines[j].rstrip()}")
                print("-" * 40)
                found = True
                count += 1
                if count >= 2: break
        if not found: print("❌ No encontrado")

    grep("1. ¿EXISTE EL MODO DIOS O SE BORRÓ?", "object GlobalPlayMemory", 2, 8)
    grep("2. EL ASESINO DE MEMORIA (Sincronización)", "selectedSeriesKey = null", 5, 5)
    grep("3. COMO ESTA LA MEMORIA AHORA", "var selectedSeriesKey", 1, 1)
    grep("4. DIBUJO DE LA SERIE", "fun SeriesFolderItem", 2, 5)
