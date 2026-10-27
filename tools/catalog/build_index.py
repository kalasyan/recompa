#!/usr/bin/env python3
"""Build Recompa's catalog index from the port manifests.

Reads every ``catalog/ports/*.yaml``, validates it against the JSON schema,
looks up the latest GitHub release of each fork, and writes ``index.json``
with the APK asset, its size and its SHA-256 for every fork. With a
``STEAMGRIDDB_API_KEY`` it also looks up cover art for every port.

Usage:
    python tools/catalog/build_index.py --offline          # validate only
    python tools/catalog/build_index.py --out catalog/dist/index.json
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Iterable

import jsonschema
import yaml

ROOT = Path(__file__).resolve().parents[2]
PORTS_DIR = ROOT / "catalog" / "ports"
SCHEMA_PATH = ROOT / "catalog" / "schema" / "port.schema.json"
SCHEMA_VERSION = 1
GITHUB_API = "https://api.github.com"
STEAMGRIDDB_API = "https://www.steamgriddb.com/api/v2"

# Art slot -> SteamGridDB endpoint and filters.
ART_QUERIES = {
    "cover": "grids/game/{id}?dimensions=600x900&types=static&nsfw=false&humor=false",
    "hero": "heroes/game/{id}?types=static&nsfw=false&humor=false",
    "logo": "logos/game/{id}?types=static&nsfw=false&humor=false",
}


class CatalogError(Exception):
    """A problem that makes the catalog unpublishable."""


@dataclass
class Manifest:
    path: Path
    data: dict[str, Any]


# --------------------------------------------------------------------------- #
# Loading and validation
# --------------------------------------------------------------------------- #

def load_schema(path: Path = SCHEMA_PATH) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def load_manifests(ports_dir: Path = PORTS_DIR) -> list[Manifest]:
    manifests = []
    for path in sorted(ports_dir.glob("*.yaml")):
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
        manifests.append(Manifest(path, data))
    return manifests


def validate(manifests: Iterable[Manifest], schema: dict[str, Any]) -> list[str]:
    """Return a list of human-readable errors (empty when everything is valid)."""
    validator = jsonschema.Draft202012Validator(schema)
    errors: list[str] = []
    seen_ids: dict[str, Path] = {}
    for m in manifests:
        name = m.path.name
        if not isinstance(m.data, dict):
            errors.append(f"{name}: the file is not a YAML mapping")
            continue
        for err in sorted(validator.iter_errors(m.data), key=lambda e: list(e.path)):
            where = "/".join(str(p) for p in err.path) or "(root)"
            errors.append(f"{name}: {where}: {err.message}")
        port_id = m.data.get("id")
        if port_id and port_id != m.path.stem:
            errors.append(f"{name}: id '{port_id}' must match the file name '{m.path.stem}'")
        if port_id in seen_ids:
            errors.append(f"{name}: id '{port_id}' already used by {seen_ids[port_id].name}")
        elif port_id:
            seen_ids[port_id] = m.path
        for i, fork in enumerate(m.data.get("forks") or []):
            for field in ("asset_pattern", "zip_entry"):
                pattern = fork.get(field)
                if pattern is None:
                    continue
                try:
                    re.compile(pattern)
                except re.error as exc:
                    errors.append(f"{name}: forks/{i}/{field}: invalid regex ({exc})")
    return errors


# --------------------------------------------------------------------------- #
# Releases
# --------------------------------------------------------------------------- #

Fetcher = Callable[[str], Any]


def github_fetcher(token: str | None) -> Fetcher:
    def fetch(url: str) -> Any:
        req = urllib.request.Request(url, headers={
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "recompa-catalog",
            **({"Authorization": f"Bearer {token}"} if token else {}),
        })
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.load(resp)
    return fetch


def steamgriddb_fetcher(api_key: str) -> Fetcher:
    def fetch(url: str) -> Any:
        req = urllib.request.Request(url, headers={
            "Authorization": f"Bearer {api_key}",
            "User-Agent": "recompa-catalog",
        })
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = json.load(resp)
        if not body.get("success"):
            raise CatalogError(f"SteamGridDB error: {body.get('errors')}")
        return body["data"]
    return fetch


def pick_release(
    releases: list[dict[str, Any]], channel: str, pattern: str | None = None,
) -> dict[str, Any] | None:
    """Newest published release; stable channels skip prereleases.

    With ``pattern``, the newest one with an asset matching it, since many
    repositories alternate desktop and Android releases.
    """
    for rel in releases:
        if rel.get("draft"):
            continue
        if channel == "stable" and rel.get("prerelease"):
            continue
        if pattern is not None and select_asset(rel.get("assets", []), pattern) is None:
            continue
        return rel
    return None


def prefer_arm64(names: list[str]) -> str | None:
    """The first arm64 name, else the first universal one, else the first one."""
    arm64 = [n for n in names if re.search(r"arm64|aarch64|v8a", n, re.I)]
    universal = [n for n in names if re.search(r"universal", n, re.I)]
    return next(iter(arm64 or universal or names), None)


def select_asset(assets: list[dict[str, Any]], pattern: str) -> dict[str, Any] | None:
    """Pick the asset matching ``pattern``; prefer arm64 builds when several match."""
    regex = re.compile(pattern)
    by_name = {a.get("name", ""): a for a in assets if regex.search(a.get("name", ""))}
    best = prefer_arm64(list(by_name))
    return by_name[best] if best is not None else None


def extract_zip_entry(archive: Path, pattern: str) -> tuple[str, Path] | None:
    """Extract the file matching ``pattern`` (preferring arm64) next to ``archive``.

    Returns its path inside the ZIP and where it was extracted, or None.
    """
    regex = re.compile(pattern)
    with zipfile.ZipFile(archive) as zf:
        names = [n for n in zf.namelist() if not n.endswith("/") and regex.search(n)]
        best = prefer_arm64(names)
        if best is None:
            return None
        target = archive.with_name(archive.name + ".entry")
        with zf.open(best) as src, target.open("wb") as out:
            shutil.copyfileobj(src, out)
    return best, target


def asset_sha256(asset: dict[str, Any], download: Callable[[str], Path] | None) -> str | None:
    digest = asset.get("digest") or ""
    if digest.startswith("sha256:"):
        return digest.split(":", 1)[1].lower()
    if download is None:
        return None
    return sha256_file(download(asset["browser_download_url"]))


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def make_downloader(workdir: Path) -> Callable[[str], Path]:
    def download(url: str) -> Path:
        target = workdir / hashlib.sha1(url.encode()).hexdigest()
        req = urllib.request.Request(url, headers={"User-Agent": "recompa-catalog"})
        with urllib.request.urlopen(req, timeout=300) as resp, target.open("wb") as out:
            shutil.copyfileobj(resp, out)
        return target
    return download


def apk_package(apk: Path) -> str:
    """Package name of an APK, via Android build-tools' aapt2."""
    return subprocess.run(["aapt2", "dump", "packagename", str(apk)],
                          check=True, capture_output=True, text=True).stdout.strip()


