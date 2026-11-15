"""Generates the Overtime app icon: a master PNG, the window icon PNG and a multi-size Windows ICO.

Design: the Material Symbols "live_tv" glyph (Outlined) in white, filling a rounded square in the app's
default sidebar / title bar colour. Large sizes use the 48 px optical size; small sizes use the heavier
20 px one so the strokes stay crisp at 16-32 px.

Glyph sources (Apache 2.0, google/material-design-icons): tools/symbols/live_tv_{20,48}px.svg

Run from the repo root:  python tools/make_icon.py
"""
import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ICON_DIR = ROOT / "composeApp" / "icons"
RES_DIR = ROOT / "composeApp" / "src" / "jvmMain" / "resources"
SYMBOLS = Path(__file__).resolve().parent / "symbols"

BACKGROUND = (25, 28, 32)     # the app's default sidebar / title bar colour (#191C20)
GLYPH = (255, 255, 255)

ICO_SIZES = [16, 20, 24, 32, 40, 48, 64, 96, 128, 256]
SMALL_MAX = 32                # sizes up to this use the 20 px optical size
SS = 8                        # supersampling factor

# ---- tiny SVG path reader (M L H V Q T C S Z, absolute and relative) -------------------

_TOKEN = re.compile(r"[MmLlHhVvQqTtCcSsZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
_ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "Q": 4, "T": 2, "C": 6, "S": 4, "Z": 0}
_STEPS = 16


def _quad(p0, p1, p2):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1])
            for t in (i / _STEPS for i in range(1, _STEPS + 1))]


def _cubic(p0, p1, p2, p3):
    return [((1 - t) ** 3 * p0[0] + 3 * (1 - t) ** 2 * t * p1[0] + 3 * (1 - t) * t * t * p2[0] + t ** 3 * p3[0],
             (1 - t) ** 3 * p0[1] + 3 * (1 - t) ** 2 * t * p1[1] + 3 * (1 - t) * t * t * p2[1] + t ** 3 * p3[1])
            for t in (i / _STEPS for i in range(1, _STEPS + 1))]


def parse_path(d):
    """Flattens an SVG path into a list of closed polygons (lists of (x, y))."""
    tokens = _TOKEN.findall(d)
    polygons, current = [], []
    x = y = sx = sy = 0.0
    last_ctrl, last_cmd, cmd, i = None, None, None, 0
    while i < len(tokens):
        if tokens[i].isalpha():
            cmd = tokens[i]
            i += 1
        elif cmd in ("M", "m"):
            cmd = "L" if cmd == "M" else "l"  # extra pairs after a move are lines
        up = cmd.upper()
        rel = cmd.islower()
        args = [float(t) for t in tokens[i:i + _ARGS[up]]]
        i += _ARGS[up]
        ox, oy = (x, y) if rel else (0.0, 0.0)
        if up == "M":
            if len(current) > 2:
                polygons.append(current)
            x, y = ox + args[0], oy + args[1]
            sx, sy = x, y
            current = [(x, y)]
        elif up == "L":
            x, y = ox + args[0], oy + args[1]
            current.append((x, y))
        elif up == "H":
            x = (x if rel else 0.0) + args[0]
            current.append((x, y))
        elif up == "V":
            y = (y if rel else 0.0) + args[0]
            current.append((x, y))
        elif up in ("Q", "T"):
            if up == "Q":
                c = (ox + args[0], oy + args[1])
                end = (ox + args[2], oy + args[3])
            else:
                c = (2 * x - last_ctrl[0], 2 * y - last_ctrl[1]) if last_cmd in ("Q", "T") else (x, y)
                end = (ox + args[0], oy + args[1])
            current += _quad((x, y), c, end)
            last_ctrl = c
            x, y = end
        elif up in ("C", "S"):
            if up == "C":
                c1 = (ox + args[0], oy + args[1])
                c2 = (ox + args[2], oy + args[3])
                end = (ox + args[4], oy + args[5])
            else:
                c1 = (2 * x - last_ctrl[0], 2 * y - last_ctrl[1]) if last_cmd in ("C", "S") else (x, y)
                c2 = (ox + args[0], oy + args[1])
                end = (ox + args[2], oy + args[3])
            current += _cubic((x, y), c1, c2, end)
            last_ctrl = c2
            x, y = end
        elif up == "Z":
            if len(current) > 2:
                polygons.append(current)
            current = []
            x, y = sx, sy
        last_cmd = up
    if len(current) > 2:
        polygons.append(current)
    return polygons


def load_symbol(px):
    svg = (SYMBOLS / f"live_tv_{px}px.svg").read_text()
    return parse_path(re.search(r'\sd="([^"]+)"', svg).group(1))



def _mask(polygons, size, scale, offset):
    """Even-odd fill: XOR of every polygon, which keeps the screen hollow and the triangle solid."""
    mask = Image.new("1", (size, size), 0)
    for poly in polygons:
        layer = Image.new("1", (size, size), 0)
        pts = [(offset + px * scale, offset + (py + 960) * scale) for px, py in poly]  # viewBox is 0 -960 960 960
        ImageDraw.Draw(layer).polygon(pts, fill=1)
        mask = ImageChops.logical_xor(mask, layer)
    return mask.convert("L")


# ---- icon --------------------------------------------------------------------------

def render(size):
    small = size <= SMALL_MAX
    s = max(size * SS, 1024)
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))

    # Rounded square, nearly full bleed, corner ≈ 22 % (an extra-large M3 corner at icon scale).
    margin = round(s * (0.02 if small else 0.04))
    radius = round(s * 0.22)
    ImageDraw.Draw(img).rounded_rectangle([margin, margin, s - margin - 1, s - margin - 1], radius, fill=BACKGROUND)

    # Glyph box as a share of the icon. The symbol's own padding leaves the TV at ~83 % of the box, so
    # 0.84 puts it at ~70 % of the icon width with breathing room; small sizes run a bit larger to stay legible.
    glyph_box = s * (0.92 if small else 0.84)
    scale = glyph_box / 960
    offset = (s - glyph_box) / 2

    polygons = load_symbol(20 if small else 48)
    img.paste(Image.new("RGBA", (s, s), GLYPH + (255,)), (0, 0), _mask(polygons, s, scale, offset))
    return img.resize((size, size), Image.LANCZOS)


def main():
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    RES_DIR.mkdir(parents=True, exist_ok=True)

    render(1024).save(ICON_DIR / "overtime.png")
    render(256).save(RES_DIR / "overtime_icon.png")

    images = [render(size) for size in ICO_SIZES]
    images[-1].save(
        ICON_DIR / "overtime.ico",
        sizes=[(size, size) for size in ICO_SIZES],
        append_images=images[:-1],
    )
    print("Wrote", ICON_DIR / "overtime.png", ICON_DIR / "overtime.ico", RES_DIR / "overtime_icon.png")


if __name__ == "__main__":
    main()
