# Publicar una versión de Recompa

Las versiones estables se firman con la clave privada de Recompa, que solo vive en los secretos del repositorio. Las builds `nightly-N` de cada push a `main` siguen usando la clave de debug pública y se publican como prerelease.

## Configuración (una sola vez)

En **Settings → Secrets and variables → Actions** del repositorio, crea estos cuatro secretos:

| Secreto | Valor |
|---|---|
| `RECOMPA_KEYSTORE_BASE64` | El keystore `.jks` codificado en base64 (`base64 -w0 recompa-release.jks`) |
| `RECOMPA_KEYSTORE_PASSWORD` | Contraseña del keystore |
| `RECOMPA_KEY_ALIAS` | `recompa` |
| `RECOMPA_KEY_PASSWORD` | Contraseña de la clave (la misma que la del keystore) |

**Guarda una copia del keystore y su contraseña fuera de GitHub** (por ejemplo, en un gestor de contraseñas). Si se pierde, ninguna versión futura podrá instalarse encima de las anteriores: Android exige la misma firma para actualizar.

## Publicar

```bash
git tag v0.2.0
git push origin v0.2.0
```

El workflow `Release` compila, pasa los tests y lint, firma el APK, comprueba la firma y crea la release `v0.2.0` como la última, con `recompa.apk` adjunto.

La versión instalada con una build nightly está firmada con otra clave, así que hay que desinstalarla una vez antes de instalar la primera versión estable.
