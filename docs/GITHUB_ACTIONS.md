# GitHub Actions

Workflow:

```text
.github/workflows/android-build.yml
```

## Descargar APK

1. Abre el repositorio en GitHub.
2. Entra a `Actions`.
3. Selecciona el workflow `Android Build`.
4. Abre la ejecucion.
5. Descarga el artifact.

## Crear tag de version

```bash
git tag v1.0.0
git push origin v1.0.0
```

Ejemplos:

```text
v1.0.0
v1.1.0
v2.0.0
```

## Secrets

Configura en GitHub:

```text
KEYSTORE_BASE64
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
API_BASE_URL
APP_NAME
BRAND_PRIMARY_COLOR
BRAND_SECONDARY_COLOR
SUPPORT_WHATSAPP
SUPPORT_EMAIL
```
