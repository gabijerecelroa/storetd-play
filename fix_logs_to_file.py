import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Añadir imports necesarios (si no existen)
if 'import java.io.File' not in content:
    content = re.sub(
        r'(^package\s+.*?\n)',
        r'\1\nimport java.io.File\nimport java.io.FileWriter\nimport java.io.IOException\n',
        content,
        flags=re.MULTILINE
    )

# 2. Reemplazar los Log.d rotos o existentes por escritura a archivo
# Buscamos cualquier línea con PosterDebug y la reemplazamos

content = re.sub(
    r'Log\.d\("PosterDebug", .*?\);?',
    '''try {
            File logFile = new File("/sdcard/Download/poster_debug.txt");
            FileWriter writer = new FileWriter(logFile, true);
            writer.append("PosterDebug: " + System.currentTimeMillis() + " | " + "buildSeriesFolders or UI log here\\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }''',
    content
)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ Logs cambiados a archivo (/sdcard/Download/poster_debug.txt)")
