# Catálogo de Recompa

Cada archivo de [`ports/`](ports) describe un port. El esquema completo está en [`schema/port.schema.json`](schema/port.schema.json).

## Campos principales

| Campo | Qué es |
|---|---|
| `id` | Identificador en minúsculas; debe coincidir con el nombre del archivo |
| `name`, `game` | Nombre del port y del juego original |
| `original_platform`, `type` | Plataforma original y tipo (`recomp`, `decomp` o `source-port`) |
| `forks` | Builds de Android, **en orden de preferencia**: el primero con release es el recomendado |
| `forks[].asset_pattern` | Expresión regular para encontrar el APK entre los assets del release |
| `forks[].package`, `forks[].signer_sha256` | Package name y SHA-256 del certificado de firma. Se rellenan al probar el port; la CI rechaza APKs con otra firma |
| `forks[].verified` | `true` cuando alguien lo ha probado en un dispositivo real |
| `requirements` | ABI, versión mínima de Android (`min_sdk`) y si necesita Vulkan |
| `assets` | Archivos que aporta el usuario: hashes SHA-1 aceptados, conversiones permitidas y cómo se entregan al port |
| `art` | Opcional. Carátulas: la CI las busca en SteamGridDB por `game`. Usa `search` si el nombre no coincide, `steamgriddb_id` para fijar el juego, o `cover`, `hero` y `logo` para poner URLs a mano |

## Carátulas

La CI del catálogo rellena `art` con la carátula vertical (600x900), el fondo panorámico y el logo de cada juego desde [SteamGridDB](https://www.steamgriddb.com). Necesita el secreto `STEAMGRIDDB_API_KEY` en el repositorio; sin él, conserva las carátulas del último `index.json` publicado. La app nunca usa la clave: solo descarga las imágenes.

## Reglas

- Nunca enlaces a ROMs, ISOs ni archivos con copyright.
- Enlaza siempre al repositorio del autor del port, no a copias.
- Antes de añadir un fork de terceros, pide permiso a su autor si es posible.

## Comprobar un manifiesto

```bash
pip install -r tools/catalog/requirements.txt
python tools/catalog/build_index.py --offline
```

Para obtener `signer_sha256` de un APK: `apksigner verify --print-certs port.apk` (línea `certificate SHA-256 digest`).
