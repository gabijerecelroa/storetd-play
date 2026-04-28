# Firma del APK

## Importante

No subas archivos `.keystore` ni `.jks` al repositorio.

## Crear keystore local

```bash
keytool -genkeypair \
  -v \
  -keystore release.keystore \
  -alias storetd-play \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Copia el archivo a:

```text
android/app/release.keystore
```

## Variables locales

```bash
export KEYSTORE_PASSWORD="tu-password"
export KEY_ALIAS="storetd-play"
export KEY_PASSWORD="tu-key-password"
```

## Convertir keystore a Base64 para GitHub Secrets

Linux/macOS:

```bash
base64 -w 0 release.keystore > keystore-base64.txt
```

macOS alternativo:

```bash
base64 release.keystore | tr -d '\n' > keystore-base64.txt
```

Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ascii keystore-base64.txt
```
