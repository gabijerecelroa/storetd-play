import os

file_path = "backend/src/playlistContent.js"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Inyectamos el chivato antes de la Aduana V4
    if "console.log('--- RADIOGRAFIA DE CATEGORIAS ---');" not in content:
        content = content.replace(
            "const cleanCat = String(category)",
            "// CHIVATO DE CATEGORIAS\n      if (!global.radiografia) global.radiografia = new Set();\n      if (!global.radiografia.has(category)) {\n        console.log('CATEGORIA ENCONTRADA: ' + category);\n        global.radiografia.add(category);\n      }\n      const cleanCat = String(category)"
        )

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("\n✅ Chivato inyectado. El servidor cantará las categorías.")
