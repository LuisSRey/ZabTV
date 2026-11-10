#!/usr/bin/env python3
"""Gate de zona segura del foreground adaptativo (S1/S2, 2026-11-10).

Mide, en los 5 ic_launcher_foreground.webp, el bbox de pixeles VISIBLES
(alfa>128 y no negros puros: el negro se funde con el fondo #000000 del
adaptive-icon XML y la mascara del launcher no lo ve) y aserta margen >=18dp
en los 4 lados. El marco azul es el contenido critico: a <18dp la mascara
(circle/squircle) del launcher lo corta (Nothing).

Uso: python tools/verify_icon_safe_area.py [--overlay out.png]
Ver DESIGN.md S1. Exit 0 = PASS, 1 = FAIL.
"""
import os
import sys

from PIL import Image, ImageDraw

SAFE_DP = 18.0
FG = (("mipmap-mdpi", 108), ("mipmap-hdpi", 162), ("mipmap-xhdpi", 216),
      ("mipmap-xxhdpi", 324), ("mipmap-xxxhdpi", 432))
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def measure(path):
    """Devuelve (margenes_dp LTRB del bbox visible, margenes_dp LTRB alfa total)."""
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    assert w == h, path
    d = w / 108.0  # px por dp (todos los lienzos son 108dp)
    px = im.load()
    l, t, r, b = w, h, -1, -1  # visible (no transparente, no negro)
    al, at, ar, ab = w, h, -1, -1  # alfa total (diagnostico)
    for y in range(h):
        for x in range(w):
            R, G, B, A = px[x, y]
            if A > 128:
                if x < al:
                    al = x
                if y < at:
                    at = y
                if x > ar:
                    ar = x
                if y > ab:
                    ab = y
                if not (R == 0 and G == 0 and B == 0):
                    if x < l:
                        l = x
                    if y < t:
                        t = y
                    if x > r:
                        r = x
                    if y > b:
                        b = y
    vis = tuple(round(v / d, 2) for v in (l, t, w - 1 - r, h - 1 - b))
    alpha = tuple(round(v / d, 2) for v in (al, at, w - 1 - ar, h - 1 - ab))
    return vis, alpha


def main():
    ok = True
    print("foreground safe-area (>=18dp en LTRB, bbox visible):")
    for name, px in FG:
        p = os.path.join(REPO, "app", "src", "main", "res", name,
                         "ic_launcher_foreground.webp")
        vis, alpha = measure(p)
        good = all(m >= SAFE_DP for m in vis)
        ok &= good
        print(f"  {name:14s} {px:3d}px vis LTRB(dp)={vis} "
              f"alfa-total LTRB(dp)={alpha} -> {'PASS' if good else 'FAIL'}")
    # Consistencia con la geometria pura del generador (solo existe tras S2).
    try:
        sys.path.insert(0, os.path.join(REPO, "tools"))
        from gen_launcher_icon import compute_safe_box
        for _, px in FG:
            ox, oy, s = compute_safe_box(px)
            # El marco x35..221 debe quedar a >=18dp del borde.
            ml = (ox + 35 * s) / (px / 108.0)
            mr = (px - (ox + 221 * s)) / (px / 108.0)
            mt = (oy + 57 * s) / (px / 108.0)
            mb = (px - (oy + 199 * s)) / (px / 108.0)
            good = all(m >= SAFE_DP for m in (ml, mr, mt, mb))
            ok &= good
            print(f"  compute_safe_box({px}): marco LR TB(dp)="
                  f"{tuple(round(m, 2) for m in (ml, mr, mt, mb))} "
                  f"-> {'PASS' if good else 'FAIL'}")
    except Exception as e:  # noqa: BLE001 - pre-S2 no existe; solo diagnostico
        print(f"  (sin chequeo compute_safe_box: {type(e).__name__})")
    if "--overlay" in sys.argv:
        i = sys.argv.index("--overlay")
        out = sys.argv[i + 1]
        src = os.path.join(REPO, "app", "src", "main", "res",
                           "mipmap-xxxhdpi", "ic_launcher_foreground.webp")
        im = Image.open(src).convert("RGBA")
        ov = Image.new("RGBA", im.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(ov)
        m = 18.0 * (im.size[0] / 108.0)  # 18dp en px de la densidad
        d.ellipse([m, m, im.size[0] - m, im.size[1] - m],
                  outline=(255, 0, 0, 255), width=max(2, im.size[0] // 200))
        d.rectangle([m, m, im.size[0] - m, im.size[1] - m],
                    outline=(255, 255, 0, 255), width=max(2, im.size[0] // 300))
        Image.alpha_composite(im, ov).save(out)
        print("  overlay:", out)
    print("SAFE-AREA:", "TODO PASS" if ok else "HAY FALLOS")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
