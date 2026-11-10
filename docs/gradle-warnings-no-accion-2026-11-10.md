# Warnings Gradle — DOC de no-acción (2026-11-10)

Docs-only. No se toca toolchain ni código app: sin bumps (Gradle/AGP/KSP/Room/Paging/WorkManager/Coil/OkHttp),
sin cambios de flags, `configuration-cache=true` intacto.

## S1 — `Configuration.setVisible(boolean)`: no-acción ([SECCION APROBADA] en DESIGN)

Texto literal (Gradle 9.8.2, AGP 9.5.0), emitido en `> Configure project :app`:

```text
The Configuration.setVisible(boolean) method has been deprecated. This is scheduled to be removed
in Gradle 11. Consult the upgrading guide for further information:
https://docs.gradle.org/9.8.2/userguide/upgrading_version_9.html#deprecate-visible-property
```

Reproducir (solo lectura, no modifica nada):

```powershell
.\gradlew.bat :app:help --warning-mode all --console=plain
```

Origen (evidencia, stacktrace vía `-Dorg.gradle.deprecation.trace=true`):

```text
DefaultConfiguration.setVisible
← DefaultKotlinBasePlugin.apply (KotlinPluginWrapper.kt:87)
← BuiltInKotlinServicesKt.initBuiltInKotlinSupport (BuiltInKotlinServices.kt:219)  # soporte Kotlin EMBEBIDO en AGP
← BasePlugin.configureProject (BasePlugin.kt:356)
← AppPlugin.apply (AppPlugin.kt:26)  # = plugin com.android.application
```

- Cero frames de scripts propios en la cadena; `grep setVisible|visible` sobre
  `*.gradle.kts` + `gradle.properties` + `settings.gradle.kts` = **0 matches**.
- El caller es AGP 9.5.0 (su `BasePlugin`/`AppPlugin` y su Kotlin embebido), no nuestro código.
  No existe flag ni refactor propio que lo silencie: el fix es subir AGP.

Upgrade bloqueado (rulings en vigor, ver `gradle/libs.versions.toml` + AGENTS.md):

- `minSdk = 23` (Fire TV 3ª gen = Fire OS 7 = API 28; ruling usuario 2026-11-06).
- WorkManager `2.9.0`: la 2.12.0 declara minSdk 24.
- Room `2.7.2`, Paging `3.3.6`, Coil `2.7.0`, OkHttp `4.12.0` (5.x exige compileSdk 37).

Subir AGP sin mover ese grafo no elimina el warning; mover el grafo viola los rulings.
**Veredicto: DOC-no-acción.** Re-evaluar cuando se levante el pin de minSdk/toolchain.
Riesgo conocido: si Gradle 11 elimina el método y AGP 9.5.0 sigue llamándolo, el build
romperá al subir Gradle — mitigación = este doc + re-auditoría obligatoria al tocar toolchain.

## S2 — Auditoría 6 candidatos propios: veredicto no-cambio

Evidencia global: el build con `--warning-mode all` emite **un único warning** (el de S1).
Ningún warning referencia ninguno de estos flags.

| # | Ubicación | Flag | Warning observado | Veredicto | Motivo |
|---|-----------|------|-------------------|-----------|--------|
| 1 | `app/build.gradle.kts:49` | `sourceCompatibility = VERSION_17` | ninguno | no-cambio | API vigente en Gradle 9; la alternativa (`java { toolchain }`) cambia el modelo de compilación sin beneficio |
| 2 | `app/build.gradle.kts:50` | `targetCompatibility = VERSION_17` | ninguno | no-cambio | ídem #1; par inseparable, tocar uno solo rompería la coherencia |
| 3 | `app/build.gradle.kts:61` | `aidl = true` | ninguno | no-cambio | 3 `.aidl` reales en `app/src/main/aidl/org/acestream/…`; quitarlo rompe la compilación |
| 4 | `app/build.gradle.kts:66` | `isIncludeAndroidResources = true` | ninguno | no-cambio | 26 tests Robolectric `@Config(sdk = [34])` lo exigen; quitarlo rompe la suite |
| 5 | `gradle.properties:3` | `org.gradle.configuration-cache=true` | ninguno | no-cambio | intacto por encargo; el propio build confirma `Configuration cache entry stored` |
| 6 | `gradle.properties:5` | `android.nonTransitiveRClass=true` | ninguno | no-cambio | comportamiento recomendado; revertirlo vuelve a R transitivas (peor) |

Tocar cualquiera de ellos sería "flag sin warning" → prohibido por el encargo.

## S3 — Página de cierre válido

- **Cero warnings propios**: los 6 candidatos auditados no emiten ninguno.
- **Único warning restante = solo-plugin bloqueado**: `setVisible` vía AGP 9.5.0 (`AppPlugin` →
  `BasePlugin` → Kotlin embebido), upgrade bloqueado por minSdk 23 + grafo pineado (S1).
- **Criterio de reapertura**: (a) cambio de AGP/Gradle, o (b) aparición de un warning con
  frame en scripts propios (`--warning-mode all` + `grep` de S1 lo detectan).

Cómo verificar (comando / pasos):

```powershell
.\gradlew.bat :app:help --warning-mode all --console=plain
# Esperado: BUILD SUCCESSFUL + exactamente 1 warning (texto literal de S1) + "Configuration cache entry stored".
# Si aparece un 2.º warning, o uno con frame en app/build.gradle.kts, este doc queda invalidado → re-auditar.
```
