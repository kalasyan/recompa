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
| `forks[].package`, `forks[].signer_sha256` | Opcionales. La CI los lee del APK la primera vez que ve el fork y los fija en `index.json`; después rechaza cualquier release con otro package u otra firma. Escríbelos solo para fijarlos tú o para aceptar un cambio de clave legítimo |
| `forks[].blocked` | Opcional. Releases que Recompa no debe ofrecer, con `tag` y `reason` (por ejemplo, porque rompe las partidas). La app avisa si alguien la tiene instalada |
| `forks[].verified`, `forks[].tested_on` | Opcionales. Marca que alguien lo ha probado en un dispositivo real, y en cuál |
| `requirements` | ABI, versión mínima de Android (`min_sdk`) y si necesita Vulkan |
| `assets` | Archivos que aporta el usuario: hashes SHA-1 aceptados, conversiones permitidas y cómo se entregan al port |
| `art` | Opcional. Carátulas: la CI las busca en SteamGridDB por `game`. Usa `search` si el nombre no coincide, `steamgriddb_id` para fijar el juego, o `cover`, `hero` y `logo` para poner URLs a mano |

## Carátulas

La CI del catálogo rellena `art` con la carátula vertical (600x900), el fondo panorámico y el logo de cada juego desde [SteamGridDB](https://www.steamgriddb.com). Necesita el secreto `STEAMGRIDDB_API_KEY` en el repositorio; sin él, conserva las carátulas del último `index.json` publicado. La app nunca usa la clave: solo descarga las imágenes.

## Versiones anteriores

Además de la última release, `index.json` guarda hasta 5 anteriores de cada fork (`history`), para que la app permita volver a una si la nueva falla. Solo entran releases cuyo hash ya ha calculado GitHub o que Recompa publicó antes. La app comprueba package y firma antes de instalarlas, igual que con la última.

Para retirar una release, añádela a `blocked` en el manifiesto:

```yaml
forks:
  - repo: alguien/port-android
    asset_pattern: '(?i)\.apk$'
    blocked:
      - tag: v1.4
        reason: "Corrompe las partidas guardadas"
```

## Informes de compatibilidad

Desde la ficha de un port, la app abre el formulario [Informe de compatibilidad](../.github/ISSUE_TEMPLATE/compat.yml) con el port, el dispositivo y la versión ya rellenados. Cada vez que se genera el catálogo, la CI cuenta los informes por port y dispositivo y los publica en `reports`. Para descartar un informe falso, ciérralo como *not planned* o ponle la etiqueta `invalid`.

## Reglas

- Nunca enlaces a ROMs, ISOs ni archivos con copyright.
- Enlaza siempre al repositorio del autor del port, no a copias.
- Antes de añadir un fork de terceros, pide permiso a su autor si es posible.

## Comprobar un manifiesto

```bash
pip install -r tools/catalog/requirements.txt
python tools/catalog/build_index.py --offline
```

## Añadir un port

Basta con `id`, `name`, `game`, `original_platform`, `type`, `requirements` y un fork con `repo` y `asset_pattern`. No hace falta instalarlo ni sacar hashes a mano: la CI descarga el APK, calcula su SHA-256 y fija package y firma.

Si el APK viene dentro de un ZIP, apunta `asset_pattern` al ZIP y añade `zip_entry` con una expresión regular para el APK de dentro.

Un port roto nunca bloquea el catálogo. Si su nueva release no pasa las comprobaciones (firma o package distintos, un asset cuyo hash cambia con el mismo tag, un APK ilegible), la CI deja un aviso y ese port conserva la última release buena. Las releases ya publicadas no se vuelven a descargar.

Si un port ha cambiado de firma (lo verás como aviso en la CI), comprueba con su autor que el cambio es legítimo y escribe la nueva `signer_sha256` en el manifiesto (la saca `apksigner verify --print-certs port.apk`, línea `certificate SHA-256 digest`).
