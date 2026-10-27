#!/usr/bin/env python3
"""Build Recompa's catalog index from the port manifests.

Reads every ``catalog/ports/*.yaml``, validates it against the JSON schema,
looks up the latest GitHub release of each fork, and writes ``index.json``
with the APK asset, its size and its SHA-256 for every fork.

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
import urllib.request
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
            pattern = fork.get("asset_pattern")
            if pattern is None:
                continue
            try:
                re.compile(pattern)
            except re.error as exc:
                errors.append(f"{name}: forks/{i}/asset_pattern: invalid regex ({exc})")
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


def pick_release(releases: list[dict[str, Any]], channel: str) -> dict[str, Any] | None:
    """Newest published release; stable channels skip prereleases."""
    for rel in releases:
        if rel.get("draft"):
            continue
        if channel == "stable" and rel.get("prerelease"):
            continue
        return rel
    return None


def select_asset(assets: list[dict[str, Any]], pattern: str) -> dict[str, Any] | None:
    """Pick the asset matching ``pattern``; prefer arm64 builds when several match."""
    regex = re.compile(pattern)
    matches = [a for a in assets if regex.search(a.get("name", ""))]
    if not matches:
        return None
    arm64 = [a for a in matches if re.search(r"arm64|aarch64|v8a", a["name"], re.I)]
    universal = [a for a in matches if re.search(r"universal", a["name"], re.I)]
    return (arm64 or universal or matches)[0]


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
    cache: dict[str, Path] = {}

    def download(url: str) -> Path:
        if url not in cache:
            target = workdir / hashlib.sha1(url.encode()).hexdigest()
            req = urllib.request.Request(url, headers={"User-Agent": "recompa-catalog"})
            with urllib.request.urlopen(req, timeout=300) as resp, target.open("wb") as out:
                shutil.copyfileobj(resp, out)
            cache[url] = target
        return cache[url]
    return download


def apk_signer_sha256(apk: Path) -> list[str]:
    """SHA-256 of each signer certificate, via Android build-tools' apksigner."""
    out = subprocess.run(["apksigner", "verify", "--print-certs", str(apk)],
                         check=True, capture_output=True, text=True).stdout
    return [m.lower() for m in re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F]{64})", out)]


# --------------------------------------------------------------------------- #
# Index
# --------------------------------------------------------------------------- #

def previous_hashes(previous: dict[str, Any] | None) -> dict[tuple[str, str, str], str]:
    """Map (repo, tag, asset name) -> sha256 from a previously published index."""
    pinned: dict[tuple[str, str, str], str] = {}
    for port in (previous or {}).get("ports", []):
        for fork in port.get("forks", []):
            rel = fork.get("release") or {}
            asset = rel.get("asset") or {}
            if asset.get("sha256"):
                pinned[(fork["repo"], rel["tag"], asset["name"])] = asset["sha256"]
    return pinned


def build_index(
    manifests: list[Manifest],
    fetch: Fetcher,
    download: Callable[[str], Path] | None = None,
    previous: dict[str, Any] | None = None,
    verify_signers: bool = False,
    now: dt.datetime | None = None,
) -> tuple[dict[str, Any], list[str], list[str]]:
    """Return (index, errors, warnings)."""
    errors: list[str] = []
    warnings: list[str] = []
    pinned = previous_hashes(previous)
    ports = []
    for m in manifests:
        port = dict(m.data)
        forks = []
        for fork in m.data["forks"]:
            entry = dict(fork)
            entry["release"] = None
            repo = fork["repo"]
            try:
                releases = fetch(f"{GITHUB_API}/repos/{repo}/releases?per_page=20")
            except Exception as exc:  # noqa: BLE001 - network errors become warnings
                warnings.append(f"{m.path.name}: {repo}: could not list releases ({exc})")
                forks.append(entry)
                continue
            rel = pick_release(releases, fork.get("channel", "stable"))
            if rel is None:
                warnings.append(f"{m.path.name}: {repo}: no published release")
                forks.append(entry)
                continue
            asset = select_asset(rel.get("assets", []), fork["asset_pattern"])
            if asset is None:
                warnings.append(f"{m.path.name}: {repo} {rel['tag_name']}: no asset matches "
                                f"'{fork['asset_pattern']}'")
                forks.append(entry)
                continue
            sha = asset_sha256(asset, download)
            key = (repo, rel["tag_name"], asset["name"])
            if sha and key in pinned and pinned[key] != sha:
                errors.append(f"{m.path.name}: {repo} {rel['tag_name']}/{asset['name']} changed: "
                              f"was {pinned[key]}, now {sha}")
                forks.append(entry)
                continue
            if verify_signers and fork.get("signer_sha256") and download is not None:
                signers = apk_signer_sha256(download(asset["browser_download_url"]))
                if fork["signer_sha256"] not in signers:
                    errors.append(f"{m.path.name}: {repo} {rel['tag_name']}: signer {signers} "
                                  f"does not match {fork['signer_sha256']}")
                    forks.append(entry)
                    continue
            entry["release"] = {
                "tag": rel["tag_name"],
                "name": rel.get("name") or rel["tag_name"],
                "published_at": rel.get("published_at"),
                "prerelease": bool(rel.get("prerelease")),
                "asset": {
                    "name": asset["name"],
                    "url": asset["browser_download_url"],
                    "size": asset.get("size"),
                    "sha256": sha,
                },
            }
            forks.append(entry)
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
                        help="check signer_sha256 with apksigner (implies --download)")
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

    with tempfile.TemporaryDirectory() as tmp:
        download = make_downloader(Path(tmp)) if (args.download or args.verify_signers) else None
        index, errors, warnings = build_index(
            manifests,
            fetch=github_fetcher(os.environ.get("GITHUB_TOKEN")),
            download=download,
            previous=load_previous(args.previous),
            verify_signers=args.verify_signers,
        )
    for w in warnings:
        print(f"warning: {w}", file=sys.stderr)
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
