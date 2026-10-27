# Recompa

Recompa es un launcher para Android de **ports nativos de juegos**: recompilaciones, decompilaciones y source ports que se ejecutan sin emulación. Encuentra un port, instálalo desde el GitHub de su autor, prepara tu copia del juego y juega.

> **Estado:** en desarrollo temprano (MVP). Ahora mismo la app muestra el catálogo y la ficha de cada port; la instalación y el asistente de ROM llegan en los próximos hitos.

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

## Probar la última build

Cada push a `main` publica el APK como release `nightly-N`. La última está siempre en
<https://github.com/kalasyan/recompa/releases/latest/download/recompa.apk>, así que basta con abrir ese enlace en el dispositivo.

Para actualizar sin buscar el enlace, añade `https://github.com/kalasyan/recompa` en [Obtainium](https://github.com/ImranR98/Obtainium): avisará e instalará cada nueva build. Todas van firmadas con la misma clave de depuración, así que se instalan encima de la anterior sin perder datos.

## Añadir un port

Lee [`catalog/README.md`](catalog/README.md). En resumen: copia un manifiesto existente, rellena los datos del port y abre un pull request. La CI valida el archivo contra el esquema.

## Uso de inteligencia artificial

Este proyecto se ha desarrollado con ayuda de herramientas de IA (Claude, de Anthropic) para la investigación, el diseño y parte del código.

## Aviso legal

Recompa no incluye, aloja ni descarga juegos, ROMs, ISOs ni otros archivos protegidos por derechos de autor. Cada port necesita una copia legítima del juego original. Los ports pertenecen a sus autores y se descargan desde sus propios repositorios.

Recompa no está afiliado ni respaldado por Nintendo, Sega, Sony, Microsoft ni ningún editor o desarrollador de los juegos. Todas las marcas pertenecen a sus respectivos dueños.

## Licencia

[GPL-3.0](LICENSE).
