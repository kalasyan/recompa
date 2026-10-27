import datetime as dt
import sys
from pathlib import Path

import shutil

import pytest

sys.path.insert(0, str(Path(__file__).parent))

import build_index as bi  # noqa: E402

SCHEMA = bi.load_schema()


def manifest(**overrides):
    data = {
        "id": "demo",
        "name": "Demo",
        "game": "Demo Game",
        "original_platform": "n64",
        "type": "decomp",
        "forks": [{"repo": "someone/demo-android", "asset_pattern": r"\.apk$"}],
        "requirements": {"abi": ["arm64-v8a"], "min_sdk": 24},
    }
    data.update(overrides)
    return bi.Manifest(Path(f"{data['id']}.yaml"), data)


def release(tag="v1.0", prerelease=False, draft=False, assets=None):
    return {
        "tag_name": tag,
        "name": tag,
        "published_at": "2026-10-01T00:00:00Z",
        "prerelease": prerelease,
        "draft": draft,
        "assets": assets if assets is not None else [asset("demo.apk")],
    }


def asset(name, digest="sha256:" + "a" * 64, size=10):
    return {"name": name, "browser_download_url": f"https://example.com/{name}",
            "size": size, "digest": digest}


def test_repository_manifests_are_valid():
    assert bi.validate(bi.load_manifests(), SCHEMA) == []


def test_validate_reports_schema_errors_and_id_mismatch():
    bad = manifest(type="emulator")
    bad.path = Path("other.yaml")
    errors = bi.validate([bad], SCHEMA)
    assert any("type" in e for e in errors)
    assert any("must match the file name" in e for e in errors)


def test_validate_rejects_invalid_regex():
    m = manifest(forks=[{"repo": "a/b", "asset_pattern": "(unclosed"}])
    assert any("invalid regex" in e for e in bi.validate([m], SCHEMA))


def test_validate_rejects_duplicate_ids():
    errors = bi.validate([manifest(), manifest()], SCHEMA)
    assert any("already used" in e for e in errors)


def test_pick_release_skips_drafts_and_prereleases_on_stable():
    releases = [release("v3", draft=True), release("v2", prerelease=True), release("v1")]
    assert bi.pick_release(releases, "stable")["tag_name"] == "v1"
    assert bi.pick_release(releases, "beta")["tag_name"] == "v2"
    assert bi.pick_release([release(draft=True)], "beta") is None


def test_select_asset_prefers_arm64():
    assets = [asset("game-x86_64.apk"), asset("game-arm64-v8a.apk"), asset("notes.txt")]
    assert bi.select_asset(assets, r"\.apk$")["name"] == "game-arm64-v8a.apk"
    assert bi.select_asset(assets, r"\.aab$") is None


def test_build_index_records_release_and_hash():
    index, errors, warnings = bi.build_index(
        [manifest()], fetch=lambda url: [release()],
        now=dt.datetime(2026, 10, 27, tzinfo=dt.timezone.utc))
    assert errors == [] and warnings == []
    rel = index["ports"][0]["forks"][0]["release"]
    assert rel["tag"] == "v1.0"
    assert rel["asset"]["sha256"] == "a" * 64
    assert index["generated_at"] == "2026-10-27T00:00:00Z"


def test_build_index_rejects_changed_hash_for_same_release():
    first, _, _ = bi.build_index([manifest()], fetch=lambda url: [release()])
    changed = [release(assets=[asset("demo.apk", digest="sha256:" + "b" * 64)])]
    index, errors, warnings = bi.build_index([manifest()], fetch=lambda url: changed, previous=first)
    assert errors == []
    assert any("changed" in w and "keeping v1.0" in w for w in warnings)
    assert index["ports"][0]["forks"][0]["release"] == first["ports"][0]["forks"][0]["release"]


def test_build_index_warns_when_no_asset_or_network_fails():
    def boom(url):
        raise OSError("offline")

    _, errors, warnings = bi.build_index([manifest()], fetch=boom)
    assert errors == [] and "could not list releases" in warnings[0]
    _, _, warnings = bi.build_index([manifest()], fetch=lambda url: [release(assets=[])])
    assert "no asset matches" in warnings[0]


