# Recompa

Recompa es un launcher para Android de **ports nativos de juegos**: recompilaciones, decompilaciones y source ports que se ejecutan sin emulación. Encuentra un port, instálalo desde el GitHub de su autor, prepara tu copia del juego y juega.

> **Estado:** beta. La app instala, actualiza y desinstala ports desde un catálogo de más de 100, y prepara tus archivos de juego con un asistente.

## Cómo funciona

- **Catálogo abierto.** Cada port es un archivo YAML en [`catalog/ports/`](catalog/ports). Una GitHub Action consulta los releases de cada autor, calcula el SHA-256 de cada APK y publica un `index.json` en GitHub Pages.
- **Sin re-alojar nada.** Recompa descarga cada APK directamente del release de su autor y comprueba su SHA-256 y su certificado de firma antes de instalarlo.
- **Trae tu propia copia.** Recompa no incluye ni descarga juegos, ROMs ni archivos con copyright. El asistente verifica que tu archivo es la versión que espera cada port y lo prepara para él.

## Estructura del repositorio

| Ruta | Contenido |
|---|---|
| `app/` | App Android (Kotlin + Jetpack Compose) |
| `core/` | Lógica sin dependencias de Android: modelos del catálogo, compatibilidad, ROMs, estado de instalación. Con tests. |
| `catalog/` | Manifiestos de los ports y su esquema JSON |
| `tools/catalog/` | Script que valida los manifiestos y genera `index.json` |
| `.github/workflows/` | CI de la app y publicación del catálogo |

## Desarrollo

Requisitos: JDK 17 y Android SDK (Android Studio o las command-line tools).

```bash
./gradlew :core:test          # tests de la lógica
./gradlew :app:assembleDebug  # APK de depuración
```

Para el catálogo (Python 3.11+):

```bash
pip install -r tools/catalog/requirements.txt
pytest tools/catalog
python tools/catalog/build_index.py --offline   # solo valida los manifiestos
python tools/catalog/build_index.py             # genera catalog/dist/index.json (usa GITHUB_TOKEN si existe)
```

Para usar otro catálogo durante el desarrollo: `./gradlew :app:assembleDebug -Precompa.catalogUrl=https://…/index.json`.

## Instalar

1. En el dispositivo Android (8.0 o superior), abre <https://github.com/kalasyan/recompa/releases/latest/download/recompa.apk> y abre el archivo descargado.
2. Android pedirá permitir instalar apps desde el navegador o el gestor de archivos. Acéptalo para instalar Recompa.
3. Al abrir Recompa por primera vez te explicará el permiso que necesita para instalar ports.
4. Elige un port, pulsa **Instalar** y, si necesita archivos del juego, usa **Archivos que necesitas** en su ficha.

Recompa avisa cuando hay una versión nueva y se actualiza desde la propia app. También puedes seguirla con [Obtainium](https://github.com/ImranR98/Obtainium) añadiendo `https://github.com/kalasyan/recompa`.

### Builds de prueba

Cada push a `main` publica una release `nightly-N` con el último código. Están firmadas con una clave de depuración pública, distinta de la de las versiones estables, así que no se pueden instalar encima de una estable (ni al revés) sin desinstalar antes. Para publicar una versión estable, mira [RELEASING.md](RELEASING.md).

## Añadir un port

Lee [`catalog/README.md`](catalog/README.md). En resumen: copia un manifiesto existente, rellena los datos del port y abre un pull request. La CI valida el archivo contra el esquema.

## Uso de inteligencia artificial

Este proyecto se ha desarrollado con ayuda de herramientas de IA (Claude, de Anthropic) para la investigación, el diseño y parte del código.

## Aviso legal

Recompa no incluye, aloja ni descarga juegos, ROMs, ISOs ni otros archivos protegidos por derechos de autor. Cada port necesita una copia legítima del juego original. Los ports pertenecen a sus autores y se descargan desde sus propios repositorios.

Recompa no está afiliado ni respaldado por Nintendo, Sega, Sony, Microsoft ni ningún editor o desarrollador de los juegos. Todas las marcas pertenecen a sus respectivos dueños.

## Licencia

[GPL-3.0](LICENSE).
