import re

file_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Log en buildSeriesFolders (después de calcular posterUrl)
log_in_mapping = r'(val posterUrl = PremiumContentSessionCache\.getSeriesFolders\(folderKey\).*?first\.logoUrl)'

log_mapping_replacement = r'''\1

            Log.d("PosterDebug", "buildSeriesFolders → folderKey='\( folderKey', posterFromCache=' \){PremiumContentSessionCache.getSeriesFolders(folderKey)?.firstOrNull()?.posterUrl}', finalPosterUrl='$posterUrl'")'''

if re.search(log_in_mapping, content, re.DOTALL):
    content = re.sub(log_in_mapping, log_mapping_replacement, content, flags=re.DOTALL)
    print("✅ Log añadido en buildSeriesFolders")
else:
    print("⚠️  No encontré el bloque del mapping para el log")

# 2. Log en la UI (al crear NetflixSeriesPosterCard)
log_in_ui = r'(NetflixSeriesPosterCard\(\s*title = folder\.title,\s*logoUrl = folder\.posterUrl \?: folder\.logoUrl,)'

log_ui_replacement = r'''Log.d("PosterDebug", "UI NetflixSeriesPosterCard → title='\( {folder.title}', posterUrl=' \){folder.posterUrl}', logoUrl='\( {folder.logoUrl}', usando=' \){folder.posterUrl ?: folder.logoUrl}'")
                    \1'''

if re.search(log_in_ui, content):
    content = re.sub(log_in_ui, log_ui_replacement, content)
    print("✅ Log añadido en la UI (NetflixSeriesPosterCard)")
else:
    print("⚠️  No encontré la llamada a NetflixSeriesPosterCard para el log")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("🎉 Logs de diagnóstico añadidos (tag: PosterDebug)")
