"""Assembles the download website into a folder, ready to be published on GitHub Pages.

The catalog CI and the Site workflow both publish it next to the catalog's index.json.
Usage: python site/build.py OUT_DIR (needs Pillow).
"""

import hashlib
import shutil
import sys
from pathlib import Path

from PIL import Image

SITE = Path(__file__).resolve().parent
ROOT = SITE.parent
FILES = ["index.html", "style.css", "app.js", "icon.png"]
# Cloudflare Web Analytics (visits, no cookies). The token is public: it is in the page.
WEB_ANALYTICS_TOKEN = ""


def main(out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    for name in FILES:
        shutil.copy2(SITE / name, out / name)
    # Browsers cache app.js and style.css; versioning their URLs keeps them in step with index.html.
    html = (out / "index.html").read_text(encoding="utf-8")
    for name in ("app.js", "style.css"):
        version = hashlib.sha256((SITE / name).read_bytes()).hexdigest()[:10]
        html = html.replace(f'"{name}"', f'"{name}?v={version}"')
    if WEB_ANALYTICS_TOKEN:
        beacon = ("<script defer src=\"https://static.cloudflareinsights.com/beacon.min.js\" "
                  f"data-cf-beacon='{{\"token\": \"{WEB_ANALYTICS_TOKEN}\"}}'></script>\n")
        html = html.replace("</body>", beacon + "</body>")
    (out / "index.html").write_text(html, encoding="utf-8")
    shutil.copy2(ROOT / "docs" / "logo.svg", out / "logo.svg")
    # The README screenshots are large PNGs; the site uses smaller JPEGs.
    shots = out / "screenshots"
    shots.mkdir(exist_ok=True)
    for png in sorted((ROOT / "docs" / "screenshots").glob("*.png")):
        img = Image.open(png).convert("RGB")
        img.thumbnail((1280, 1280))
        img.save(shots / f"{png.stem}.jpg", quality=82, optimize=True, progressive=True)
    (out / ".nojekyll").touch()


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(Path(sys.argv[1]))
