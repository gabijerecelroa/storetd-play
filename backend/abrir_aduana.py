import os, re

print("\n=======================================================")
print(" 🧨 OPERACIÓN PUERTAS ABIERTAS: ADULTOS VIP 🧨")
print("=======================================================\n")

# 1. METER ADULTOS A LA FUERZA EN LA LISTA VIP
pc_path = "src/playlistContent.js"
with open(pc_path, "r", encoding="utf-8") as f:
    pc = f.read()

# Buscamos la lista de allowedKeywords y le inyectamos las palabras hot
if '"adultos"' not in pc:
    pc = pc.replace('"infantiles premium"', '"infantiles premium", "adultos", "hot", "xxx", "18+"')
    with open(pc_path, "w", encoding="utf-8") as f:
        f.write(pc)
    print("✅ [1/2] Aduana hackeada: 'adultos' agregado a la Lista VIP.")
else:
    print("⚠️ [1/2] La palabra 'adultos' ya estaba en la Lista VIP.")

# 2. FORZAR AL SERVIDOR A ENVIARLOS SIEMPRE (La app los ocultará)
sv_path = "src/server.js"
with open(sv_path, "r", encoding="utf-8") as f:
    sv = f.read()

# Reemplazamos la lógica del req.query por un 'true' permanente
old_rule = "const includeAdult = req.query.includeAdult === 'true' || req.query.includeAdult === '1';"
new_rule = "const includeAdult = true; // SIEMPRE MANDAR ADULTOS (La App se encarga de ocultarlos con PIN)"

if old_rule in sv:
    sv = sv.replace(old_rule, new_rule)
    with open(sv_path, "w", encoding="utf-8") as f:
        f.write(sv)
    print("✅ [2/2] Servidor liberado: Ahora envía los canales y confía en el PIN de la TV.")
else:
    # Por si no encuentra la línea exacta, forzamos la sobreescritura manual
    sv = re.sub(r'const includeAdult =.*?;', new_rule, sv)
    with open(sv_path, "w", encoding="utf-8") as f:
        f.write(sv)
    print("✅ [2/2] Servidor liberado a la fuerza.")

print("\n=======================================================\n")
