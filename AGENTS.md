# AGENTS.md — TVPlayer

Android TV / Fire TV / móvil media player. Player-only: la app no incluye, aloja ni distribuye contenido — el usuario provee listas M3U/Xtream. Spec vinculante: `docs/specs/2026-11-04-tvplayer-v1-design.md`. Plan de ejecución (16 tareas, fuente de verdad del alcance): `docs/superpowers/plans/2026-11-04-tvplayer-v01-core.md`. Ledger TDD con rulings: `.superpowers/sdd/2026-11-04-tvplayer-v01-core/progress.md`.

## Comandos

```powershell
# Suite completa (Robolectric, ~45 s en verde)
.\gradlew.bat :app:testDebugUnitTest --console=plain

# Un solo test
.\gradlew.bat :app:testDebugUnitTest --tests "*ImportServiceTest*" --console=plain

# APK debug
.\gradlew.bat :app:assembleDebug --console=plain
```

**Después de cambiar dependencias** añade `--no-configuration-cache` — hay un bug intermitente en `dataBindingMergeDependencyArtifactsDebug` que rompe el config-cache tras cambios en el grafo.

## Reglas duras del proyecto

- **Un solo OkHttp compartido** (`AppContainer.okHttp`) para TODO el transporte: import, EPG, logos (Coil via `Coil.setImageLoader` en `TpApp.onCreate`), playback (Media3 via `OkHttpDataSource.Factory`). El interceptor `CleartextGate` (Task 11) será el ÚNICO switch de http:// — jamás crees un segundo cliente.
- **Un solo permiso**: `INTERNET`. Sin WebView, sin DexClassLoader, sin `allowBackup`, sin telemetría. `usesCleartextTraffic` PROHIBIDO en manifest (el gate es runtime).
- **TDD estricto**: RED (test fallando) antes de GREEN, registrado en el ledger. Commit por tarea: `feat:`/`fix:`/`test:`.
- **Streaming en import/EPG**: nunca la lista completa en RAM; caps 30 MB (M3U) / 120 MB (XMLTV) en `ImportService`/`EpgService`.
- **Urls de canal** pasan por `UrlPolicer.isAllowedHttpUrl` ANTES de tocar red o decoder. El parser M3U preserva URLs verbatim (incluye `acestream://`); el filtro de política es responsabilidad del caller.
- **UI = Views + RecyclerView**, sin Compose. Dual layout: `res/layout/` (móvil) + `res/layout-television/` (TV, safe area 56dp). Foco TV: `FocusScaleHelper.attach(view)` escala 1.0→1.1 + borde acento (`tile_focus_bg`).

## Toolchain y trampas de versiones

- **minSdk 23** (ruling de usuario: Fire TV 3ª gen = Fire OS 7 = API 28; 23 cubre de sobra). NO bajar a 21 — la política anterior quedó obsoleta.
- **Media3 1.11.1** con deps como coordenadas string en `build.gradle.kts` (el catalog TOML no soporta claves con guión accesibles por punto en Kotlin DSL; `libs.named()` también falló).
- **Desugaring activado** (`desugar_jdk_libs:2.0.4`, `isCoreLibraryDesugaringEnabled=true`): `XmltvParser` usa `java.time`, inexistente en API 23-25.
- **WorkManager 2.9.0** (2.12.0 declara minSdk 24). Inyecta 5 permisos + componentes exportados con permisos de sistema — ver `scripts/acceptance.sh` y ledger Task 16.
- **Room 2.7.2** (2.8.x exige minSdk 23 pero no aporta nada crítico; quedará así hasta que algo lo exija). `room.schemaLocation` via KSP arg → schemas JSON en `app/schemas/` (commiteados).
- **Paging 3.3.6** — la última compatible con lo ya construido; NO subir sin verificar `asSnapshot` y `room-paging` juntos.
- **Coil 2.7.0** (`io.coil-kt:coil`, red embebida, `.okHttpClient()` first-class). Coil 3 exige refactor; no hay ganancia.
- **OkHttp 4.12.0** (5.x exige compileSdk 37).
- **Robolectric** con `@Config(sdk = [34])`; tests de Flow/Paging necesitan `Dispatchers.setMain(UnconfinedTestDispatcher())` en `@Before` + `resetMain()` en `@After`.
- **AGP 9.5.0 con Kotlin integrado** — NO aplicar el plugin `org.jetbrains.kotlin.android` (ya removed). El bloque `kotlin { compilerOptions { ... } }` va dentro de `android { }`.

## Arquitectura

```
app/src/main/java/com/tvplayer/app/
  core/        # parsers JVM puros (m3u/, xmltv/) + Normalizer, UrlPolicer, StableKey
  data/        # AppContainer (DI manual), db/ (Room), repo/ (ImportService)
  engine/      # PlaybackEngine + Media3Engine + PlaybackSource
  ui/          # home/, channels/ (browser), player/, common/ (FocusScaleHelper)
```

- **Una Activity** (`MainActivity`) + fragments reemplazados via `TvNavigator.NavHost.open()`. Navegación al player: `openPlayer(listId, position)`.
- **Room** como único estado de UI (UI observa DB, no ViewModels con estado duplicado).
- **StableKey** del canal: `tvg-id` si existe; si no, sha1 de `normName+group`. Los favoritos sobreviven reimports con esta clave.

## Gotchas de Windows/pwsh

- Comandos con `.\gradlew.bat` en foreground para capturar output completo; background (`&`) pierde stderr.
- MockWebServer reescribe Content-Length al usar `setBody()` — tests de cap deben usar `maxBodyBytes` inyectado (misma ruta de código que el default).
- Códigos de error XMLTV epoch: verifica aritmética de millis con fecha real antes de escribir tests de ventana temporal.
