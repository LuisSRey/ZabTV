#!/usr/bin/env python3
"""Monograma TvP de ZabTV como logo unico (legacy + foreground + banner).

Spec (Brand Studio, viewBox 0 0 256 256):
- Fondo: rect 256x256 rx64 fill #000000.
- Marco TV: rect x35 y57 w186 h142 rx33, fill none, stroke #0A84FF width 10.
- T: barra (59,88)-(126,108) + fuste (81,108)-(104,171), fill #98989D.
- v: poligono (108,126),(126,126),(138,150),(150,126),(168,126),
  (146,169),(130,169), fill #98989D.
- P: contorno exterior muestreado de las cubicas del path + hueco
  interior pintado en negro encima, fill #0A84FF.
- Play: triangulo (168,112),(184,122),(168,132) fill negro (recorta la P).

Determinista. Todo en WebP lossless (AAPT no lo recuantiza; el PNG
paletizado rompio Fire OS 7).
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFont

BLACK = (0, 0, 0)
BLUE = (10, 132, 255)
GRAY = (152, 152, 157)
WHITE = (255, 255, 255)

# Margen minimo de la zona segura del adaptive icon (2026-11-10): la mascara
# del launcher recorta todo lo que quede a menos de 18dp del borde del lienzo
# de 108dp. El marco azul a sangre quedaba a 35/256*108 = 14.76dp y el Nothing
# lo cortaba.
# ANALYSIS2 (2026-11-10): el gate lateral no basta. La mascara circle/teardrop
# recorta por RADIO desde el centro: todo el marco exterior (stroke incluido)
# debe quedar dentro del circulo seguro de radio 33dp (78.22u). Las esquinas
# exteriores del marco estan a 108.42u del centro (cota 109u), asi que
# k <= 78.22/109 = 0.7176, menos ~1dp de holgura -> k = 0.69 (esquinas a
# 31.6-31.7dp, ~1.3dp de holgura).
SAFE_MARGIN_DP = 18.0
SAFE_RADIUS_DP = 33.0

# Tamanos por densidad (datos puros; los bucles que generan viven en __main__).
SIZES = {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96,
         "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}
FG_SIZES = {"mipmap-mdpi": 108, "mipmap-hdpi": 162, "mipmap-xhdpi": 216,
            "mipmap-xxhdpi": 324, "mipmap-xxxhdpi": 432}
BANNERS = {"drawable-mdpi": (160, 90), "drawable-hdpi": (240, 135),
           "drawable-xhdpi": (320, 180), "drawable-xxhdpi": (480, 270),
           "drawable-xxxhdpi": (640, 360)}


def compute_safe_box(px):
    """(ox, oy, s) puros para dibujar el grupo completo dentro del circulo seguro.

    Escala uniforme k = 0.69 sobre el centro del diseno (128,128):
    - Radial (ANALYSIS2, vinculante): la esquina exterior del marco (rect
      x35..221 y57..199 rx33 + stroke 10 centrado -> borde exterior
      [30,52,226,204] r38, arco sup-izq centro (68,90) -> 108.42u del centro,
      cota 109u) queda a 109*0.69 = 75.2u = 31.7dp del centro, ~1.3dp de
      holgura dentro del circulo seguro de radio 33dp (78.22u).
      k <= 78.22/109 = 0.7176 (al borde); k <= 75.85/109 = 0.6959 (a 32dp).
    - Lateral (se mantiene): el marco (ancho 186u) queda en 128.3u ->
      margenes de rect (256-128.3)/2 = 63.8u = 26.9dp en L/R (>=18dp);
      vertical 63.8u+ -> ~27dp en T/B. El gate lateral sigue pasando.
    El k anterior (166/186 ~= 0.892) daba falso verde: laterales >=18dp pero
    esquinas a ~40.8dp del centro, fuera del circulo de 33dp (el Nothing las
    mordia). Vale para with_bg=True y with_bg=False (identico
    posicionamiento). Solo lo usa el bucle foreground; legacy/banner usan sus
    args actuales y quedan byte-identicos.
    """
    k = 0.69
    s = (px / 256.0) * k
    o = (px - 256.0 * s) / 2.0
    return o, o, s


def cubic(p0, p1, p2, p3, n=24):
    pts = []
    for i in range(n + 1):
        t = i / n
        mt = 1 - t
        x = mt**3 * p0[0] + 3 * mt * mt * t * p1[0] + 3 * mt * t * t * p2[0] + t**3 * p3[0]
        y = mt**3 * p0[1] + 3 * mt * mt * t * p1[1] + 3 * mt * t * t * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


# P exterior: M145 88 h24 [cubica a (206,121.5)] [cubica a (169,156)]
# h-1 v15 h-23 V88 Z
_P_SEG1 = cubic((169, 88), (192, 88), (206, 100), (206, 121.5))
_P_SEG2 = cubic((206, 121.5), (206, 143.5), (192, 156), (169, 156))
P_OUTER = [(145, 88)] + _P_SEG1[1:] + _P_SEG2[1:] + [(168, 156), (168, 171), (145, 171)]

# P hueco: m23 20 (=168,108) v28 h2 [cubica a (184,121.8)]
# [cubica a (170,108)] h-2 Z
_P_SEG3 = cubic((170, 136), (179, 136), (184, 131), (184, 121.8))
_P_SEG4 = cubic((184, 121.8), (184, 112.8), (179, 108), (170, 108))
P_INNER = [(168, 108), (168, 136)] + _P_SEG3[1:] + _P_SEG4[1:] + [(168, 108)]

V_POLY = [(108, 126), (126, 126), (138, 150), (150, 126), (168, 126), (146, 169), (130, 169)]
PLAY_POLY = [(168, 112), (184, 122), (168, 132)]


def draw_mark(d, ox, oy, s, with_bg=True):
    """Dibuja el monograma. (ox,oy)=esquina sup-izq en px, s=px por unidad."""
    def X(x):
        return ox + x * s

    def Y(y):
        return oy + y * s

    if with_bg:
        d.rounded_rectangle([X(0), Y(0), X(256), Y(256)], radius=64 * s, fill=BLACK)
    w = max(1, int(round(10 * s)))
    d.rounded_rectangle([X(35), Y(57), X(221), Y(199)], radius=33 * s,
                        outline=BLUE, width=w)
    d.rectangle([X(59), Y(88), X(126), Y(108)], fill=GRAY)
    d.rectangle([X(81), Y(108), X(104), Y(171)], fill=GRAY)
    d.polygon([(X(x), Y(y)) for x, y in V_POLY], fill=GRAY)
    d.polygon([(X(x), Y(y)) for x, y in P_OUTER], fill=BLUE)
    d.polygon([(X(x), Y(y)) for x, y in P_INNER], fill=BLACK)
    d.polygon([(X(x), Y(y)) for x, y in PLAY_POLY], fill=BLACK)


# Generacion CLI: python tools/gen_launcher_icon.py <out_root>
# (bajo main guard para que compute_safe_box/draw_mark sean importables
# sin efectos laterales; el comportamiento CLI no cambia).
if __name__ == "__main__":
    out_root = sys.argv[1]

    # Icono legacy cuadrado (API 23-25; en 26+ manda el adaptive icon).
    for name, px in SIZES.items():
        img = Image.new("RGBA", (px, px), BLACK + (255,))
        d = ImageDraw.Draw(img)
        draw_mark(d, 0, 0, px / 256, with_bg=True)
        os.makedirs(f"{out_root}/{name}", exist_ok=True)
        img.save(f"{out_root}/{name}/ic_launcher.webp", format="WEBP", lossless=True)
        mask = Image.new("L", (px, px), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, px, px], fill=255)
        rounded = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        rounded.paste(img, (0, 0), mask)
        rounded.save(f"{out_root}/{name}/ic_launcher_round.webp", format="WEBP", lossless=True)
        print(name, px)

    # Foreground adaptativo: grupo completo (fondo+marco+TvP) escalado
    # uniformemente dentro de la caja segura de 72dp (ver compute_safe_box).
    # El fondo negro del XML del adaptive-icon sigue aportando el negro base.
    for name, px in FG_SIZES.items():
        fg = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        d = ImageDraw.Draw(fg)
        draw_mark(d, *compute_safe_box(px), with_bg=True)
        fg.save(f"{out_root}/{name}/ic_launcher_foreground.webp", format="WEBP", lossless=True)
        print("fg", name, px)

    # Banner TV en 5 densidades: fondo negro + marca a la izquierda +
    # texto "ZabTV" blanco en negrita (Inter empaquetada). Si la fuente
    # falla, marca centrada sin texto.
    font_dir = os.path.join(out_root, "font")
    print("fonts:", sorted(os.listdir(font_dir)) if os.path.isdir(font_dir) else "SIN DIR FONT")
    for name, (W, H) in BANNERS.items():
        banner = Image.new("RGB", (W, H), BLACK)
        d = ImageDraw.Draw(banner)
        try:
            fp = os.path.join(font_dir, "inter_bold.otf")
            size = int(H * 0.34)
            font = ImageFont.truetype(fp, size)
            side = int(H * 0.72)
            ox, oy = int(H * 0.10), (H - side) // 2
            draw_mark(d, ox, oy, side / 256, with_bg=True)
            gap = int(H * 0.08)
            avail = W - (ox + side + gap) - int(H * 0.06)
            while font.getbbox("ZabTV")[2] > avail and size > 8:
                size -= 1
                font = ImageFont.truetype(fp, size)
            d.text((ox + side + gap, H / 2), "ZabTV", font=font,
                   fill=WHITE, anchor="lm")
            print("banner", name, W, H, "font_size", size)
        except Exception as e:
            print("banner", name, W, H, "SIN FUENTE:", e, "-> marca centrada")
            side = int(H * 0.76)
            draw_mark(d, (W - side) // 2, (H - side) // 2, side / 256, with_bg=True)
        os.makedirs(f"{out_root}/{name}", exist_ok=True)
        banner.save(f"{out_root}/{name}/banner.webp", format="WEBP", lossless=True)
