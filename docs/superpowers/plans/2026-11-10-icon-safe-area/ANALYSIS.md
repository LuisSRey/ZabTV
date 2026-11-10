# ANALYSIS — Foreground adaptativo fuera de la zona segura (2026-11-10)

## Causa raíz

El launcher (Nothing, y cualquier launcher con máscara circle/squircle/teardrop)
recorta el adaptive icon a una máscara inscrita: **todo lo que quede a <18dp del
borde del lienzo de 108dp se corta**. El foreground actual dibuja la marca a
sangre y el marco azul queda fuera de la zona segura.

Cadena causal en `tools/gen_launcher_icon.py` (líneas 93-102):

- El bucle de foreground crea un lienzo transparente de `px` (108/162/216/324/432
  según densidad; los 5 son 108dp) y llama `draw_mark(d, 0, 0, px/256, with_bg=True)`.
- Ese `(0, 0, px/256)` mapea el espacio de diseño 0..256 al lienzo completo:
  el marco TV (`x35..221`, `draw_mark` línea 67) cae en
  `35/256*108 = 14.7656dp` del borde izquierdo/derecho (medido en assets:
  14.0–15.0dp según redondeo por densidad; arriba/abajo `57/256*108 = 24.05dp`, OK).
- El fondo negro de `with_bg=True` (`rounded_rectangle 0..256`) va a sangre:
  bbox alfa == lienzo completo (márgenes 0dp). Es invisible sobre el
  `<background android:drawable="@color/tp_bg" />` (`#000000`, `colors.xml`),
  pero no aporta ningún inset: lo que se corta es el marco, no el fondo.

Medición actual (bbox de píxeles visibles = opacos y no negros, que es lo que el
ojo/la máscara ven; el negro se funde con el fondo del adaptive-icon):

| densidad | lienzo | margen L | margen T | margen R | margen B | mín |
|---|---|---|---|---|---|---|
| mdpi | 108 | 15.00dp | 24.00dp | 14.00dp | 23.00dp | **14.00** |
| hdpi | 162 | 14.67dp | 24.00dp | 14.00dp | 23.33dp | **14.00** |
| xhdpi | 216 | 15.00dp | 24.00dp | 14.50dp | 23.50dp | **14.50** |
| xxhdpi | 324 | 14.67dp | 24.00dp | 14.33dp | 23.67dp | **14.33** |
| xxxhdpi | 432 | 14.75dp | 24.00dp | 14.50dp | 23.75dp | **14.50** |

Teoría: `35*108/256 = 14.7656dp < 18dp` (laterales); vertical `57*108/256 =
24.05dp` (pasa). La máscara del launcher muerde ~3.2dp del marco en cada lado →
el marco azul aparece cortado en el Nothing.

Nota de rasterización (2026-11-10, Pillow 12.3.0): el `outline` con ancho par
pinta +1px en los bordes trailing (derecha/abajo). El `k = 170/186` ideal
(marco a 43u = 18.14dp) dejaba el margen derecho rasterizado en ~17.1dp en 4
densidades; por eso el fix usa `k = 166/186` (marco a 45u = 19.0dp de rect,
bbox visible medido ≥18dp). Barrido empírico por densidad en el ledger del
commit.

## Ficheros y valores

- `tools/gen_launcher_icon.py`: `draw_mark(d, ox, oy, s)` + bucle foreground
  `FG_SIZES` (108/162/216/324/432) con `draw_mark(d, 0, 0, px/256, with_bg=True)`.
- Assets afectados (SOLO estos 5): `app/src/main/res/mipmap-*/ic_launcher_foreground.webp`.
- Intocables: `ic_launcher.webp`, `ic_launcher_round.webp` (legacy),
  `drawable-*/banner.webp`, watermark, `ic_logo_mark` — otro bucle/otro fichero.
- Geometría del monograma intocable salvo padding: el fix es solo escala
  uniforme + centrado (padding), ningún path, color, radio ni grosor cambia.
- `app/src/main/res/mipmap-anydpi-v29/ic_launcher.xml`: fondo `@color/tp_bg`
  (`#000000`) + foreground `@mipmap/ic_launcher_foreground` (no cambia).
- `app/build.gradle.kts`: `versionCode = 77` (se sube a 78: el launcher
  cachea iconos y solo invalida con cambio de versión).

## Restricción de plataforma

`minSdk 23`: sin `adaptive-icon` con `monochrome` ni `themedIcon` (API 33);
el foreground debe llevar su propio negro y la zona segura se garantiza con
geometría (escala + centrado), no con XML.