def inspect_apk(apk: Path) -> tuple[str, list[str]]:
    return apk_package(apk), apk_signer_sha256(apk)


def apk_signer_sha256(apk: Path) -> list[str]:
    """SHA-256 of each signer certificate, via Android build-tools' apksigner."""
    out = subprocess.run(["apksigner", "verify", "--print-certs", str(apk)],
                         check=True, capture_output=True, text=True).stdout
    return [m.lower() for m in re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F]{64})", out)]


# --------------------------------------------------------------------------- #
# Art
# --------------------------------------------------------------------------- #

def search_game(fetch: Fetcher, name: str) -> int | None:
    """SteamGridDB id of the best match for ``name``: an exact title, else the first result."""
    results = fetch(f"{STEAMGRIDDB_API}/search/autocomplete/{urllib.parse.quote(name, safe='')}")
    if not results:
        return None
    exact = [r for r in results if r.get("name", "").casefold() == name.casefold()]
    return (exact or results)[0]["id"]


def resolve_art(
    manifest: Manifest,
    fetch: Fetcher | None,
    previous: dict[str, Any] | None,
) -> tuple[dict[str, Any] | None, list[str]]:
    """Cover, hero and logo URLs for a port, and warnings.

    URLs written in the manifest win. The rest come from SteamGridDB when
    ``fetch`` is given, and from the previous index otherwise or on failure,
    so a missing API key or a network error never wipes published art.
    """
    warnings: list[str] = []
    wanted = manifest.data.get("art") or {}
    art: dict[str, Any] = {slot: wanted[slot] for slot in ART_QUERIES if wanted.get(slot)}
    missing = [slot for slot in ART_QUERIES if slot not in art]
    if missing and fetch is not None:
        query = wanted.get("search") or manifest.data["game"]
        try:
            game_id = wanted.get("steamgriddb_id") or search_game(fetch, query)
            if game_id is None:
                warnings.append(f"{manifest.path.name}: SteamGridDB has no game matching '{query}'")
            else:
                art["steamgriddb_id"] = game_id
                for slot in missing:
                    images = fetch(f"{STEAMGRIDDB_API}/{ART_QUERIES[slot].format(id=game_id)}")
                    if images:
                        art[slot] = images[0]["url"]
        except Exception as exc:  # noqa: BLE001 - art is optional
            warnings.append(f"{manifest.path.name}: could not fetch art ({exc})")
    old = previous or {}
    for slot in ART_QUERIES:
        if slot not in art and old.get(slot):
            art[slot] = old[slot]
    if "steamgriddb_id" not in art and old.get("steamgriddb_id"):
        art["steamgriddb_id"] = old["steamgriddb_id"]
    return (art or None), warnings


