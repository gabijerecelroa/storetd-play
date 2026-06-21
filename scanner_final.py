import os

def scan(path, word, before, after):
    if not os.path.exists(path): return "❌ Archivo no encontrado"
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()
        for i, l in enumerate(lines):
            if word in l:
                return "".join(lines[max(0, i-before):min(len(lines), i+after)])
    return "❌ No encontrado"

print("=== 🔎 1. EL CARTEL ZOMBIE ===")
print(scan("android/app/src/main/java/com/storetd/play/feature/vod/VodDetailScreen.kt", "Elegí una fuente", 15, 20))

print("\n=== 🔎 2. EL CABALLO DE TROYA (LLAMADA) ===")
print(scan("android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt", "PremiumContentSessionCache.getSeriesPoster(", 4, 6))

print("\n=== 🔎 3. EL CABALLO DE TROYA (FUNCIÓN) ===")
print(scan("android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt", "fun getSeriesPoster", 2, 10))

print("\n=== 🔎 4. LA AMNESIA DE EPISODIOS ===")
print(scan("android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt", "lazySeriesEpisodes =", 10, 20))
