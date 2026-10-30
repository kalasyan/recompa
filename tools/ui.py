"""Helpers for tools/screenshots.sh.

  ui.py find DUMP.xml LABEL...       prints "x1 y1 x2 y2" around every node whose text or
                                     content description is one of LABELs (exit 1 if none)
  ui.py highlight IMAGE.png x1 y1 x2 y2 [x1 y1 x2 y2 ...]
                                     draws an orange frame around each box, in place
"""
import re
import sys
import xml.etree.ElementTree as ET


def find(dump, labels):
    boxes = []
    for node in ET.parse(dump).iter("node"):
        if node.get("text") in labels or node.get("content-desc") in labels:
            boxes.append([int(n) for n in re.findall(r"\d+", node.get("bounds", ""))])
    if not boxes:
        return 1
    print(min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))
    return 0


def highlight(image, coords):
    from PIL import Image, ImageDraw

    img = Image.open(image).convert("RGB")
    draw = ImageDraw.Draw(img)
    pad = 14
    for i in range(0, len(coords), 4):
        x1, y1, x2, y2 = coords[i:i + 4]
        draw.rounded_rectangle((x1 - pad, y1 - pad, x2 + pad, y2 + pad), radius=24, outline="#F97316", width=8)
    img.save(image)
    return 0


if __name__ == "__main__":
    cmd, path, *rest = sys.argv[1:]
    if cmd == "find":
        sys.exit(find(path, set(rest)))
    sys.exit(highlight(path, [int(n) for n in rest]))