def previous_art(previous: dict[str, Any] | None) -> dict[str, dict[str, Any]]:
    """Map port id -> art from a previously published index."""
    return {p["id"]: p["art"] for p in (previous or {}).get("ports", []) if p.get("art")}


# --------------------------------------------------------------------------- #
# Index
# --------------------------------------------------------------------------- #

PIN_KEYS = ("package", "signer_sha256")


class ForkRejected(Exception):
    """A release that must not be published; the fork keeps its previous release."""


def previous_forks(previous: dict[str, Any] | None) -> dict[tuple[str, str], dict[str, Any]]:
    """Map (port id, fork repo) -> fork as published in a previous index."""
    return {(port["id"], fork["repo"]): fork
            for port in (previous or {}).get("ports", [])
            for fork in port.get("forks", [])}


def resolve_release(
    fork: dict[str, Any],
    entry: dict[str, Any],
    rel: dict[str, Any],
    asset: dict[str, Any],
    old: dict[str, Any],
    download: Callable[[str], Path] | None,
    verify_signers: bool,
    inspect: Callable[[Path], tuple[str, list[str]]],
) -> dict[str, Any]:
    """The release entry for ``asset``, pinning package and signer into ``entry``.

    Raises ForkRejected when the release must not be published.
    """
    tag = rel["tag_name"]
    old_rel = old.get("release") or {}
    old_asset = old_rel.get("asset") or {}
    same_asset = old_rel.get("tag") == tag and old_asset.get("name") == asset["name"]
    digest = (asset.get("digest") or "").removeprefix("sha256:").lower() or None
    if same_asset and digest and old_asset.get("sha256") and digest != old_asset["sha256"]:
        raise ForkRejected(f"{tag}/{asset['name']} changed: was {old_asset['sha256']}, now {digest}")

    # Already published and nothing to re-check: reuse it without downloading.
    pins_unchanged = all(entry.get(k) == old.get(k) for k in PIN_KEYS)
    if same_asset and old_asset.get("sha256") and pins_unchanged and (
            not verify_signers or all(old.get(k) for k in PIN_KEYS)):
        return old_rel

    sha = asset_sha256(asset, download)
    if same_asset and sha and old_asset.get("sha256") and sha != old_asset["sha256"]:
        raise ForkRejected(f"{tag}/{asset['name']} changed: was {old_asset['sha256']}, now {sha}")

    zip_entry = None
    apk_path: Path | None = None
    if fork.get("zip_entry"):
        if download is None:
            raise ForkRejected("zip_entry needs --download")
        try:
            found = extract_zip_entry(download(asset["browser_download_url"]), fork["zip_entry"])
        except (OSError, zipfile.BadZipFile) as exc:
            raise ForkRejected(f"{tag}: cannot read ZIP ({exc})") from exc
        if found is None:
            raise ForkRejected(f"{tag}: nothing in {asset['name']} matches '{fork['zip_entry']}'")
        apk_path = found[1]
        zip_entry = {"path": found[0], "size": apk_path.stat().st_size, "sha256": sha256_file(apk_path)}

    if verify_signers and download is not None:
        try:
            package, signers = inspect(apk_path or download(asset["browser_download_url"]))
        except Exception as exc:  # noqa: BLE001 - an APK we cannot inspect is not published
            raise ForkRejected(f"{tag}: could not inspect APK ({exc})") from exc
        problems = []
        if entry.get("package") and entry["package"] != package:
            problems.append(f"package {package} does not match {entry['package']}")
        if entry.get("signer_sha256") and entry["signer_sha256"] not in signers:
            problems.append(f"signer {signers} does not match {entry['signer_sha256']}")
        if problems or not signers:
            raise ForkRejected(f"{tag}: " + ("; ".join(problems) or "APK is not signed"))
        entry.setdefault("package", package)
        entry.setdefault("signer_sha256", signers[0])

    return {
        "tag": tag,
        "name": rel.get("name") or tag,
        "published_at": rel.get("published_at"),
        "prerelease": bool(rel.get("prerelease")),
        "asset": {
            "name": asset["name"],
            "url": asset["browser_download_url"],
            "size": asset.get("size"),
            "sha256": sha,
            **({"entry": zip_entry} if zip_entry else {}),
        },
    }


