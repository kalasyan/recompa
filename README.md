<div align="center">

  <img src="docs/logo.svg" width="160" alt="Logo de Recompa">

  <h1>Recompa</h1>

  **Los ports nativos de tus juegos favoritos, en un solo launcher para Android.**

  <p>
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+">
    <img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.2">
    <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
    <img src="https://img.shields.io/badge/Estado-beta-F97316?style=flat-square" alt="Estado: beta">
    <a href="LICENSE"><img src="https://img.shields.io/badge/Licencia-GPL--3.0-1F3A5F?style=flat-square" alt="Licencia GPL-3.0"></a>
  </p>

  <p>
    <a href="https://github.com/kalasyan/recompa/releases/latest/download/recompa.apk">
      <img src="https://img.shields.io/badge/Descargar-APK-F97316?style=for-the-badge&logo=android&logoColor=white" alt="Descargar APK">
    </a>
    <a href="https://kalasyan.github.io/recompa/index.json">
      <img src="https://img.shields.io/badge/Cat%C3%A1logo-130%2B%20ports-1F3A5F?style=for-the-badge&logo=github&logoColor=white" alt="Catálogo">
    </a>
  </p>

  <img src="https://img.shields.io/github/v/release/kalasyan/recompa?include_prereleases&style=for-the-badge&label=%C3%9Altima%20versi%C3%B3n&color=1F3A5F" alt="Última versión">
  <img src="https://img.shields.io/github/actions/workflow/status/kalasyan/recompa/android.yml?branch=main&style=for-the-badge&label=CI" alt="CI">

</div>

---

## 📖 Sobre el proyecto

**Recompa** es un launcher para Android de **ports nativos de juegos**: recompilaciones, decompilaciones y source ports que se ejecutan sin emulación.

Encuentra un port, instálalo desde el GitHub de su autor, prepara tu copia del juego con el asistente y juega. Recompa se encarga de que cada APK sea el que dice ser y de avisarte cuando hay versiones nuevas.

Está pensado para jugar con mando: la interfaz funciona entera con cruceta y gatillos, y en pantallas apaisadas como la del AYN Thor muestra la ficha a la izquierda y la cuadrícula de carátulas a la derecha.

## ✨ Funcionalidades

- 🎮 **Catálogo de más de 130 ports:** Zelda, Mario, Sonic, Doom, Diablo y muchos más, de PC, N64, Xbox 360, GameCube, GBA, PSP y otras plataformas.
- 🖼 **Carátulas:** cada port muestra su portada, fondo y logo de SteamGridDB.
- 🗂 **Filtros por plataforma:** Todos, Compatibles e Instalados (L1/R1) y la plataforma original (L2/R2), con el número de ports de cada una.
- 📥 **Instalación en un toque:** descarga el APK directamente del release de su autor, sin re-alojar nada.
- 🔒 **Verificación:** comprueba el SHA-256 y el certificado de firma de cada APK antes de instalarlo.
- 🧩 **Asistente de archivos:** verifica que tu ROM o tus datos son la versión que espera cada port y los deja preparados.
- 🔄 **Actualizaciones:** avisa cuando un port o la propia app tienen versión nueva y se actualiza desde dentro.
- 🎨 **Interfaz oscura de launcher:** fondo con el arte del juego seleccionado y un foco muy visible para jugar con mando.

## 📸 Capturas

> Próximamente. Si tienes Recompa instalado y quieres aportar capturas, abre un issue con ellas.

## 🧭 Cómo funciona

- **Catálogo abierto.** Cada port es un archivo YAML en [`catalog/ports/`](catalog/ports). Una GitHub Action consulta los releases de cada autor, calcula el SHA-256 de cada APK y publica un `index.json` en GitHub Pages.
- **Sin re-alojar nada.** Recompa descarga cada APK directamente del release de su autor y comprueba su SHA-256 y su certificado de firma antes de instalarlo.
- **Trae tu propia copia.** Recompa no incluye ni descarga juegos, ROMs ni archivos con copyright. El asistente verifica que tu archivo es la versión que espera cada port y lo prepara para él.

## 📥 Instalar

