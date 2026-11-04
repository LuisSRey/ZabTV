# TVPlayer v0.1 (M0–M2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reproductor Android 100% agnóstico de contenido: importa listas M3U/EPG/portal Xtream propias del usuario, reproduce directo con Media3, organiza por grupos, con favoritos, búsqueda y EPG XMLTV básico, corriendo en TV (D-pad) y móvil (táctil) desde API 21.

**Architecture:** App single-module Gradle con fronteras por paquete: `core` (parsers JVM puros), `data` (Room + OkHttp + repos), `engine` (`PlaybackEngine` + impl Media3), `ui` (1 Activity + fragments; layouts duales TV/táctil). Importación y EPG siempre en stream (nunca lista entera en RAM); DB como único estado de UI (UI observa BD).

**Tech Stack:** Kotlin 2.x, AGP, ViewBinding + RecyclerView, Media3 1.x (exoplayer+hls+dash+ss), Room, WorkManager, OkHttp + MockWebServer (tests), Coil, EncryptedSharedPreferences (androidx.security), Robolectric para tests de datos, R8 en release.

**Spec:** `docs/specs/2026-11-04-tvplayer-v1-design.md` (leer en paralelo; este plan argumenta desde ella).

## Global Constraints

- Paquete: `com.tvplayer.app`. Nombre visible: `TVPlayer`. minSdk 21 / targetSdk 35 / compileSdk 35.
- Único permiso en Manifest: `android.permission.INTERNET`. Sin `usesCleartextTraffic` ni `networkSecurityConfig` estático: **todo el transporte de la app pasa por el cliente OkHttp compartido (import, EPG, logos vía Coil, playback vía datasource de Media3)** y el interceptor `CleartextGate` (Task 11) es el ÚNICO punto que permite/bloquea `http://` según el ajuste del usuario (default OFF).
- Prohibido: WebView, DexClassLoader, `Html.fromHtml` con `movementMethod`, ServerSocket, servicios exportados, deep links, `allowBackup=true`, ads/telemetría/crashlytics.
- Tamaño/valores de spec: título de tamaños/límites exactos — M3U ≤ 30 MB, XMLTV ≤ 120 MB, ventana EPG `[now−12h, now+60h]`, inserción por lotes cada ~2 000 filas, contenedor EPG podado a ≤ ~5 días, WorkManager EPG 6 h con backoff, buffer Media3: back-buffer 8 s / min 15 s / max 30 s (ajustable), Coil cache disco 96 MB, ≤ 10 listas.
- Colores base: fondo `#0B0E14`, superficie `#131722`, acento único `#3DDC97`, texto `#E8ECF1`/secundario `#9AA3B2`.
- Esquemas: toda URL de media/logo pasa por `UrlPolicer.isAllowedHttpUrl` (solo http/https) ANTES de tocar red o decoder; texto SIEMPRE TextView plano.
- Strings dual: `values/` (es) + `values-en/` desde el primer commit que toque UI.
- Java runtime: JDK disponible en el sistema puede ser 25; si el daemon Gradle no arranca con él, instalar JDK 21 (Temurin) bajo `tools/jdk21/` y fijar `org.gradle.java.home` en `gradle.properties`. Compilación con toolchain Java 17.
- Cada task termina en commit con mensaje `feat|fix|test: ...` y mediante `git` el repo que se inicializa en Task 0.

## Review Focus

1. **Lista M3U sin header `#EXTM3U`, con BOM, attrs repetidos o `tvg-name` con comas** → parsear igual y no crashear; el nombre tras la ÚLTIMA coma. Test en Task 4.
2. **URL con esquema peligroso (`file://`, `intent://`, `javascript:`) en canales o logos** → rechazada por `UrlPolicer` y JAMÁS llega a network/decoder/Activity; lista se importa igual sin esos canales/logos. Test en Task 3 + Task 6.
3. **Reimportación que devuelve 0 canales o <10% de los actuales** → no se destruye nada; se marca error y se conservan datos previos. Test en Task 6.
4. **Favorito debe sobrevivir a reimportación de la misma lista** (clave estable `tvg-id` o `hash(norm_name+group)`). Test en Task 9.
5. **Cleartext OFF (default) con lista `http://`** → error claro y accionable, nada se descarga en claro; con ON funciona. Test en Task 11 + Task 6.

