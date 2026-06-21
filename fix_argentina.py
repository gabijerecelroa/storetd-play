import os

file_path = "backend/src/playlistContent.js"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Agregamos la bandera y abreviaturas al arreglo de palabras clave
    if '"arg"' not in content:
        content = content.replace(
            '"premium"', 
            '"premium", "arg", " ar ", "🇦🇷", "nacional", "local"'
        )
        
    # 2. Hacemos que el filtro también revise el texto original con emojis (rawCat)
    if 'String(category).toLowerCase().includes(kw)' not in content:
        content = content.replace(
            'if (cleanCat.includes(kw)) {', 
            'if (cleanCat.includes(kw) || String(category).toLowerCase().includes(kw)) {'
        )

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("\n✅ [ÉXITO] Filtro maestro actualizado. Atraparemos la bandera 🇦🇷 y variaciones de ARG.\n")
else:
    print("\n❌ Error: No se encontró playlistContent.js\n")