def build_index(
    manifests: list[Manifest],
    fetch: Fetcher,
    download: Callable[[str], Path] | None = None,
    previous: dict[str, Any] | None = None,
    verify_signers: bool = False,
    now: dt.datetime | None = None,
    art_fetch: Fetcher | None = None,
    inspect: Callable[[Path], tuple[str, list[str]]] = inspect_apk,
) -> tuple[dict[str, Any], list[str], list[str]]:
    """Return (index, errors, warnings).

    With ``verify_signers``, each new release APK is inspected for its package
    and signer. The first values seen for a fork are pinned in the index (trust
    on first use) and later releases must match them; values written in the
    manifest take precedence, which is also how a legitimate key rotation is
    accepted. Releases already in ``previous`` are reused without downloading.

    A release that fails a check is not published: its fork keeps the release
    of the previous index and a warning is reported, so one broken port never
    blocks the rest of the catalog. ``errors`` is kept for problems that make
    the whole catalog unpublishable.
    """
    errors: list[str] = []
    warnings: list[str] = []
    old_forks = previous_forks(previous)
    old_art = previous_art(previous)
    ports = []
    for m in manifests:
        port = dict(m.data)
        art, art_warnings = resolve_art(m, art_fetch, old_art.get(m.data["id"]))
        warnings.extend(art_warnings)
        port.pop("art", None)
        if art:
            port["art"] = art
        forks = []
        for fork in m.data["forks"]:
            repo = fork["repo"]
            old = old_forks.get((m.data["id"], repo), {})
            entry = dict(fork)
            entry["release"] = None
            for key in PIN_KEYS:
                value = fork.get(key) or old.get(key)
                if value:
                    entry[key] = value
            forks.append(entry)
            try:
                releases = fetch(f"{GITHUB_API}/repos/{repo}/releases?per_page=20")
            except Exception as exc:  # noqa: BLE001 - network errors become warnings
                warnings.append(f"{m.path.name}: {repo}: could not list releases ({exc})")
                entry["release"] = old.get("release")
                continue
            channel = fork.get("channel", "stable")
            rel = pick_release(releases, channel, fork["asset_pattern"])
            if rel is None:
                newest = pick_release(releases, channel)
                warnings.append(f"{m.path.name}: {repo}: " + (
                    f"no recent release has an asset matching '{fork['asset_pattern']}'"
                    if newest else "no published release"))
                entry["release"] = old.get("release")
                continue
            asset = select_asset(rel.get("assets", []), fork["asset_pattern"])

            # Each fork's downloads are deleted once it is done, so a large
            # catalog never needs more disk than its biggest APK.
            fetched: dict[str, Path] = {}

            def fork_download(url: str) -> Path:
                if url not in fetched:
                    fetched[url] = download(url)
                return fetched[url]

            try:
                entry["release"] = resolve_release(
                    fork, entry, rel, asset, old,
                    fork_download if download is not None else None, verify_signers, inspect)
            except ForkRejected as exc:
                kept = old.get("release")
                warnings.append(f"{m.path.name}: {repo}: {exc}; "
                                + (f"keeping {kept['tag']}" if kept else "not published"))
                entry["release"] = kept
            finally:
                for path in fetched.values():
                    for leftover in (path, path.with_name(path.name + ".entry")):
                        leftover.unlink(missing_ok=True)
        port["forks"] = forks
        ports.append(port)

    stamp = (now or dt.datetime.now(dt.timezone.utc)).replace(microsecond=0)
    index = {
        "schema_version": SCHEMA_VERSION,
        "generated_at": stamp.isoformat().replace("+00:00", "Z"),
        "ports": ports,
    }
    return index, errors, warnings


