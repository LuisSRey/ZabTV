# DESIGN — Back / Inmersivo / Watermark (2026-11-09)

## [SECCION APROBADA] S1 — Navegación: tabs sin pila, flow con máx 1 entrada

- `openTab(fragment, tag)`: purga la pila con
  `popBackStackImmediate(null, POP_BACK_STACK_INCLUSIVE)` + `replace` **sin**
  apilar. BACK en raíz sale de la app.
- `pushFlow(fragment, tag)`: si hay ≥1 entrada hace pop previo (máx 1 entrada)
  + `replace` con `addToBackStack`. BACK en flow hace pop a la tab origen.
- `open()` queda `@Deprecated` y delega en `openTab` (compatibilidad).
- `openPlayer/openBrowser` → `pushFlow`; `openLists/openSettings` → `openTab`.
- Pestañas Explorar/Buscar (`openBrowserFirst/openSearchFirst`) usan `openTab`
  **directo** (no `openBrowser`, que es `pushFlow` para drill-in): la tab nunca
  apila (F1.2). `openBrowser(listId)` sigue siendo `pushFlow` para drill-in
  desde Home/Listas/"abrir con" (BACK vuelve al origen).
- Invariantes: **I1** tabs jamás apilan · **I2** ≤1 entrada flow.

## [SECCION APROBADA] S2 — Inmersivo horizontal solo en móvil

- Solo si `!isTv && orientation == LANDSCAPE` (`FocusScaleHelper.isTv` +
  `resources.configuration.orientation`), predicado puro `isImmersiveCandidate()`
  testeable en JVM.
- Aplicar con `WindowInsetsControllerCompat`: `hide(systemBars())` +
  `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. Enganche en
  `onViewCreated` + `onResume` + `onConfigurationChanged` (el manifest evita
  recreación); restaurar con `show(systemBars())` en portrait, `onPause` y
  `onDestroyView`.
- `resize_mode="fit"` intacto en ambos layouts; `layout-television` intacto.

## [SECCION APROBADA] S3 — Watermark deriva del logo maestro

- Reescribir `ic_logo_watermark.xml` con `viewportWidth/Height=48` y `pathData`
  **verbatim** de `ic_logo_mark.xml`. Deltas: `width/height=220dp`,
  pastilla `#330A84FF` (azul interfaz con alfa horneada), play `#33FFFFFF`,
  comentario `<!-- Derivado de ... -->`. Sin test (visual).
