# ANALYSIS2 — Gate lateral insuficiente: el marco muerde esquinas en máscara circular (2026-11-10)

## Diagnóstico confirmado (no re-investigar)

El launcher del Nothing enmascara en círculo/teardrop y muerde las ESQUINAS del
marco azul. El gate anterior (margen lateral ≥18dp, `ANALYSIS.md` / `DESIGN.md`
S1–S2, `k = 166/186 ≈ 0.892`) da **falso verde**: el margen mide aristas
(izquierda/derecha del bbox), pero la máscara circular recorta por radio desde
el centro del lienzo de 108dp.

- Círculo seguro: Ø66dp → **radio 33dp** = `33·256/108 = 78.22u` (u = unidad de
  diseño sobre base 256; `1u = 108/256dp`).
- Esquina exterior del marco (stroke incluido): el marco es
  `rect x35 y57 w186 h142 rx33` con `stroke width 10` centrado en el path.
  Borde exterior = rect expandido `[30,52,226,204]`, radio exterior `33+5 = 38`,
  centro de arco sup-izq `(68,90)`. Punto diagonal exterior:
  `(68−38/√2, 90−38/√2) = (41.13,63.13)` → distancia al centro `(128,128)`:
  `dx=86.87, dy=64.87` → **108.42u ≈ 109u** (cota conservadora 109u).
- Con `k = 166/186 ≈ 0.892`: esquinas a `108.42·0.892 = 96.7u = 40.8dp` del
  centro, **fuera** del círculo de 33dp → el launcher corta las 4 esquinas.
  El bbox lateral medía ≥18dp porque las aristas sí entran; las esquinas no.
- Evidencia de usuario: el corte cambia con la forma de icono (hipótesis A:
  depende de la máscara) y en Ajustes también se ve cortado (el icono que se
  muestra ahí ya sale de la máscara del launcher).

## Decisión

Re-escalar para que **TODO el marco exterior (stroke incluido) quede dentro
del radio 33dp**, con ~1dp de holgura:

- Cota: `k ≤ 78.22/108.42 = 0.7215` (justo en el borde), `k ≤ 75.85/108.42 =
  0.6996` (a 32dp, 1dp de holgura). Cota conservadora con 109u:
  `k ≤ 78.22/109 = 0.7176` / `k ≤ 75.85/109 = 0.6959`.
- `k = 0.69`: esquinas a `108.42·0.69 = 74.8u = 31.6dp` (con 109u: 31.7dp) →
  **~1.3–1.4dp de holgura** dentro del círculo de 33dp.
- Centrado, uniforme, sin tocar la geometría del monograma: solo cambia la
  escala del grupo en `draw_mark(with_bg=True)` del bucle foreground
  (`compute_safe_box`), ningún path/color/radio/grosor. Legacy, banner,
  watermark y mark quedan byte-idénticos; se regeneran SOLO los 5
  `ic_launcher_foreground.webp`.
- El fondo negro a sangre (`rounded_rectangle 0..256` escalado) sigue
  extendiéndose más allá del círculo, pero es invisible sobre el fondo
  `#000000` del adaptive-icon XML: la máscara no lo ve. El gate radial solo
  mide píxeles visibles (no negros), igual que el gate lateral.
- El gate lateral ≥18dp se mantiene (sigue pasando: márgenes de arista con
  `k=0.69` → laterales `(256−186·0.69)/2 = 63.8u = 26.9dp`); se añade la
  aserción RADIAL como condición necesaria (el fallo real).
