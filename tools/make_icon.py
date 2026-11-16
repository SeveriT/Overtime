"""Generates the Overtime app icon: a master PNG, the window icon PNG and a multi-size Windows ICO.

Design: the Material Icons "live_tv" glyph (Rounded: a TV with antennas and a play triangle) in near black
(#0A0A0B) on a light blue (#8AD0F2) rounded square. It is the same icon the Android app shows for Live TV,
and the Android launcher icon uses the same colours, so both apps share one look.

Glyph source (Apache 2.0, Compose material-icons-extended Icons.Rounded.LiveTv): tools/symbols/live_tv_rounded_24px.svg

Run from the repo root:  python tools/make_icon.py
"""
import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ICON_DIR = ROOT / "composeApp" / "icons"
RES_DIR = ROOT / "composeApp" / "src" / "jvmMain" / "resources"
SYMBOLS = Path(__file__).resolve().parent / "symbols"

BACKGROUND = (0x8A, 0xD0, 0xF2)  # light blue (#8AD0F2), chosen by the user
GLYPH = (0x0A, 0x0A, 0x0B)       # near black (#0A0A0B)

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


VIEWBOX = 24  # the glyph is drawn in a 24 x 24 box
SCREEN_CENTRE_Y = 14  # middle of the TV's screen in glyph units


def load_symbol():
    svg = (SYMBOLS / "live_tv_rounded_24px.svg").read_text()
    return parse_path(re.search(r'\sd="([^"]+)"', svg).group(1))



def _mask(polygons, size, scale, offset, offset_y):
    """Even-odd fill: XOR of every polygon, which keeps the screen hollow and the triangle solid."""
    mask = Image.new("1", (size, size), 0)
    for poly in polygons:
        layer = Image.new("1", (size, size), 0)
        pts = [(offset + px * scale, offset_y + py * scale) for px, py in poly]
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

    # Glyph box as a share of the icon. The TV fills 22/24 of its box, so 0.72 puts it at ~66 % of the
    # icon width with breathing room; small sizes run a bit larger to stay legible.
    glyph_box = s * (0.80 if small else 0.72)
    scale = glyph_box / VIEWBOX
    offset = (s - glyph_box) / 2
    # Centre the TV's screen (y 6..22 of the glyph), not the whole glyph: the antennas would push it low.
    offset_y = s / 2 - SCREEN_CENTRE_Y * scale

    polygons = load_symbol()
    img.paste(Image.new("RGBA", (s, s), GLYPH + (255,)), (0, 0), _mask(polygons, s, scale, offset, offset_y))
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
