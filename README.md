<div align="center">

  <img src="docs/logo.svg" width="160" alt="Recompa logo">

  <h1>Recompa</h1>

  **Native ports of your favorite games, in one Android launcher.**

  <p>
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+">
    <img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.2">
    <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
    <img src="https://img.shields.io/badge/Status-beta-F97316?style=flat-square" alt="Status: beta">
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-1F3A5F?style=flat-square" alt="License GPL-3.0"></a>
  </p>

  <p>
    <a href="https://github.com/kalasyan/recompa/releases/latest/download/recompa.apk">
      <img src="https://img.shields.io/badge/Download-APK-F97316?style=for-the-badge&logo=android&logoColor=white" alt="Download APK">
    </a>
    <a href="https://kalasyan.github.io/recompa/index.json">
      <img src="https://img.shields.io/badge/Catalog-130%2B%20ports-1F3A5F?style=for-the-badge&logo=github&logoColor=white" alt="Catalog">
    </a>
  </p>

  <img src="https://img.shields.io/github/v/release/kalasyan/recompa?include_prereleases&style=for-the-badge&label=Latest&color=1F3A5F" alt="Latest release">
  <img src="https://img.shields.io/github/actions/workflow/status/kalasyan/recompa/android.yml?branch=main&style=for-the-badge&label=CI" alt="CI">

</div>

---

## 📖 About

**Recompa** is an Android launcher for **native game ports**: recompilations, decompilations and source ports that run without emulation.

Find a port, install it straight from its author's GitHub, set up your copy of the game with the wizard and play. Recompa makes sure every APK is what it claims to be and tells you when new versions are out.

It is built for controllers: the whole interface works with the D-pad and shoulder buttons, and on landscape screens like the AYN Thor it shows the details on the left and the cover grid on the right.

## ✨ Features

- 🎮 **130+ ports:** Zelda, Mario, Sonic, Doom, Diablo and many more, from PC, N64, Xbox 360, GameCube, GBA, PSP and other platforms.
- 🖼 **Cover art:** every port shows its cover, background and logo from SteamGridDB.
- 🗂 **Platform filters:** All, Compatible and Installed (L1/R1), plus the original platform (L2/R2), with a port count for each.
- 📥 **One-tap install:** downloads the APK directly from its author's release, with nothing re-hosted.
- 🔒 **Verification:** checks each APK's SHA-256 and signing certificate before installing it.
- 🧩 **Game files wizard:** checks that your ROM or data files are the version each port expects and gets them ready.
- 🔄 **Updates:** tells you when a port or Recompa itself has a new version and updates from inside the app.
- 🎨 **Dark launcher UI:** the selected game's art as the backdrop and a highly visible focus ring for controller play.

## 📸 Screenshots

> Coming soon. If you have Recompa installed and want to contribute screenshots, open an issue with them.

## 🧭 How it works

- **Open catalog.** Each port is a YAML file in [`catalog/ports/`](catalog/ports). A GitHub Action checks each author's releases, computes the SHA-256 of every APK and publishes an `index.json` to GitHub Pages.
- **Nothing re-hosted.** Recompa downloads each APK directly from its author's release and checks its SHA-256 and signing certificate before installing it.
- **Bring your own copy.** Recompa does not include or download games, ROMs or any copyrighted files. The wizard checks that your file is the version each port expects and prepares it for that port.

## 📥 Install

1. On your Android device (8.0 or later), open <https://github.com/kalasyan/recompa/releases/latest/download/recompa.apk> and open the downloaded file.
2. Android will ask you to allow installing apps from your browser or file manager. Allow it to install Recompa.
3. The first time you open Recompa, it explains the permission it needs to install ports.
4. Pick a port, tap **Install** and, if it needs game files, use **Files you need** on its page.

Recompa tells you when a new version is out and updates itself from inside the app. You can also track it with [Obtainium](https://github.com/ImranR98/Obtainium) by adding `https://github.com/kalasyan/recompa`.

<div align="center">

  <a href="https://github.com/kalasyan/recompa/releases/latest/download/recompa.apk">
    <img src="https://img.shields.io/badge/DOWNLOAD%20RECOMPA-F97316?style=for-the-badge&logo=android&logoColor=white" alt="Download Recompa">
  </a>

</div>

### 🧪 Test builds

Every push to `main` publishes a `nightly-N` release with the latest code. Nightlies are signed with a public debug key that differs from the stable release key, so you can't install one over a stable version (or the other way around) without uninstalling first. To publish a stable version, see [RELEASING.md](RELEASING.md).

## ➕ Adding a port

Read [`catalog/README.md`](catalog/README.md). In short: copy an existing manifest, fill in the port's details and open a pull request. CI validates the file against the schema.

## 🛠 Built with

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

## 📋 Project info

| | |
|---|---|
| **Name** | Recompa |
| **Platform** | Android |
| **Language** | Kotlin |
| **UI** | Jetpack Compose (Material 3) |
| **Min SDK** | Android 8.0 (API 26) |
| **Target SDK** | Android 15 (API 35) |
| **Package** | `io.github.kalasyan.recompa` |
| **Catalog** | <https://kalasyan.github.io/recompa/index.json> |
| **Status** | Beta |

## 🗂 Repository layout

| Path | Contents |
|---|---|
| `app/` | Android app (Kotlin + Jetpack Compose) |
| `core/` | Android-free logic: catalog models, compatibility, ROMs, install state. With tests. |
| `catalog/` | Port manifests and their JSON schema |
| `tools/catalog/` | Script that validates the manifests and builds `index.json` |
| `.github/workflows/` | App CI and catalog publishing |

## 👩‍💻 Development

Requirements: JDK 17 and the Android SDK (Android Studio or the command-line tools).

```bash
./gradlew :core:test          # logic tests
./gradlew :app:assembleDebug  # debug APK
```

For the catalog (Python 3.11+):

```bash
pip install -r tools/catalog/requirements.txt
pytest tools/catalog
python tools/catalog/build_index.py --offline   # only validates the manifests
python tools/catalog/build_index.py             # builds catalog/dist/index.json (uses GITHUB_TOKEN if set)
```

To use a different catalog during development: `./gradlew :app:assembleDebug -Precompa.catalogUrl=https://…/index.json`.

## 🤖 Use of AI

This project was built with the help of AI tools (Claude, by Anthropic) for research, design and part of the code.

## ⚖️ Legal notice

Recompa does not include, host or download games, ROMs, ISOs or any other copyrighted files. Each port needs a legitimate copy of the original game. Ports belong to their authors and are downloaded from their own repositories.

Recompa is not affiliated with or endorsed by Nintendo, Sega, Sony, Microsoft or any game publisher or developer. All trademarks belong to their respective owners.

## 📄 License

[GPL-3.0](LICENSE).

<div align="center">

  ---

  <img src="docs/logo.svg" width="64" alt="Recompa logo">

  **Recompa**

  *Native ports of your favorite games, in one launcher.*

  Made for playing on Android with a controller. 🎮

</div>