---

### Task 0: Bootstrap del repo y toolchain

**Files:**
- Create: `.gitignore`, `.gitattributes`, `settings.gradle.kts`, `build.gradle.kts` (root), `gradle.properties`, `gradle/libs.versions.toml`, `local.properties` (generado, NO en git), `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` (mínimo), `tools/placeholder.md`

**Interfaces:**
- Produces: Gradle wrapper ejecutable (`gradlew.bat`), AGP/Kotlin/Media3/Room versiones fijadas en `libs.versions.toml` (las que el bootstrap resuelva), SDK dir en `local.properties`.

- [ ] **Step 1: Iniciar git + .gitignore** (build/, .gradle/, local.properties, .idea/, tools/jdk*, *.apk release sin firma).
- [ ] **Step 2: Descargar Gradle (zip bin de la versión 9.x estable) a `tools/gradle/` y generar wrapper con `tools/gradle/bin/gradle.bat wrapper --gradle-version < misma >`**. Si el JDK del sistema (25) no arranca el daemon, descargar Temurin 21 a `tools/jdk21/` y fijar `org.gradle.java.home`.
- [ ] **Step 3: Detectar Android SDK en `%LOCALAPPDATA%\Android\Sdk`** — verificar plataformas alternativas: si falta `platforms;android-35` y `build-tools;35.x`, se instala con `cmdline-tools/bin/sdkmanager` (o se fija `compileSdk` al nivel disponible ≥ 33, documentando la desviación).
- [ ] **Step 4: versions catalog mínimo**: kotlin, agp, media3, room, okhttp, coil, workmanager, lifecycle, junit, robolectric, mockwebserver.
- [ ] **Step 5: Verificar build vacío**

