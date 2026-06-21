import os

file_path = "backend/src/playlistContent.js"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    if "LA ADUANA VIP" in "".join(lines):
        print("\n⚠️ La Aduana VIP ya estaba instalada.\n")
    else:
        inside_live = False
        inserted = False
        
        for i, line in enumerate(lines):
            if "function normalizeXtreamLiveItems" in line:
                inside_live = True
                
            if inside_live and "const category =" in line and "xtreamCategoryName" in line:
                indent = line[:len(line) - len(line.lstrip())]
                injection = f"""{indent}// 🔥 LA ADUANA VIP: FILTRO ESTRICTO DE CATEGORIAS
{indent}const allowedKeywords = [
{indent}    "paraguay", "gran hermano", "argentina", "copa libertadores",
{indent}    "eventos premium", "espn", "fox", "movistar", "24/7",
{indent}    "cinema", "cine premium", "infantil", "musica",
{indent}    "música", "latinos", "ufc", "zona latina", "mundial", "d$port",
{indent}    "dsport", "deportes"
{indent}];
{indent}const categoryNormalized = String(category).toLowerCase();
{indent}let isAllowed = false;
{indent}for (const kw of allowedKeywords) {{
{indent}    if (categoryNormalized.includes(kw)) {{
{indent}        isAllowed = true;
{indent}        break;
{indent}    }}
{indent}}}
{indent}if (!isAllowed) return null; // Destruye el canal en la aduana\n"""
                lines.insert(i + 1, injection)
                inserted = True
                break
                
        if inserted:
            with open(file_path, "w", encoding="utf-8") as f:
                f.writelines(lines)
            print("\n✅ [EXITO] ¡La Aduana VIP fue instalada! Cortafuegos activado.\n")
        else:
            print("\n❌ Error: No se encontró el punto de inyección.\n")
else:
    print("\n❌ Error: Archivo no encontrado.\n")
