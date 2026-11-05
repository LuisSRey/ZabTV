# Resultados de perf en dispositivo (spec §6.7)

Plantilla por corrida de `sh scripts/perf_check.sh`. Una fila por fecha/dispositivo.
Los benchmarks JVM de dev (Tasks 4/5/6: M3U 10k < 1 s, XMLTV 50k < 10 s,
import 10k ≈ 5 s en Robolectric) son referencia, no criterio: el criterio
vinculante se mide aquí, en hardware real.

| Fecha | Dispositivo (SoC/RAM) | Cold start TotalTime | PSS tras 5 min | Janky frames (60 s) | ANR |
|---|---|---|---|---|---|
| _(ejemplo)_ | Fire TV Stick 3ª gen | — | — | — | 0 |