def load_previous(source: str | None) -> dict[str, Any] | None:
    if not source:
        return None
    try:
        if re.match(r"^https?://", source):
            req = urllib.request.Request(source, headers={"User-Agent": "recompa-catalog"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.load(resp)
        return json.loads(Path(source).read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001 - first publish has no previous index
        print(f"warning: no previous index at {source} ({exc})", file=sys.stderr)
        return None


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--offline", action="store_true", help="only validate the manifests")
    parser.add_argument("--out", type=Path, default=ROOT / "catalog" / "dist" / "index.json")
    parser.add_argument("--previous", help="path or URL of the last published index.json")
    parser.add_argument("--download", action="store_true",
                        help="download APKs to hash them when GitHub gives no digest")
    parser.add_argument("--verify-signers", action="store_true",
                        help="read package and signer of each APK with aapt2 and apksigner, "
                             "pin them on first sight and reject changes (implies --download)")
    parser.add_argument("--strict", action="store_true", help="treat warnings as errors")
    args = parser.parse_args(argv)

    manifests = load_manifests()
    errors = validate(manifests, load_schema())
    if errors:
        print("\n".join(f"error: {e}" for e in errors), file=sys.stderr)
        return 1
    print(f"{len(manifests)} manifests are valid")
    if args.offline:
        return 0

    art_key = os.environ.get("STEAMGRIDDB_API_KEY")
    if not art_key:
        print("warning: STEAMGRIDDB_API_KEY is not set, keeping the art of the previous index",
              file=sys.stderr)
    with tempfile.TemporaryDirectory() as tmp:
        download = make_downloader(Path(tmp)) if (args.download or args.verify_signers) else None
        index, errors, warnings = build_index(
            manifests,
            fetch=github_fetcher(os.environ.get("GITHUB_TOKEN")),
            download=download,
            previous=load_previous(args.previous),
            verify_signers=args.verify_signers,
            art_fetch=steamgriddb_fetcher(art_key) if art_key else None,
        )
    in_actions = os.environ.get("GITHUB_ACTIONS") == "true"
    for w in warnings:
        print(f"::warning::{w}" if in_actions else f"warning: {w}", file=sys.stderr)
    for e in errors:
        print(f"error: {e}", file=sys.stderr)
    if errors or (args.strict and warnings):
        return 1

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    available = sum(1 for p in index["ports"] for f in p["forks"] if f["release"])
    print(f"wrote {args.out} ({len(index['ports'])} ports, {available} forks with a release)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