1. En el dispositivo Android (8.0 o superior), abre <https://github.com/kalasyan/recompa/releases/latest/download/recompa.apk> y abre el archivo descargado.
2. Android pedirá permitir instalar apps desde el navegador o el gestor de archivos. Acéptalo para instalar Recompa.
3. Al abrir Recompa por primera vez te explicará el permiso que necesita para instalar ports.
4. Elige un port, pulsa **Instalar** y, si necesita archivos del juego, usa **Archivos que necesitas** en su ficha.

Recompa avisa cuando hay una versión nueva y se actualiza desde la propia app. También puedes seguirla con [Obtainium](https://github.com/ImranR98/Obtainium) añadiendo `https://github.com/kalasyan/recompa`.

<div align="center">

  <a href="https://github.com/kalasyan/recompa/releases/latest/download/recompa.apk">
    <img src="https://img.shields.io/badge/DESCARGAR%20RECOMPA-F97316?style=for-the-badge&logo=android&logoColor=white" alt="Descargar Recompa">
  </a>

</div>

### 🧪 Builds de prueba

Cada push a `main` publica una release `nightly-N` con el último código. Están firmadas con una clave de depuración pública, distinta de la de las versiones estables, así que no se pueden instalar encima de una estable (ni al revés) sin desinstalar antes. Para publicar una versión estable, mira [RELEASING.md](RELEASING.md).

## ➕ Añadir un port

Lee [`catalog/README.md`](catalog/README.md). En resumen: copia un manifiesto existente, rellena los datos del port y abre un pull request. La CI valida el archivo contra el esquema.

## 🛠 Tecnologías

<div align="center">

  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/OkHttp-3E4348?style=flat-square&logo=square&logoColor=white" alt="OkHttp">
  <img src="https://img.shields.io/badge/Coil-1F3A5F?style=flat-square" alt="Coil">
  <img src="https://img.shields.io/badge/Python-3776AB?style=flat-square&logo=python&logoColor=white" alt="Python">
  <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white" alt="GitHub Actions">
  <img src="https://img.shields.io/badge/SteamGridDB-1B2838?style=flat-square&logo=steam&logoColor=white" alt="SteamGridDB">

</div>

## 📋 Información del proyecto

| Dato | Detalle |
|---|---|
| **Nombre** | Recompa |
| **Plataforma** | Android |
| **Lenguaje** | Kotlin |
| **Interfaz** | Jetpack Compose (Material 3) |
| **Min SDK** | Android 8.0 (API 26) |
| **Target SDK** | Android 15 (API 35) |
| **Package** | `io.github.kalasyan.recompa` |
| **Catálogo** | <https://kalasyan.github.io/recompa/index.json> |
| **Estado** | Beta |

## 🗂 Estructura del repositorio

| Ruta | Contenido |
|---|---|
| `app/` | App Android (Kotlin + Jetpack Compose) |
| `core/` | Lógica sin dependencias de Android: modelos del catálogo, compatibilidad, ROMs, estado de instalación. Con tests. |
| `catalog/` | Manifiestos de los ports y su esquema JSON |
| `tools/catalog/` | Script que valida los manifiestos y genera `index.json` |
| `.github/workflows/` | CI de la app y publicación del catálogo |

## 👩‍💻 Desarrollo

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

## 🤖 Uso de inteligencia artificial

Este proyecto se ha desarrollado con ayuda de herramientas de IA (Claude, de Anthropic) para la investigación, el diseño y parte del código.

## ⚖️ Aviso legal

Recompa no incluye, aloja ni descarga juegos, ROMs, ISOs ni otros archivos protegidos por derechos de autor. Cada port necesita una copia legítima del juego original. Los ports pertenecen a sus autores y se descargan desde sus propios repositorios.

Recompa no está afiliado ni respaldado por Nintendo, Sega, Sony, Microsoft ni ningún editor o desarrollador de los juegos. Todas las marcas pertenecen a sus respectivos dueños.

## 📄 Licencia

[GPL-3.0](LICENSE).

<div align="center">

  ---

  <img src="docs/logo.svg" width="64" alt="Logo de Recompa">

  **Recompa**

  *Los ports nativos de tus juegos favoritos, en un solo launcher.*

  Hecho para jugar en Android con mando. 🎮

</div>