def test_asset_sha256_downloads_when_no_digest(tmp_path):
    apk = tmp_path / "demo.apk"
    apk.write_bytes(b"hello")
    sha = bi.asset_sha256(asset("demo.apk", digest=None), download=lambda url: apk)
    assert sha == "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    assert bi.asset_sha256(asset("demo.apk", digest=None), download=None) is None


def fake_steamgriddb(games, images):
    """A SteamGridDB fetcher answering searches from ``games`` and image lists from ``images``."""
    calls = []

    def fetch(url):
        calls.append(url)
        if "/search/autocomplete/" in url:
            return games
        kind = url.split("/api/v2/", 1)[1].split("/", 1)[0]
        return images.get(kind, [])
    fetch.calls = calls
    return fetch


def test_resolve_art_prefers_exact_title_and_fills_every_slot():
    fetch = fake_steamgriddb(
        games=[{"id": 1, "name": "Demo Game 2"}, {"id": 7, "name": "demo game"}],
        images={"grids": [{"url": "https://cdn/cover.png"}], "heroes": [{"url": "https://cdn/hero.png"}],
                "logos": [{"url": "https://cdn/logo.png"}]})
    art, warnings = bi.resolve_art(manifest(), fetch, None)
    assert warnings == []
    assert art == {"steamgriddb_id": 7, "cover": "https://cdn/cover.png",
                   "hero": "https://cdn/hero.png", "logo": "https://cdn/logo.png"}
    assert "dimensions=600x900" in next(c for c in fetch.calls if "/grids/game/7" in c)


def test_resolve_art_uses_pinned_id_search_name_and_manual_urls():
    fetch = fake_steamgriddb(games=[{"id": 3, "name": "Diablo"}],
                             images={"heroes": [{"url": "https://cdn/hero.png"}]})
    m = manifest(art={"search": "Diablo", "cover": "https://example.com/mine.png"})
    art, _ = bi.resolve_art(m, fetch, None)
    assert art["cover"] == "https://example.com/mine.png"
    assert art["hero"] == "https://cdn/hero.png" and "logo" not in art
    assert fetch.calls[0].endswith("/search/autocomplete/Diablo")

    fetch = fake_steamgriddb(games=[], images={"grids": [{"url": "https://cdn/c.png"}]})
    art, _ = bi.resolve_art(manifest(art={"steamgriddb_id": 42}), fetch, None)
    assert art["steamgriddb_id"] == 42 and not any("autocomplete" in c for c in fetch.calls)


def test_resolve_art_keeps_previous_art_without_key_or_on_failure():
    old = {"steamgriddb_id": 7, "cover": "https://cdn/old.png"}
    assert bi.resolve_art(manifest(), None, old) == (old, [])

    def boom(url):
        raise OSError("offline")
    art, warnings = bi.resolve_art(manifest(), boom, old)
    assert art == old and "could not fetch art" in warnings[0]
    assert bi.resolve_art(manifest(), None, None) == (None, [])


def test_build_index_publishes_art_without_search_hints():
    fetch = fake_steamgriddb(games=[{"id": 7, "name": "Diablo"}],
                             images={"grids": [{"url": "https://cdn/cover.png"}]})
    index, errors, _ = bi.build_index([manifest(art={"search": "Diablo"})],
                                      fetch=lambda url: [release()], art_fetch=fetch)
    assert errors == []
    assert index["ports"][0]["art"] == {"steamgriddb_id": 7, "cover": "https://cdn/cover.png"}

    again, _, _ = bi.build_index([manifest()], fetch=lambda url: [release()], previous=index)
    assert again["ports"][0]["art"]["cover"] == "https://cdn/cover.png"


SIGNER = "c" * 64


def inspected(package="com.demo", signers=(SIGNER,)):
    return lambda apk: (package, list(signers))


def build_verified(manifests, inspect, previous=None, tag="v1.0"):
    return bi.build_index(manifests, fetch=lambda url: [release(tag)], previous=previous,
                          download=lambda url: Path("demo.apk"), verify_signers=True,
                          inspect=inspect)


