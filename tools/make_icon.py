"""Generates the Overtime app icon for the Windows app (master PNG, window icon PNG, multi-size ICO) and,
optionally, the Android app's launcher icon layers.

Design: the Material Icons "live_tv" glyph (Rounded: a TV with antennas and a play triangle) in near black
(#0A0A0B) on a light blue (#8AD0F2) circle. The TV casts a soft shadow down and to the right, and the
circle is shaded slightly along its bottom edge. Both apps share this look.

Glyph source (Apache 2.0, Compose material-icons-extended Icons.Rounded.LiveTv): tools/symbols/live_tv_rounded_24px.svg

Run from the repo root:
    python tools/make_icon.py
    python tools/make_icon.py --android <OvertimeAndroid>/app/src/main/res
"""
import argparse
import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ICON_DIR = ROOT / "composeApp" / "icons"
RES_DIR = ROOT / "composeApp" / "src" / "jvmMain" / "resources"
SYMBOLS = Path(__file__).resolve().parent / "symbols"

BACKGROUND = (0x8A, 0xD0, 0xF2)  # light blue (#8AD0F2), chosen by the user
GLYPH = (0x0A, 0x0A, 0x0B)       # near black (#0A0A0B)

ICO_SIZES = [16, 20, 24, 32, 40, 48, 64, 96, 128, 256]
SMALL_MAX = 32                # sizes up to this use a larger glyph and no shadows, to stay crisp
SS = 8                        # supersampling factor

# Shadows, as shares of the icon size (tuned against the reference image the user picked).
GLYPH_SHADOW_DX = 0.020       # the TV's shadow falls down and to the right
GLYPH_SHADOW_DY = 0.030
GLYPH_SHADOW_BLUR = 0.025
GLYPH_SHADOW_OPACITY = 0.27
RIM_SHIFT = 0.020             # the circle's own edge shading, strongest at the bottom
RIM_BLUR = 0.020
RIM_OPACITY = 0.17

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


def _shifted(mask, dx, dy):
    """[mask] moved by (dx, dy) pixels, with empty space coming in at the edges."""
    out = Image.new("L", mask.size, 0)
    out.paste(mask, (round(dx), round(dy)))
    return out


def _scaled(mask, opacity):
    return mask.point(lambda v: round(v * opacity))


def _circle(s, margin):
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).ellipse([margin, margin, s - margin - 1, s - margin - 1], fill=255)
    return mask


def _glyph_mask(s, glyph_box):
    """The TV glyph in an s x s canvas: glyph_box px wide, its screen (not the antennas) centred."""
    scale = glyph_box / VIEWBOX
    offset = (s - glyph_box) / 2
    offset_y = s / 2 - SCREEN_CENTRE_Y * scale
    return _mask(load_symbol(), s, scale, offset, offset_y)


def _glyph_shadow(glyph, unit):
    """Soft shadow of the glyph, as an alpha mask; [unit] is the icon size the shadow shares refer to."""
    shadow = _shifted(glyph, GLYPH_SHADOW_DX * unit, GLYPH_SHADOW_DY * unit)
    shadow = shadow.filter(ImageFilter.GaussianBlur(GLYPH_SHADOW_BLUR * unit))
    return _scaled(shadow, GLYPH_SHADOW_OPACITY)


def _rim_shade(circle, unit):
    """Shading inside the circle along its edge, strongest at the bottom, as an alpha mask."""
    outside = ImageChops.invert(_shifted(circle, 0, -RIM_SHIFT * unit))
    shade = outside.filter(ImageFilter.GaussianBlur(RIM_BLUR * unit))
    return ImageChops.multiply(_scaled(shade, RIM_OPACITY), circle)


def _paint(img, colour, alpha):
    img.paste(Image.new("RGBA", img.size, colour + (255,)), (0, 0), alpha)


# ---- Windows icon ---------------------------------------------------------------------

def render(size):
    small = size <= SMALL_MAX
    s = max(size * SS, 1024)
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))

    circle = _circle(s, round(s * (0.02 if small else 0.0)))
    _paint(img, BACKGROUND, circle)

    # Glyph box as a share of the icon. The TV fills 22/24 of its box, so 0.72 puts it at ~66 % of the
    # icon width with breathing room; small sizes run a bit larger to stay legible.
    glyph = _glyph_mask(s, s * (0.80 if small else 0.72))
    if not small:
        _paint(img, (0, 0, 0), _rim_shade(circle, s))
        _paint(img, (0, 0, 0), ImageChops.multiply(_glyph_shadow(glyph, s), circle))
    _paint(img, GLYPH, glyph)
    img.putalpha(circle)
    return img.resize((size, size), Image.LANCZOS)


def write_windows():
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


# ---- Android launcher icon ------------------------------------------------------------

# Adaptive icon layers are 108 dp; the launcher shows the middle 72 dp through its own mask shape.
ADAPTIVE_DP = 108
VISIBLE_DP = 72
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
LEGACY_DP = 48
BOTTOM_SHADE_HEIGHT = 0.12  # share of the visible icon, at the bottom, that darkens


def _adaptive_layers(px):
    """Foreground (glyph + its shadow) and background (blue + rim shade) layers, px x px."""
    s = px * SS
    unit = s * VISIBLE_DP / ADAPTIVE_DP  # the shadows are tuned to the visible icon, not the 108 dp layer
    glyph = _glyph_mask(s, unit * 0.72)
    fg = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    _paint(fg, (0, 0, 0), _glyph_shadow(glyph, unit))
    _paint(fg, GLYPH, glyph)

    # The launcher picks the mask shape (circle, squircle, rounded square), so instead of the Windows
    # icon's circle rim the background darkens towards the bottom of the visible area, which reads the
    # same under any shape.
    bg = Image.new("RGBA", (s, s), BACKGROUND + (255,))
    bottom = (s + unit) / 2
    band = BOTTOM_SHADE_HEIGHT * unit
    shade = Image.new("L", (1, s), 0)
    for y in range(s):
        t = min(max((y - (bottom - band)) / band, 0.0), 1.0)
        shade.putpixel((0, y), round(255 * RIM_OPACITY * t * t))
    _paint(bg, (0, 0, 0), shade.resize((s, s)))
    return fg.resize((px, px), Image.LANCZOS), bg.resize((px, px), Image.LANCZOS)


def write_android(res):
    res = Path(res)
    for name, density in DENSITIES.items():
        folder = res / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        fg, bg = _adaptive_layers(round(ADAPTIVE_DP * density))
        fg.save(folder / "ic_launcher_foreground.webp", lossless=True)
        bg.save(folder / "ic_launcher_background.webp", lossless=True)
        legacy = render(round(LEGACY_DP * density))
        legacy.save(folder / "ic_launcher.webp", lossless=True)
        legacy.save(folder / "ic_launcher_round.webp", lossless=True)
    print("Wrote Android launcher icons under", res)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--android", metavar="RES_DIR", help="also write the Android app's launcher icons")
    args = parser.parse_args()
    write_windows()
    if args.android:
        write_android(args.android)


if __name__ == "__main__":
    main()
