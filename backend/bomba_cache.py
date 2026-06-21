import re
with open("src/server.js", "r", encoding="utf-8") as f: code = f.read()

bomba = """
// 🔥 BOMBA DE CACHÉ GLOBAL (ACTUALIZACIÓN FORZADA) 🔥
app.use((req, res, next) => {
    if(req.query) {
        req.query.autoRefresh = '1'; // Obliga al servidor a dar la lista nueva
        req.query.force = 'true';
    }
    next();
});
"""
if "BOMBA DE CACHÉ GLOBAL" not in code:
    code = code.replace("const app = express();", "const app = express();\n" + bomba)
    with open("src/server.js", "w", encoding="utf-8") as f: f.write(code)
    print("\n✅ [ÉXITO] Bomba de Caché Inyectada. Todos los clientes recibirán listas nuevas.\n")
else:
    print("\n⚠️ La Bomba ya estaba instalada.\n")
