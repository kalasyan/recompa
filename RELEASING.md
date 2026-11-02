# Publicar una versión de Recompa

Las versiones estables se firman con la clave privada de Recompa, que solo vive en los secretos del repositorio. Las builds `nightly-N` de cada push a `main` usan la clave de debug pública y se publican en el repo **privado** `kalasyan/recompa-nightlies`, así que el público solo puede descargar las versiones estables.

## Nightlies

- **Publicarlas:** en este repo, el secreto `NIGHTLY_REPO_TOKEN` guarda un token fine-grained con acceso solo a `kalasyan/recompa-nightlies` y permiso **Contents: Read and write**. Sin ese secreto, la nightly se guarda como borrador (draft) de release en este repo, que solo ven los colaboradores. Se guardan las 5 últimas.
- **Actualizarlas desde la app:** en una nightly, **Ajustes → Actualizaciones de nightlies** pide otro token fine-grained, con acceso solo a `kalasyan/recompa-nightlies` y permiso **Contents: Read-only**. Se guarda solo en el dispositivo (fuera de las copias de seguridad) y con él la app avisa de cada nightly nueva y la instala como cualquier actualización. Sin token, la nightly no busca actualizaciones. Las versiones estables no tienen esta opción y siguen buscando solo releases estables de este repo.

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

Desde GitHub: **Actions → Release → Run workflow**, escribe la versión (por ejemplo `0.2.0`) y ejecútalo sobre `main`. O desde la terminal:

```bash
git tag v0.2.0
git push origin v0.2.0
```

Antes de publicar, escribe las notas de la versión en inglés en `docs/release-notes/X.Y.Z.md` (por ejemplo `docs/release-notes/0.3.0.md`). Si no existe ese archivo, la release usa la lista de commits que genera GitHub.

El workflow `Release` compila, pasa los tests y lint, firma el APK, comprueba la firma y crea la release `v0.2.0` como la última, con `recompa.apk` adjunto.

La versión instalada con una build nightly está firmada con otra clave, así que hay que desinstalarla una vez antes de instalar la primera versión estable.
