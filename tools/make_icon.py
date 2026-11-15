"""Generates the Overtime app icon: a master PNG, the window icon PNG and a multi-size Windows ICO.

Styled as a sibling of the Overclock icon (a bumper plate): dark rim with stopwatch ticks,
blue band with OVERTIME set around it and TV on the sides, light hub with a play button.

Run from the repo root:  python tools/make_icon.py
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ICON_DIR = ROOT / "composeApp" / "icons"
RES_DIR = ROOT / "composeApp" / "src" / "jvmMain" / "resources"

FONTS = [r"C:\Windows\Fonts\ariblk.ttf", r"C:\Windows\Fonts\arialbd.ttf"]

RIM = (20, 22, 27)
TICK_MINOR = (78, 85, 98)
TICK_MAJOR = (150, 157, 170)
BAND = (31, 79, 168)
BAND_EDGE = (232, 236, 242)
HUB = (206, 209, 215)
HUB_RING = (148, 153, 162)
TEXT = (255, 255, 255)

BASE = 1024  # design units
SS = 4  # supersampling factor for smooth edges

ICO_SIZES = [16, 20, 24, 32, 40, 48, 64, 96, 128, 256]
SIMPLE_MAX = 48  # sizes up to this drop text and ticks, which turn to mush when tiny


def load_font(size):
    for path in FONTS:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    sys.exit("No bold system font found")


def cap_height(font):
    return -font.getbbox("H", anchor="ls")[1]


def circle(draw, c, r, fill):
    draw.ellipse([c - r, c - r, c + r, c + r], fill=fill)


def arc_text(img, text, font, c, radius, mid_deg, tracking):
    """Sets text around a circle, glyph tops facing outward, reading clockwise, centred on mid_deg (0 = 12 o'clock)."""
    widths = [font.getlength(ch) for ch in text]
    total = sum(widths) + tracking * (len(text) - 1)
    angle = mid_deg - math.degrees(total / radius) / 2
    cap = cap_height(font)
    tile = int(font.size * 2)
    for ch, w in zip(text, widths):
        a = angle + math.degrees(w / 2 / radius)
        glyph = Image.new("L", (tile, tile), 0)
        ImageDraw.Draw(glyph).text((tile / 2, tile / 2 + cap / 2), ch, font=font, fill=255, anchor="ms")
        glyph = glyph.rotate(-a, resample=Image.BICUBIC)
        x = c + radius * math.sin(math.radians(a)) - tile / 2
        y = c - radius * math.cos(math.radians(a)) - tile / 2
        img.paste(Image.new("RGBA", (tile, tile), TEXT + (255,)), (round(x), round(y)), glyph)
        angle += math.degrees((w + tracking) / radius)


def render(detailed):
    s = BASE * SS
    c = s / 2
    u = lambda v: v * SS  # design units -> pixels
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    circle(d, c, u(500), RIM)

    if detailed:
        for i in range(60):
            a = math.radians(i * 6)
            major = i % 5 == 0
            r1, r2 = (u(446), u(488)) if major else (u(466), u(488))
            d.line(
                [(c + r1 * math.sin(a), c - r1 * math.cos(a)), (c + r2 * math.sin(a), c - r2 * math.cos(a))],
                fill=TICK_MAJOR if major else TICK_MINOR,
                width=round(u(11 if major else 5)),
            )

    if detailed:
        circle(d, c, u(410), BAND)
        circle(d, c, u(232), BAND_EDGE)
        circle(d, c, u(222), HUB)
        d.ellipse([c - u(160), c - u(160), c + u(160), c + u(160)], outline=HUB_RING, width=round(u(12)))
        r = u(118)
    else:
        # Small sizes: thin rim, big hub and play button so the shape survives at 16 px.
        circle(d, c, u(452), BAND)
        circle(d, c, u(300), HUB)
        r = u(190)

    # Play button, centred on its centroid.
    pts = [(c + r * math.cos(math.radians(t)), c + r * math.sin(math.radians(t))) for t in (0, 120, 240)]
    d.polygon(pts, fill=RIM)

    if detailed:
        font = load_font(round(u(84)))
        text_r = u(321)
        arc_text(img, "OVERTIME", font, c, text_r, 0, u(10))
        arc_text(img, "OVERTIME", font, c, text_r, 180, u(10))
        side = load_font(round(u(64)))
        cap = cap_height(side)
        d.text((c - text_r, c + cap / 2), "TV", font=side, fill=TEXT, anchor="ms")
        d.text((c + text_r, c + cap / 2), "TV", font=side, fill=TEXT, anchor="ms")

    return img


def main():
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    RES_DIR.mkdir(parents=True, exist_ok=True)

    detailed = render(True)
    simple = render(False)

    def at(size):
        source = simple if size <= SIMPLE_MAX else detailed
        return source.resize((size, size), Image.LANCZOS)

    at(1024).save(ICON_DIR / "overtime.png")
    at(256).save(RES_DIR / "overtime_icon.png")

    images = [at(size) for size in ICO_SIZES]
    largest = images[-1]
    largest.save(
        ICON_DIR / "overtime.ico",
        sizes=[(size, size) for size in ICO_SIZES],
        append_images=images[:-1],
    )
    print("Wrote", ICON_DIR / "overtime.png", ICON_DIR / "overtime.ico", RES_DIR / "overtime_icon.png")


if __name__ == "__main__":
    main()
