# Publicar una versión de Recompa

Las versiones estables se firman con la clave privada de Recompa, que solo vive en los secretos del repositorio. Las builds `nightly-N` de cada push a `main` usan la clave de debug pública y se publican en el repo **privado** `kalasyan/recompa-nightlies`, así que el público solo puede descargar las versiones estables.

## Nightlies

- **Publicarlas:** se compilan y publican en el propio repo privado `kalasyan/recompa-nightlies` (workflow *Nightly*), así que en este repo no aparece ningún run. Cada push a `main` llega por un webhook de GitHub al Worker de feedback (`feedback.recompa.app/github/push`), que arranca ese workflow; la configuración está en `feedback/README.md` de recompa-catalog. También se puede lanzar a mano desde *Actions → Nightly → Run workflow* de recompa-nightlies. Se guardan las 5 últimas.
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

Las notas van también dentro del APK: la primera vez que la app se abre tras actualizarse muestra la ventana **Novedades** con ellas, en el idioma de la app (en una instalación nueva no sale). Para eso:

- Añade las traducciones junto al inglés: `X.Y.Z.es.md`, `X.Y.Z.fr.md`, `X.Y.Z.pt.md` y `X.Y.Z.de.md`. Si falta alguna, ese idioma ve el texto en inglés. La release de GitHub solo usa `X.Y.Z.md`.
- Lo que va después de un comentario `<!-- app-end -->` (por ejemplo, la sección *Installing*) solo sale en GitHub, no en la app. En las traducciones no hace falta.
- Formato admitido en la app: párrafos, títulos `##`, listas con `-`, **negrita**, `código` y [enlaces](https://recompa.app).
- Las notas tienen que estar en `main` antes de publicar, porque se meten en el APK al compilar. Las nightlies muestran las notas de la versión más alta que haya en la carpeta, así se pueden revisar antes de la release.

El workflow `Release` compila, pasa los tests y lint, firma el APK, comprueba la firma y crea la release `v0.2.0` como la última, con `recompa.apk` adjunto.

La versión instalada con una build nightly está firmada con otra clave, así que hay que desinstalarla una vez antes de instalar la primera versión estable.