def test_first_sight_pins_package_and_signer():
    index, errors, _ = build_verified([manifest()], inspected())
    fork = index["ports"][0]["forks"][0]
    assert errors == []
    assert (fork["package"], fork["signer_sha256"]) == ("com.demo", SIGNER)


def test_pins_from_previous_index_reject_a_new_signer():
    first, _, _ = build_verified([manifest()], inspected())
    index, errors, warnings = build_verified([manifest()], inspected(signers=["d" * 64]),
                                             previous=first, tag="v2.0")
    assert errors == [] and any("signer" in w for w in warnings)
    fork = index["ports"][0]["forks"][0]
    assert fork["release"]["tag"] == "v1.0" and fork["signer_sha256"] == SIGNER
    _, _, warnings = build_verified([manifest()], inspected(package="com.evil"), previous=first, tag="v2.0")
    assert any("package com.evil" in w for w in warnings)


def test_published_release_is_reused_without_downloading():
    first, _, _ = build_verified([manifest()], inspected())

    def no_download(url):
        raise AssertionError("should not download")

    index, errors, warnings = bi.build_index(
        [manifest()], fetch=lambda url: [release()], previous=first,
        download=no_download, verify_signers=True, inspect=inspected())
    assert errors == [] and warnings == []
    assert index["ports"][0]["forks"][0] == first["ports"][0]["forks"][0]


def test_pins_are_kept_per_port_even_when_ports_share_a_repo():
    first, _, _ = build_verified([manifest()], inspected())
    other = manifest(id="other")
    index, _, _ = build_verified([other], inspected(package="com.other", signers=["e" * 64]),
                                 previous=first)
    assert index["ports"][0]["forks"][0]["package"] == "com.other"


def test_manifest_pin_overrides_previous_index():
    first, _, _ = build_verified([manifest()], inspected())
    rotated = manifest(forks=[{"repo": "someone/demo-android", "asset_pattern": r"\.apk$",
                               "signer_sha256": "d" * 64}])
    index, errors, _ = build_verified([rotated], inspected(signers=["d" * 64]), previous=first)
    assert errors == []
    assert index["ports"][0]["forks"][0]["signer_sha256"] == "d" * 64


def test_uninspectable_apk_is_not_published():
    def broken(apk):
        raise OSError("not an APK")

    index, errors, warnings = build_verified([manifest()], broken)
    assert errors == [] and any("could not inspect" in w and "not published" in w for w in warnings)
    assert index["ports"][0]["forks"][0]["release"] is None


def test_zip_entry_publishes_the_apk_inside_the_archive(tmp_path):
    import zipfile
    archive = tmp_path / "v5-android.zip"
    with zipfile.ZipFile(archive, "w") as zf:
        zf.writestr("readme.txt", "hi")
        zf.writestr("out/app-x86_64-release.apk", b"x86")
        zf.writestr("out/app-arm64-v8a-release.apk", b"hello")
    m = manifest(forks=[{"repo": "someone/demo-android", "asset_pattern": r"android\.zip$",
                         "zip_entry": r"\.apk$"}])
    inspected_paths = []

    def inspect(apk):
        inspected_paths.append(apk.read_bytes())
        return "com.demo", [SIGNER]

    index, errors, warnings = bi.build_index(
        [m], fetch=lambda url: [release(assets=[asset("v5-android.zip")])],
        download=lambda url: Path(shutil.copy(archive, tmp_path / "download")), verify_signers=True, inspect=inspect)
    assert errors == [] and warnings == []
    entry = index["ports"][0]["forks"][0]["release"]["asset"]["entry"]
    assert entry["path"] == "out/app-arm64-v8a-release.apk"
    assert entry["sha256"] == "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    assert inspected_paths == [b"hello"]

    m.data["forks"][0]["zip_entry"] = r"\.aab$"
    _, _, warnings = bi.build_index(
        [m], fetch=lambda url: [release(assets=[asset("v5-android.zip")])],
        download=lambda url: Path(shutil.copy(archive, tmp_path / "download")), verify_signers=True, inspect=inspect)
    assert "nothing in v5-android.zip matches" in warnings[0]


if __name__ == "__main__":
    sys.exit(pytest.main([__file__]))
