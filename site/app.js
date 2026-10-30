"use strict";

const REPO = "kalasyan/recompa";

// The page is written in Spanish; these are the English strings, keyed like the data-i18n attributes.
const EN = {
  "nav.catalog": "Catalog",
  "nav.install": "Install",
  "nav.faq": "FAQ",
  "hero.eyebrow": "Android launcher · Free and open source",
  "hero.title": "Native ports of your favorite games, in one launcher.",
  "hero.lead": "Recompilations, decompilations and source ports that run without emulation. Pick a game, install its port straight from the author's GitHub, set up your copy of the game and play.",
  "hero.download": "Download APK",
  "hero.meta": "Android 8.0 or later",
  "shot.library": "Recompa's library with cover art and platform filters",
  "shot.platform": "Nintendo 64 ports",
  "shot.port": "A port's page",
  "stats.ports": "ports",
  "stats.platforms": "original platforms",
  "stats.verified": "and signature checked on every install",
  "features.title": "Everything you need to play ports on Android",
  "f1.t": "Curated catalog",
  "f1.d": "Ports from PC, Nintendo 64, Xbox 360, GameCube, GBA, PSP and more, with the recommended fork of each one.",
  "f2.t": "Verified installs",
  "f2.d": "Every APK is downloaded from its author's repository, and its SHA-256 and signature are checked before installing.",
  "f3.t": "Game files wizard",
  "f3.d": "Checks your ROM or game data and puts it where the port looks for it.",
  "f4.t": "Updates",
  "f4.d": "Update every port at once, get notified about new versions, or go back to an earlier one if something breaks. Recompa updates itself too.",
  "f5.t": "Made for controllers",
  "f5.d": "A dark UI built for the D-pad and shoulder buttons, with search and favorites one button away. Great on handhelds like the AYN Thor.",
  "f6.t": "Frontend mode",
  "f6.d": "Full screen, and Recompa can be your handheld's home screen. With compatibility reports from other players.",
  "f7.t": "Background downloads",
  "f7.d": "Leave the app while ports download, verify and install. And if you already installed a port on your own, you can link it.",
  "f8.t": "No GitHub account needed",
  "f8.d": "Rate how each port runs, or suggest one that's missing from the catalog, straight from the app.",
  "f9.t": "In your language",
  "f9.d": "Recompa comes in English, Spanish, French, Portuguese and German.",
  "howto.title": "Where to find things",
  "howto.toolbar": "Suggest a port with the + button in the library. The gear next to it opens Settings.",
  "howto.toolbar.alt": "The suggest a port and settings buttons in the library",
  "howto.settings": "Change the language at the top of Settings.",
  "howto.settings.alt": "The language picker in Settings",
  "howto.link": "Already had the port installed? Link it from its page, under the Install button.",
  "howto.link.alt": "The I already have it installed button on a port's page",
  "howto.report": "At the bottom of each port's page you can tell how it runs for you, no GitHub account needed.",
  "howto.report.alt": "The button to report how a port runs",
  "catalog.title": "The catalog",
  "catalog.sub": "It updates itself several times a day with each port's new versions.",
  "catalog.search": "Search for a game",
  "catalog.loading": "Loading the catalog…",
  "catalog.error": "The catalog could not be loaded right now.",
  "catalog.none": "No ports match your search.",
  "catalog.all": "All",
  "catalog.more": "Show all {n} ports",
  "install.title": "How to install it",
  "install.sub": "Recompa is not on Google Play: ports are installed from GitHub, which the store doesn't allow. It installs like any other APK.",
  "s1.t": "Download the APK",
  "s1.d": "Open this page on your Android device and tap “Download APK”.",
  "s2.t": "Allow the install",
  "s2.d": "Open the downloaded file. Android will ask you to allow installing apps from your browser or file manager: turn it on for Recompa. If Google Play Protect warns you, tap “More details” and then “Install anyway”.",
  "s3.t": "Let it install ports",
  "s3.d": "The first time you open Recompa, it explains the permission it needs to install ports.",
  "s4.t": "Pick a game",
  "s4.d": "Tap “Install” on a port and, if it needs game files, use “Files you need” on its page.",
  "upd.t": "It updates itself",
  "upd.d": "Recompa tells you when a new version is out, in the app and with a notification, and installs it over the one you have without losing your ports or settings.",
  "faq.title": "FAQ",
  "q1.t": "Does Recompa include games or ROMs?",
  "q1.d": "No. Recompa does not include, host or download games, ROMs, ISOs or any other copyrighted files. Each port needs a legitimate copy of the original game.",
  "q2.t": "Is it safe to install an APK from outside the store?",
  "q2.d": "Recompa is always signed with the same key and its code is public on GitHub. It also checks the SHA-256 and signature of every port before installing it. If something doesn't match, it doesn't install it.",
  "qpp.t": "Why does Play Protect say it's a harmful app?",
  "qpp.d": "Google Play Protect distrusts new apps that aren't on Google Play and can install other apps, as any store does. It hasn't found anything specific: you can check by uploading the APK to <a href=\"https://www.virustotal.com/\">VirusTotal</a>. To install it, tap “More details” and then “Install anyway”. The warning will fade as more people install it.",
  "q3.t": "Which devices does it run on?",
  "q3.d": "Android 8.0 or later. It's designed for handhelds with controllers like the AYN Thor, but it works on phones and tablets too. Each port has its own requirements, and Recompa tells you which ones work on your device.",
  "q4.t": "Are there test builds?",
  "q4.d": "Yes: every change publishes a <a href=\"https://github.com/kalasyan/recompa/releases\">nightly build</a>. They use a different signing key, so switching between a nightly and the stable version (either way) requires uninstalling first.",
  "q5.t": "How do I report a problem?",
  "q5.d": "Open an <a href=\"https://github.com/kalasyan/recompa/issues\">issue on GitHub</a>. Problems with a specific port belong to its author; from Recompa you can send a compatibility report or suggest a port, no GitHub account needed.",
  "foot.legal": "Recompa does not include, host or download games, ROMs, ISOs or other copyrighted files. Ports belong to their authors and are downloaded from their own repositories.",
  "foot.releases": "All versions",
};

