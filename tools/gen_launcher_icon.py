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
    # WebP sin perdida: AAPT no lo recuantiza (el crunch pasaba el PNG a
    # paleta y Fire OS 7 no lo cargaba) y va en el mismo R sin tocar XMLs.
    img.save(f"{out_root}/{name}/ic_launcher.webp", format="WEBP", lossless=True)
    # Round legacy: misma composicion con mascara circular.
    mask = Image.new("L", (px, px), 0)
    md = ImageDraw.Draw(mask)
    md.ellipse([0, 0, px, px], fill=255)
    rounded = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    rounded.paste(img, (0, 0), mask)
    rounded.save(f"{out_root}/{name}/ic_launcher_round.webp", format="WEBP", lossless=True)
    print(name, px)

# Foreground adaptativo en PNG (algunos launchers fallan con vectores con arcos):
# pastilla + play centrados sobre transparente; el fondo lo pone el adaptive-icon.
FG_SIZES = {"mipmap-mdpi": 108, "mipmap-hdpi": 162, "mipmap-xhdpi": 216,
            "mipmap-xxhdpi": 324, "mipmap-xxxhdpi": 432}
for name, px in FG_SIZES.items():
    fg = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    m = px * 0.25
    r = px * 0.16
    d.rounded_rectangle([m, m, px - m, px - m], radius=r, fill=BLUE)
    cx, cy = px / 2, px / 2
    s = px * 0.15
    d.polygon([(cx - s * 0.6, cy - s), (cx - s * 0.6, cy + s),
               (cx + s * 0.9, cy)], fill=WHITE)
    fg.save(f"{out_root}/{name}/ic_launcher_foreground.webp", format="WEBP", lossless=True)
    print("fg", name, px)
# Banner TV en 5 densidades (como los testigos que si se ven: RGB sin
# cuantizar; el crunch de AAPT lo pasaba a paleta y Fire OS 7 no lo carga).
# Medidas proporcionales: mdpi 160x90, hdpi 240x135, xhdpi 320x180,
# xxhdpi 480x270, xxxhdpi 640x360.
for name, (W, H) in {"drawable-mdpi": (160, 90), "drawable-hdpi": (240, 135),
                     "drawable-xhdpi": (320, 180), "drawable-xxhdpi": (480, 270),
                     "drawable-xxxhdpi": (640, 360)}.items():
    banner = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(banner)
    s = int(H * 76 / 180)
    m = (W - s) // 2
    r = max(4, int(H * 18 / 180))
    d.rounded_rectangle([m, (H - s) // 2, m + s, (H + s) // 2], radius=r, fill=BLUE[:3])
    cx, cy = W / 2, H / 2
    t = H * 21 / 180
    d.polygon([(cx - t * 0.6, cy - t), (cx - t * 0.6, cy + t),
               (cx + t * 0.9, cy)], fill=WHITE[:3])
    os.makedirs(f"{out_root}/{name}", exist_ok=True)
    banner.save(f"{out_root}/{name}/banner.webp", format="WEBP", lossless=True)
    print("banner", name, W, H)