Run: `gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (APK debug generado).

- [ ] **Step 6: Commit** `feat: bootstrap gradle project (TV+, minSdk21)`

### Task 1: Shell de app (Activity única + Home vacía dual)

**Files:**
- Create: `app/src/main/java/com/tvplayer/app/MainActivity.kt`, `ui/home/HomeFragment.kt`, `ui/nav/TvNavigator.kt`, `ui/common/FocusScaleHelper.kt`, `ui/settings/AboutDialog.kt` (vía Home), layout `app/src/main/res/layout/activity_main.xml`, `fragment_home.xml` (+ `layout-sw600dp/`), `res/values/strings.xml`, `res/values-en/strings.xml`, `res/values/themes.xml` (dark TV: colores del spec, sin dayNight), manifest completo con `LEANBACK_LAUNCHER` + `banner` +(android.intent.category.LAUNCHER para móvil), `android:resizeableActivity="true"`.
- Modify: manifest (registrar Activity, permiso INTERNET, leanback required=false).

**Interfaces:**
- Produces: `TvNavigator.open(player/list/search/settings)` API básica vía FragmentManager; `FocusScaleHelper.attach(view)` (scale 1.0→1.1 + borde en focus).

- [ ] **Step 1: Implementar shell** — Home vacía (táctil y TV) con disclaimer "no incluye contenido" + tile "Añadir lista"; navegación por FragmentManager vía `TvNavigator`.
- [ ] **Step 2: Añadir test Robolectric smoke**

Test: `HomeSmokeTest` — MainActivity lanza, home muestra disclaimer string id.
Run: `gradlew :app:testDebugUnitTest --tests "*HomeSmokeTest*"` Expected: PASS (tras el paso 1 falla por clases ausentes).

- [ ] **Step 3: Verificar debug build + manifest dump**

Run: `gradlew :app:assembleDebug` y `aapt dump badging app-debug.apk | grep -i "leanback\|INTERNET"` Expected: lanzador leanback presente; sin más permisos.

- [ ] **Step 4: Commit** `feat: app shell home dual + manifest leanback + theme dark`

### Task 2: Esquema Room + AppContainer

**Files:**
- Create: `data/db/AppDatabase.kt`, `data/db/entities.kt` (ListEntity, ChannelEntity, FavouriteEntity, EpgProgramEntity, SettingEntity), `data/db/daos.kt` (ListDao, ChannelDao, FavouriteDao, EpgDao, SettingsDao), `data/AppContainer.kt` (DI manual: db, okHttp con cache 32MB/96MB disk para imágenes vía Coil config, dispatchers).

**Interfaces:**
- Produces (firmas exactas — todo el resto del plan enlaza aquí):
  - `ListDao`: `all(): List<ListEntity>`, `get(id: Long): ListEntity?`, `upsert(list: ListEntity): Long`, `delete(id: Long)`.
  - `ChannelDao`: `upsertAll(channels: List<ChannelEntity>): Unit` (idempotente por clave estable), `countForList(listId: Long): Int`, `groupsForList(listId: Long): List<GroupCount>` con `data class GroupCount(val group: String, val count: Int)`, `pagingForListAndGroup(listId: Long, group: String?): PagingSource<Int, ChannelEntity>` (group null = todos).
  - `FavouriteDao`: `toggle(listId: Long, channelKey: String): Boolean`, `exists(listId, channelKey): Boolean`, `favouritesListId(listId: Long): List<ChannelEntity>`.
  - `EpgDao`: `insertBatch(programs: List<EpgProgramEntity>): Unit`, `nowNext(tvgIds: List<String>, atMillis: Long): Map<String, Pair<EpgProgramEntity?, EpgProgramEntity?>>`, `dayPrograms(tvgId: String, startInclMillis: Long, endInclMillis: Long): List<EpgProgramEntity>`, `pruneBefore(beforeMillis: Long): Int`, `count(): Int`.
  - `SettingsDao`: `get(key: String, default: String?): String?`, `put(key, value)`.

- [ ] **Step 1: Escribir test Robolectric** `AppDatabaseSchemaTest`: inserta list+canal+fav+epg, respeta índice/unique de `favourites(list_id, channel_key)` y `epg(tvg_id, start)`; query `search` devuelve por `norm_name LIKE`. Run fail → implementar → PASS (Robolectric, inMemory).
- [ ] **Step 2:** habilita KSP + Room in app module.
- [ ] **Step 3: Commit** `feat: room schema v1 + app container`

### Task 3: Núcleo — normalizador y URL allowlist

**Files:**
- Create: `core/norm/Normalizer.kt` (`fun normName(raw: String): String` — lowercase, NFD-strip diacríticos, colapsa espacios, quita tokens HD/FHD/SD/4K), `core/net/UrlPolicer.kt` (`fun isAllowedHttpUrl(url: String?): Boolean`; `fun requireAllowedHttpUrl(url) throws UrlPolicyException`).

**Interfaces:**
- Produces: `normName(raw: String): String`; `UrlPolicer.isAllowedHttpUrl(url: String?): Boolean` (usado por import, logos y por `Media3Engine.prepare`).

- [ ] **Step 1: Failing test** `UrlPolicerTest`: permite `http://`/`https://`; rechaza `file://`, `intent://`, `javascript:`, `content://`, `ftp://`, cadena vacía, null, `HTTP://` en mayúsculas es válida; URLs con espacios/controles rechazadas. `NormalizerTest`: `"Canal HD  Español™"` → `"canal espanol"` esperado.
- [ ] **Step 2: Implementar** (esquema leído vía `URI`; rechazar sin `http/https`, con user-info `user@`, con espacios o caracteres de control; `Exception class UrlPolicyException(message)`.`).
- [ ] **Step 3: Run tests PASS.**
- [ ] **Step 4: Commit** `feat: normalizer + url allowlist (core)`

### Task 4: Parser M3U por streaming

**Files:**
- Create: `core/m3u/M3uParser.kt`, `core/m3u/model.kt` (`M3uHeader(epgUrl: String?)`, `M3uChannel(tvgId: String?, tvgName: String?, logoUrl: String?, groupTitle: String?, name: String, url: String, rawAttrs: Map<String,String>)`).
- Test: `app/src/test/java/com/tvplayer/app/core/M3uParserTest.kt` + fixture strings embebidos.

**Interfaces:**
- Produces: `fun parse(reader: Reader): M3uDocument` donde `M3uDocument(header: M3uHeader, channels: Sequence<M3uChannel>)`. El `Sequence` hace pull-línea-a-línea (streaming); cerrar reader corta.

- [ ] **Step 1: Failing tests** — casos: documento normal; **sin `#EXTM3U` header**; BOM inicial; **attrs repetidos** (último gana); nombre con comas (tras la ÚLTIMA coma de EXTINF); líneas `#EXTVLCOPT` ignoradas; URLs `acestream://` preservadas tal cual (la UI decide luego, Task 8); líneas basura sueltas; CRLF; 10 000 líneas sintéticas parseadas < 1 s (JVM).
- [ ] **Step 2: Implementar con BufferedReader line-stream**: regex attr simple `\b([\w-]+)="([^"]*)"`; header extrae `x-tvg-url|url-tvg`. Sin dependencias.
- [ ] **Step 3: Run PASS** (`gradlew :app:testDebugUnitTest --tests "*M3uParser*"`)
- [ ] **Step 4: Commit** `feat: streaming m3u parser + edge cases`

### Task 5: Parser XMLTV por streaming

**Files:**
- Create: `core/xmltv/XmltvParser.kt`, `core/xmltv/model.kt` (`XmltvChannel(id: String, displayName: String?)`, `XmltvProgramme(tvgId, startEpoch, endEpoch, title, description?)`), `core/xmltv/EpgWindow.kt` (`val WINDOW_HALF_PAST_HOURS = 12`, `WINDOW_FORECAST_HOURS = 60`; `fun inWindow(startEpoch, nowEpoch)`).
- Test: `core/XmltvParserTest.kt`.

**Interfaces:**
- Produces: `fun parse(reader: InputStream, nowEpoch: Long): XmltvResult` con `channels: List<XmltvChannel>` (poca gente, ≤ ~10k) y `programmes: Sequence<XmltvProgramme>` **filtrada por ventana** en el propio parser (descarta fuera de ventana ANTES de materializar).

- [ ] **Step 1: Failing tests** — doc real; con `<channel id="c1.ext"><display-name>..` y `<programme channel="c1.ext" start="20260104120000 +0000" stop="...">`; **timezone offsets variados** (con/+00:00); programas fuera de ventana excluidos; entidades HTML en `&amp;` title decodificadas; doc truncado a mitad (no crashea: termina el Sequence limpio); 50 000 programas sintéticos parse ≤ 5 s en JVM test como referencia.
- [ ] **Step 2: Implementar** con `XmlPullParser` (streaming, `FEATURE_PROCESS_DOCDECL=false`, namespace off). Fechas: `DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")` (desugaring ya activado vía coreLibraryDesugaring en Task 0/build).
- [ ] **Step 3: Run PASS · Commit** `feat: streaming xmltv parser + window`

### Task 6: Pipeline de importación (servicio + límites + salvaguarda)

**Files:**
- Create: `data/repo/ImportService.kt`, `data/repo/ImportSource.kt` (`HttpSource(url, ua?)` | `FileSource(uri)`), `data/repo/ImportResult.kt` (`data class ImportResult(listId, saved: Int, skipped: Int, errorLines: Int, log: List<String>, durationMs: Long)`).
- Modify: `ChannelDao/ListDao` según interfaces Task 2.

**Interfaces:**
- Consumes: Task 3-5 (parsers, `UrlPolicer`, `normName`, cap 30 MB), Task 2 DB, cliente OkHttp compartido de `AppContainer` (sin parámetro extra: el gate de cleartext del Task 11 es transparente para el pipeline).
- Produces: `suspend fun ImportService.import(listId: Long?, source: ImportSource): ImportResult` con `ImportResult(listId, saved: Int, skipped: Int, errorLines: Int, log: List<String>, durationMs: Long, rejected: Boolean, message: String?)` — descarga en streaming, transacción cada 2 000 canales; canal con URL no permitida por `UrlPolicer` → `skipped` + línea en `log`; **regla del 10 %**: si en reimportación `saved < ceil(prevCount × 0.10)` cuando `prevCount > 0` → NO TOCAR tabla, devolver `rejected=true` y mensaje; cancelación limpia (no deja tabla a medias).

- [ ] **Step 1: Failing tests Robolectric + MockWebServer** — tabla con 100 canales; importa fixture con 2 003 canales, 3 de ellos con esquema `file://` → `saved=2000, skipped=3, errorLines=3`; **reimportación devolviendo 0 canales** → tabla conserva los 100 y `rejected=true`; UA personalizada llega al servidor (check de header); cancelación a mitad no deja tabla a medias (se comprueba el count tras cancelar).
- [ ] **Step 2: Implementar** (OkHttp `Call.execute()` stream + límite `30_000_000` bytes; superado → aborto limpio con `ImportResult` marcado).
- [ ] **Step 3: Run PASS. Benchmark referencia JVM**: import de fixture 10k canales ≤ 5 s en Robolectric como calibrado de dev (número de dispositivo se recalibra en M1 con Task 15).
- [ ] **Step 4: Commit** `feat: import pipeline with limits, 10% guard, cancellation`

### Task 7: UI Navegación de canales (TV rail+grid y móvil pestañas)

**Files:**
- Create: `ui/channels/ChannelBrowserFragment.kt` (TV: rail izquierdo de grupos + grid derecha), `ui/channels/ChannelBrowserViewModel.kt` (expone `groups: StateFlow<List<GroupCount>>`, `channels: Flow<PagingData<ChannelEntity>>` por grupo), RecyclerView adapters + ViewBinding, layouts `fragment_channel_browser.xml` (+ variante móvil táctil: pestañas horizontales arriba, grid a ancho completo) — ambos aquí.

**Interfaces:**
- Consumes: Task 2 DAOs; `TvNavigator` (Task 1).
- Produces: callback `onChannelClick(channel: ChannelEntity)` hacia `TvNavigator.openPlayer(currentList, channelsListOrdering, initialIndex)` (Task 8).

- [ ] **Step 1: Implementar** (Paging 3 desde DB; focus scale con el helper del Task 1; logo con Coil y placeholder por iniciales si falta).
- [ ] **Step 2: Test Robolectric** `ChannelBrowserViewModelTest`: datos seed → groups computa conteos correctos; cambiar grupo → paging source cambia. Run PASS.
- [ ] **Step 3: Commit** `feat: channel browser dual layout + paging`

### Task 8: Motor de reproducción + Player UI (zapping/overlay)

**Files:**
- Create: `engine/PlaybackEngine.kt` (`interface PlaybackEngine { fun bind(surface); fun prepare(src: PlaybackSource); fun play/pause...; onStateChanged/onError suspend callbacks; fun release() }`), `engine/PlaybackSource.kt` (`data class Hls/dash/progressive` — tipo decidido por extensión+Content-Type), `engine/Media3Engine.kt` (buffer per spec; UA opcional; error mapper con mensaje accionable: red, permiso/servidor, formato no soportado (rtmp/udp/pipe → mensaje claro), cleartext off), `ui/player/PlayerFragment.kt` (SurfaceView, overlay 5 s con logo/nombre/**ahora** (Task 14 conecta)/sig, zapping arriba/abajo en el orden de la lista de entrada, long-press OK favorito via Task 9, BACK close), `ui/player/PlayerViewModel.kt`.

**Interfaces:**
- Consumes: Task 6/7 (canal+orden), Task 3 (UrlPolicer en prepare).
- Produces: `Media3Engine.errorFor(PlaybackException): PlayerError(code, userMessageRes)` — testeo directo.

- [ ] **Step 1: Failing unit test** `Media3EngineSourceTest`: URLs a Source correcto por extensión (`m3u8`→HLS, `mpd`→DASH, `ts/mp4/mkv`→progressive); URL esquema no permitida → error antes de crear player; error codes map a res ids de mensaje.
- [ ] **Step 2: Implementar engine + player fragment** (zapping por índice sobre orden actual; placeholder "ahora" hasta Task 14).
- [ ] **Step 3: Commit** `feat: playback engine media3 + player ui zapping overlay`

### Task 9: Favoritos

**Files:**
- Modify: `FavouriteDao` (Task 2), `core/m3u/Channel` hash clave estable = `tvgId ?: sha1(normName+group)`; `data/repo/FavouritesRepo.kt`.
- Create: rail de favoritos en Home (Task 7 fragment reutiliza adapter del browser); toggle desde player long-press OK y ficha.

**Interfaces:**
- Produce: `FavouritesRepo.toggle(listId, channel): Boolean`, ` favouritesForList/listado rail`, y Home por grupos/canales.

- [ ] **Step 1: Failing test estable + reimport**: `FavouritesDaoTest` — favorito marcado, canal reimportado con misma clave → favorito vivo; canal con URL distinta pero tvg-id igual → vivo. Run PASS.
- [ ] **Step 2: Implementar repo/rail/toggle.**
- [ ] **Step 3: Commit** `feat: favourites stable key + rail + toggle`

### Task 10: Búsqueda

**Files:**
- Modify: `ChannelDao.search` (Task 2) — `WHERE list_id=? AND norm_name LIKE '%' || :query || '%' LIMIT 100`.
- Create: `ui/search/SearchFragment.kt` + ViewModel, input por teclado (IMF en TV vía `inputType=text` y D-pad OK) + campo táctil en móvil.

**Interfaces:**
- Consumes: `ChannelDao.search`, `normName` (Task 3).

- [ ] **Step 1: Failing test DAO** — seed con `"Canal Spanish™ HD"` → `norm_name="canal spanish"`; búsqueda por `"spanish"` lo devuelve; por `"noexiste"` vacía; con 10k canales responde < 200 ms (JVM).
- [ ] **Step 2: Implementar pantalla + integración Home.**
- [ ] **Step 3: Commit** `feat: search screen accent-insensitive`

### Task 11: Gestión de listas + Ajustes (incl. cleartext opt-in y limpieza)

**Files:**
- Create: `ui/lists/ListsFragment.kt` (alta URL/archivo, borrar, refrescar manual; `<10%` guard surfaced), `ui/lists/AddListDialog...` (URL+UA opcional), `ui/settings/SettingsFragment.kt` (buffer sizes, **cleartext toggle con advertencia**, UA por lista, limpiar favoritos/EPG/logos/todo, acerca-de con disclaimer §6.8 exacto).
- Modify: `data/AppContainer` — OkHttp crea un interceptor `CleartextGate` que bloquea `http://` si `SettingsDao.get(allow_http,false) == false` (error con mensaje "activa HTTP en Ajustes"); NOTA: `networkSecurityConfig` queda TLS estricta por defecto y el toggle runtime se aplica por interceptor (config XML es estática).

- [ ] **Step 1: Failing test** `CleartextGateTest`: setting OFF → llamada a MockWebServer (http) falla antes de conectar con mensaje claro; ON → 200 OK pasa.
- [ ] **Step 2: Implementar pantallas** (+18N strings).
- [ ] **Step 3: Commit** `feat: lists manager + settings + cleartext opt-in`

### Task 12: Portal Xtream Codes + credenciales cifradas

**Files:**
- Create: `data/portal/XtreamLogin.kt` (host normalizador: añade `https://` si el usuario no pone esquema; URLs `get.php?username=U&password=P&type=m3u_plus&output=ts` y `xmltv.php?username=U&password=P`; credenciales urlencoded), `data/portal/SecureCredentials.kt` (EncryptedSharedPreferences con Keystore).
- Modify: Task 11 dialog añade tab "Portal" (host/user/pass).

**Interfaces:**
- Consumes: Task 6 ImportService (el portal construye URLs y delega).
- Produces: `fun XtreamLogin.playlistUrl(...)`, `epgUrl()`; `SecureCredentials.save/load(listId)`.

- [ ] **Step 1: Failing test pure** (`XtreamLoginTest: host variants añade esquema https por defecto, credenciales urlencoded; error host ridículo`); `SecureCredentialsTest Robolectric roundtrip + blob no plain en prefs file`.
- [ ] **Step 2: Implementar + UI tab portal** (single request, cred invalid → mensaje específico; nunca logar credenciales).
- [ ] **Step 3: Commit** `feat: xtream portal + encrypted credentials`

### Task 13: Ingesta EPG + WorkManager

**Files:**
- Create: `data/repo/EpgService.kt` (stream SAX → ventana → insertBatch cada 2 000 → prune ≤5 días), `data/work/EpgWorker.kt` (6h, backoff, restricciones), registro en `AppContainer`/Application.

**Interfaces:**
- Consumes: Task 5, `EpgDao` Task 2, cap 120 MB, timeouts cortos.

- [ ] **Step 1: Failing test Robolectric** — fixture XMLTV 5 000 programas (chunk fuera de ventana) → inserta solo en ventana; segunda corrida poda antiguo; **cap 120 MB aborta limpio un fixture-forzado**.
- [ ] **Step 2: Implementar service + worker registro 6h.**
- [ ] **Step 3: Commit** `feat: epg ingest streaming + worker 6h`

### Task 14: EPG UI (ahora/siguiente + ficha de canal + overlay)

**Files:**
- Modify: row de canal / ficha de canal (Task 7) añade tira "Ahora/Siguiente" via `EpgDao.nowNext` batch (todas las visibles); Player overlay conecta "ahora" (Task 8); Tag "aproximado" cuando match es por nombre (fallback), no exacto.
- Create: `ui/channel_dialog/ChannelSheetFragment.kt` — ficha: logo, nombre, grupo, lista de todos los programas del día (consultas por rango).

- [ ] **Step 1: Failing test** `EpgUiQueriesTest`: comportamiento determinística por rango (dayPrograms); matching exact vs fallback-nombre marcado. PASS.
- [ ] **Step 2: Implementar UI (TV y móvil).**
- [ ] **Step 3: Commit** `feat: epg now/next + channel sheet + overlay integration`

### Task 15: Fixtures sintéticos + script perf_check

**Files:**
- Create: `tools/gen_fixtures.py` (M3U 10 000 canales c/attrs raros + XMLTV 50 000 programas), `scripts/perf_check.sh` (am start -W, meminfo a 5 min, gfxinfo 60 s, `logcat -b events|grep anr`, tabla final), `docs/perf_results/README` (plantilla resultados).
- NOTA: verificar `python --version`; si no hay Python, el generador se implementa como taskGradle `:tools:genFixtures` (Kotlin) en el mismo repo — elegir UNA vía y no duplicar.

- [ ] **Step 1: Generar fixtures** disponibles en `tools/fixtures/` (rutas estables); `python tools/gen_fixtures.py --quick` sale <10 s.
- [ ] **Step 2: Commit** `test: synthetic fixtures + perf script`

### Task 16: Release R8 + paquete de aceptación seguridad (F8)

**Files:**
- Modify: `app/build.gradle.kts` (release minify+shrinkResources, signingConfig vía env/keystore local NO en git, `debuggable=false`).
- Create: `scripts/acceptance.sh` (aapt dump badging/permissions, dump del Manifest con apkanalyzer, check de ausencia WebView/DexClassLoader, SHA-256 del APK), `app/proguard-rules.pro` mínimo (Media3/Room traen consumer rules propios).

- [ ] **Step 1: `gradlew :app:assembleRelease`** con firma local (keystore fuera de git via env var) → APK ≤ 15 MB, SHA-256 impreso y guardado en `docs/perf_results/`.
- [ ] **Step 2: acceptance script** pasa: permisos = exactamente INTERNET; exported=false salvo launcher; sin atributo `usesCleartextTraffic`; sin clases WebView/DexClassLoader en el dump; el `CleartextGate` está presente (jadx opcional local, o si no: verificación por tests del Task 11); proguard file correcto (`proguard-rules.pro`, Media3/Room traen sus consumer rules).
- [ ] **Step 3: Commit** `feat: release build hardening + security acceptance`

---

## Notas de ejecución (leer antes de empezar)

- El entorno del agente es Windows: usar `gradlew.bat`; los paths en commits relativos.
- Tests de volumen (Bench 3/6/5) son JVM-referencia; los criterios de dispositivo (spec §6.7) se verifican en M1 final con `scripts/perf_check.sh` + fixtures del Task 15 y se registran en `docs/perf_results/`.
- Cancelación de importaciones vía `runCatching + isActive` del coroutine scope del ImportService, no a mitad de transacción (se comprueba en límites de task 6).
