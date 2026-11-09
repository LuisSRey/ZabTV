# ANALYSIS — Back / Inmersivo / Watermark (2026-11-09)

Tres frentes independientes sobre la navegación y el player. Causa raíz de cada uno.

## F1 — BACK resucita vistas fusionadas (MainActivity)

`MainActivity.open()` hacía `replace` **sin** `addToBackStack`: la transacción
de tab no entraba en la pila. Pero `openPlayer/openBrowser/openLists/openSettings`
sí hacían `replace + addToBackStack`. Resultado:

- Al navegar Home → Browser → Player y luego pulsar una tab (p. ej. Home),
  `open()` reemplazaba el contenedor **sin purgar la pila**: las entradas
  huérfanas (Browser, Player) seguían en el back stack.
- Al pulsar BACK, el `FragmentManager` hacía pop de una entrada huérfana y
  **resucitaba su vista sobre la pantalla actual**, fusionando ambas
  (p. ej. el Player reaparecía encima de Home).
- Asimetría `goTab`: Home/Search iban por `open()` sin pila mientras
  Listas/Ajustes apilaban con `addToBackStack`, así que el BACK se comportaba
  distinto según la pestaña y los taps repetidos acumulaban entradas.

## F2 — Player sin gestión de insets

`PlayerFragment` no tocaba `WindowInsets`/`WindowInsetsController`: en móvil
apaisado las barras del sistema seguían visibles sobre el vídeo. Además el
manifest declara `configChanges="orientation|screenSize|screenLayout|keyboardHidden"`
en `MainActivity`, que **evita la recreación** al rotar: sin enganche explícito
en `onConfigurationChanged` (ni re-aplicación en `onResume`) cualquier ajuste
de inmersivo aplicado una vez se perdería o no se aplicaría al girar.

## F3 — Watermark con geometría distinta al maestro

`ic_logo_watermark.xml` usaba `viewportWidth/Height=220` con `pathData`
reescalados a mano, mientras el maestro `ic_logo_mark.xml` usa viewport 48
con sus propios paths (verificados diferentes: pastilla y play no coinciden
proporcionalmente — el reescalado manual deforma los radios de la pastilla).
El watermark debe derivar geometría **verbatim** del maestro.
