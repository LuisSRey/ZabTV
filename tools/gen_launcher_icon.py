#!/usr/bin/env python3
"""Icono legacy de TVPlayer (API 23-25; en 26+ manda el adaptive icon).
Fondo negro + pastilla azul + triángulo play blanco.Determinista."""
from PIL import Image, ImageDraw

BG = (10, 10, 12, 255)
BLUE = (10, 132, 255, 255)
WHITE = (255, 255, 255, 255)

SIZES = {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96,
         "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}

import sys
out_root = sys.argv[1]

for name, px in SIZES.items():
    img = Image.new("RGBA", (px, px), BG)
    d = ImageDraw.Draw(img)
    m = px * 0.25
    r = px * 0.16
    d.rounded_rectangle([m, m, px - m, px - m], radius=r, fill=BLUE)
    cx, cy = px / 2, px / 2
    s = px * 0.15
    # triángulo play centrado ópticamente (ligeramente a la derecha)
    d.polygon([(cx - s * 0.6, cy - s), (cx - s * 0.6, cy + s),
               (cx + s * 0.9, cy)], fill=WHITE)
    import os
    os.makedirs(f"{out_root}/{name}", exist_ok=True)
    img.save(f"{out_root}/{name}/ic_launcher.png")
    # Round legacy: misma composicion con mascara circular.
    mask = Image.new("L", (px, px), 0)
    md = ImageDraw.Draw(mask)
    md.ellipse([0, 0, px, px], fill=255)
    rounded = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    rounded.paste(img, (0, 0), mask)
    rounded.save(f"{out_root}/{name}/ic_launcher_round.png")
    print(name, px)