const ES_EXTRA = {
  "catalog.error": "Ahora mismo no se ha podido cargar el catálogo.",
  "catalog.none": "Ningún port coincide con tu búsqueda.",
  "catalog.all": "Todas",
  "catalog.more": "Ver los {n} ports",
};

const PLATFORMS = {
  n64: "Nintendo 64", gamecube: "GameCube", wii: "Wii", wiiu: "Wii U", ds: "Nintendo DS",
  switch: "Switch", snes: "Super Nintendo", genesis: "Mega Drive", nes: "NES", gb: "Game Boy",
  gbc: "Game Boy Color", gba: "Game Boy Advance", "32x": "32X", amiga: "Amiga", ps1: "PlayStation",
  ps2: "PlayStation 2", ps3: "PlayStation 3", psp: "PSP", xbox: "Xbox", xbox360: "Xbox 360", pc: "PC",
};

const ES = {};
let lang = "es";
let release = null;
let ports = [];
let platform = "";
let showAll = false;

function store(key, value) {
  try { localStorage.setItem(key, value); } catch (e) { /* private mode */ }
}
function stored(key) {
  try { return localStorage.getItem(key); } catch (e) { return null; }
}

function t(key) {
  // Falls back to the Spanish text in the HTML, so a missing translation never shows the key.
  return (lang === "en" ? EN[key] : undefined) ?? ES[key] ?? ES_EXTRA[key] ?? key;
}

function applyLang() {
  document.documentElement.lang = lang;
  document.getElementById("lang").textContent = lang === "en" ? "ES" : "EN";
  document.querySelectorAll("[data-i18n]").forEach((el) => { el.textContent = t(el.dataset.i18n); });
  document.querySelectorAll("[data-i18n-html]").forEach((el) => { el.innerHTML = t(el.dataset.i18nHtml); });
  document.querySelectorAll("[data-i18n-alt]").forEach((el) => { el.alt = t(el.dataset.i18nAlt); });
  document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => { el.placeholder = t(el.dataset.i18nPlaceholder); });
  renderVersion();
  if (ports.length) renderChips();
  renderCatalog();
}

function setupLang() {
  // Keep the Spanish text that is in the HTML so the page can switch back to it.
  document.querySelectorAll("[data-i18n]").forEach((el) => { ES[el.dataset.i18n] = el.textContent; });
  document.querySelectorAll("[data-i18n-html]").forEach((el) => { ES[el.dataset.i18nHtml] = el.innerHTML; });
  document.querySelectorAll("[data-i18n-alt]").forEach((el) => { ES[el.dataset.i18nAlt] = el.alt; });
  document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => { ES[el.dataset.i18nPlaceholder] = el.placeholder; });
  const browser = (navigator.language || "es").toLowerCase().startsWith("es") ? "es" : "en";
  lang = stored("lang") || browser;
  document.getElementById("lang").addEventListener("click", () => {
    lang = lang === "en" ? "es" : "en";
    store("lang", lang);
    applyLang();
  });
  if (lang !== "es") applyLang();
}

