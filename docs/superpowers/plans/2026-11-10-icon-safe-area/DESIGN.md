# DESIGN — Foreground adaptativo en zona segura (2026-11-10)

## [SECCION APROBADA] S1 — Test RED: `tools/verify_icon_safe_area.py`

Script python autocontenido (PIL, ya dependencia del generador):

- Para cada `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.webp`:
  bbox de píxeles **visibles** (alfa>128 y no negros puros — el negro se funde
  con el fondo `#000000` del adaptive-icon y no lo ve la máscara) y márgenes
  L/TRB convertidos a dp (`px / (lienzo/108)`).
- Aserta margen ≥18dp en los 4 lados, 5 ficheros. En rojo actual: laterales
  ~14.0–15.0dp (teoría 14.7656dp) → FAIL.
- Informa además el bbox alfa total (incluye el negro a sangre: 0dp) como
  diagnóstico, sin asetarlo: el negro es redundante con el fondo XML.
- Si `gen_launcher_icon` expone `compute_safe_box()` (S2), el script la importa
  y comprueba consistencia geometría↔assets (import opcional con `try/except`
  para que el RED corra antes del refactor).

## [SECCION APROBADA] S2 — Fix: `SAFE_MARGIN_DP = 18.0` + `compute_safe_box()`

En `tools/gen_launcher_icon.py`, sin tocar legacy/banner:

- Constante `SAFE_MARGIN_DP = 18.0` y función pura `compute_safe_box(px)` →
  `(ox, oy, s)`: escala uniforme `k = 166/186 ≈ 0.892` sobre el centro del
  diseño (128,128), i.e. `s = (px/256)*k`, `ox = oy = (px - 256*s)/2`.
  - El marco (ancho 186u: `x35..221`) queda en 166u → márgenes de rect
    `(256-166)/2 = 45u = 19.0dp` (bbox visible medido 18.0–19.5dp por
    redondeo a píxel). Vertical: marco `y57..199` → 63.9u = 27.0dp.
    Todo ≥18dp.
  - Por qué no 170/186 (≈0.914, 43u = 18.14dp): el `outline` de Pillow con
    ancho par pinta +1px en los bordes trailing (derecha/abajo) y el margen
    derecho rasterizado caía a ~17.1dp en 4 de 5 densidades (barrido
    empírico, Pillow 12.3.0). 166/186 es el mayor `k` que deja el bbox
    visible medido en ≥18dp en las 5 densidades. Escala + centrado intactos
    (solo padding).
  - Geometría intacta: la misma `k` en X e Y para fondo, marco, T, v, P, play
    y grosor de trazo (`10*s` ya escala solo); `with_bg=False` usa idéntico
    `(ox, oy, s)` (mismo posicionamiento, sin negro).
- `draw_mark` no cambia de firma ni de geometría: el bucle foreground pasa
  `(ox, oy, s) = compute_safe_box(px)` en vez de `(0, 0, px/256)`.
  `with_bg=False` con el mismo `(ox, oy, s)` da idéntico posicionamiento
  (sin negro). Legacy (`SIZES`) y banner siguen con sus args actuales →
  byte-idénticos.
- Generación CLI bajo `if __name__ == "__main__":` (mismo comportamiento;
  permite importar `compute_safe_box`/`draw_mark` sin efectos laterales).
- Regenerar **SOLO** los 5 foreground (bucle `FG_SIZES` con `compute_safe_box`);
  verificar con `git status` que legacy/banner/watermark/mark no cambian.

## [SECCION APROBADA] S3 — versionCode +1, suite, commit

- `app/build.gradle.kts`: `versionCode` 77 → 78 (invalida cachés del launcher).
- `tools/verify_icon_safe_area.py`: PASS (≥18dp 4 lados, 5 ficheros) + overlay
  visual de máscaras (círculo inscrito a 18dp) para inspección.
- Suite JVM completa verde (base 130) + `assembleDebug` + `assembleRelease`.
- Commit único `fix: foreground adaptativo en zona segura`.
