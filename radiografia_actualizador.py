import os

print("\n=== 🔎 1. MANIFEST (PERMISOS Y FILEPROVIDER) ===")
os.system("grep -i 'REQUEST_INSTALL_PACKAGES' android/app/src/main/AndroidManifest.xml || true")
os.system("grep -i 'provider' -A 10 -B 2 android/app/src/main/AndroidManifest.xml || true")

print("\n=== 🔎 2. RUTAS PRIVADAS (XML) ===")
os.system("ls android/app/src/main/res/xml/ || true")
os.system("cat android/app/src/main/res/xml/*path*.xml 2>/dev/null || true")

print("\n=== 🔎 3. EL ACTUALIZADOR ACTUAL ===")
os.system("cat android/app/src/main/java/com/storetd/play/core/update/AppUpdateDownloader.kt || true")