function renderVersion() {
  const el = document.getElementById("version");
  if (!release) { el.textContent = t("hero.meta"); return; }
  const parts = [release.tag_name];
  const apk = (release.assets || []).find((a) => a.name === "recompa.apk");
  if (apk) parts.push(`${(apk.size / 1048576).toFixed(1)} MB`);
  if (release.published_at) {
    parts.push(new Date(release.published_at).toLocaleDateString(lang, { day: "numeric", month: "short", year: "numeric" }));
  }
  parts.push(t("hero.meta"));
  el.textContent = parts.join(" · ");
}

async function loadRelease() {
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`);
    if (!res.ok) return;
    release = await res.json();
    renderVersion();
  } catch (e) { /* the button works without it */ }
}

// Downloads through dl.recompa.app are counted (anonymously, by the feedback Worker).
// The button keeps its direct GitHub link until the Worker answers.
async function useCountedDownload() {
  try {
    const res = await fetch("https://dl.recompa.app/", { signal: AbortSignal.timeout(4000) });
    if (res.ok && (await res.json()).ok) {
      document.getElementById("download").href = "https://dl.recompa.app/download?from=web";
    }
  } catch (e) { /* not deployed yet, or blocked: GitHub's link works */ }
}

function platformName(code) {
  return PLATFORMS[code] || code;
}

function renderChips() {
  const counts = {};
  ports.forEach((p) => { counts[p.original_platform] = (counts[p.original_platform] || 0) + 1; });
  const codes = Object.keys(counts).sort((a, b) => counts[b] - counts[a]);
  const chips = document.getElementById("chips");
  chips.replaceChildren();
  const add = (code, label) => {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "chip";
    b.textContent = label;
    b.setAttribute("aria-pressed", String(platform === code));
    b.addEventListener("click", () => { platform = code; renderChips(); renderCatalog(); });
    chips.append(b);
  };
  add("", `${t("catalog.all")} · ${ports.length}`);
  codes.forEach((c) => add(c, `${platformName(c)} · ${counts[c]}`));
}

function portCard(p) {
  const fork = p.forks.find((f) => f.release) || p.forks[0];
  const a = document.createElement("a");
  a.className = "port";
  a.href = `https://github.com/${fork ? fork.repo : ""}`;
  a.target = "_blank";
  a.rel = "noopener";
  a.title = p.description || p.name;
  const cover = document.createElement("div");
  cover.className = "cover";
  const art = p.art && p.art.cover;
  if (art) {
    const img = document.createElement("img");
    img.src = art;
    img.alt = "";
    img.loading = "lazy";
    img.referrerPolicy = "no-referrer";
    img.addEventListener("error", () => { const s = document.createElement("span"); s.textContent = p.game; img.replaceWith(s); });
    cover.append(img);
  } else {
    const s = document.createElement("span");
    s.textContent = p.game;
    cover.append(s);
  }
  const name = document.createElement("div");
  name.className = "name";
  name.textContent = p.name;
  const plat = document.createElement("div");
  plat.className = "plat";
  plat.textContent = platformName(p.original_platform);
  a.append(cover, name, plat);
  return a;
}

function renderCatalog() {
  const box = document.getElementById("ports");
  if (!ports.length) return;
  const q = document.getElementById("search").value.trim().toLowerCase();
  const list = ports.filter((p) =>
    (!platform || p.original_platform === platform) &&
    (!q || `${p.name} ${p.game}`.toLowerCase().includes(q)));
  const limit = showAll || q || platform ? list.length : 42;
  box.replaceChildren(...list.slice(0, limit).map(portCard));
  if (!list.length) {
    const p = document.createElement("p");
    p.className = "empty";
    p.textContent = t("catalog.none");
    box.append(p);
  }
  document.querySelector(".catalog .more")?.remove();
  if (list.length > limit) {
    const more = document.createElement("div");
    more.className = "more";
    const b = document.createElement("button");
    b.type = "button";
    b.className = "btn";
    b.textContent = t("catalog.more").replace("{n}", list.length);
    b.addEventListener("click", () => { showAll = true; renderCatalog(); });
    more.append(b);
    box.after(more);
  }
}

async function loadCatalog() {
  try {
    // Served from the same site as this page.
    const res = await fetch("index.json", { cache: "no-cache" });
    if (!res.ok) throw new Error(res.status);
    const index = await res.json();
    ports = index.ports.filter((p) => p.forks.some((f) => f.release))
      .sort((a, b) => a.name.localeCompare(b.name));
    document.getElementById("stat-ports").textContent = ports.length;
    document.getElementById("stat-platforms").textContent = new Set(ports.map((p) => p.original_platform)).size;
    renderChips();
    renderCatalog();
  } catch (e) {
    document.getElementById("ports").replaceChildren(Object.assign(document.createElement("p"), {
      className: "empty", textContent: t("catalog.error"),
    }));
  }
}

setupLang();
document.getElementById("search").addEventListener("input", renderCatalog);
loadRelease();
useCountedDownload();
loadCatalog();
