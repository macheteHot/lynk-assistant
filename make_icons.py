#!/usr/bin/env python3
"""Generate launcher icons (blue rounded square + white gear) as PNG mipmaps.

Only needs Pillow:  pip install pillow
Usage:              python3 make_icons.py
"""
import math
import os

from PIL import Image, ImageDraw

BG = (21, 101, 192, 255)      # #1565C0
FG = (255, 255, 255, 255)

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

SS = 4  # supersample factor
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "res")


def rounded_square(size, radius_ratio, fill):
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle(
        [0, 0, s - 1, s - 1], radius=int(s * radius_ratio), fill=fill)
    return img


def gear(size):
    """White gear: 8 flat-top teeth, hub hole punched out."""
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = s / 2.0

    root = s * 0.250        # valley radius
    tip = s * 0.350         # tooth tip radius
    teeth = 8
    step = 360.0 / teeth
    w_tooth = 11.0          # half angular width of a tooth
    w_gap = 9.0             # half angular width of a valley

    def pt(r, deg):
        a = math.radians(deg)
        return (cx + r * math.cos(a), cy + r * math.sin(a))

    pts = []
    for i in range(teeth):
        c = i * step - 90.0
        pts.append(pt(tip, c - w_tooth))
        pts.append(pt(tip, c + w_tooth))
        g = c + step / 2.0
        pts.append(pt(root, g - w_gap))
        pts.append(pt(root, g + w_gap))

    d.polygon(pts, fill=FG)
    hole = s * 0.100
    d.ellipse([cx - hole, cy - hole, cx + hole, cy + hole], fill=(0, 0, 0, 0))
    return img


def build(size):
    img = rounded_square(size, 0.22, BG)
    img.alpha_composite(gear(size))
    return img.resize((size, size), Image.LANCZOS)


def main():
    for density, size in DENSITIES.items():
        out_dir = os.path.join(RES, "mipmap-" + density)
        os.makedirs(out_dir, exist_ok=True)
        path = os.path.join(out_dir, "ic_launcher.png")
        build(size).save(path, "PNG", optimize=True)
        print("wrote", os.path.relpath(path, HERE), f"{size}x{size}")


if __name__ == "__main__":
    main()
