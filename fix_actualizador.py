import os, re

file_path = "android/app/src/main/java/com/storetd/play/core/update/AppUpdateDownloader.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Buscamos la variable de la url (suele llamarse 'url' o parecida)
match = re.search(r'fun\s+download\s*\(\s*([a-zA-Z0-9_]+)\s*:\s*String', content)
url_param = match.group(1) if match else "url"

new_fallback = f"""            try {{
                // 🔥 PUENTE DE EMERGENCIA: Abre el navegador si la TV Box está mutilada
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse({url_param}))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                android.widget.Toast.makeText(context, "Gestor bloqueado. Abriendo navegador web...", android.widget.Toast.LENGTH_LONG).show()
                true
            }} catch (e2: Exception) {{
                android.widget.Toast.makeText(context, "Descarga manual requerida. Usa la app 'Downloader'.", android.widget.Toast.LENGTH_LONG).show()
                false
            }}"""

# Reemplazamos el toast rojo inútil por el puente
content = re.sub(r'Toast\.makeText\([\s\S]*?No se pudo iniciar la descarga[\s\S]*?\.show\(\)\s*false', new_fallback, content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n✅ [ÉXITO] PUENTE DE EMERGENCIA INYECTADO: Tu app ahora sobrevivirá a las Dinax.\n")
