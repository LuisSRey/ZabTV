# TVPlayer — Diseño y especificación v1

Fecha: 2026-11-04 · Estado: borrador para aprobación · Autor del borrador: arquitecto asistido

## 1. Qué es y qué no es

TVPlayer es una **aplicación reproductora que funciona de forma nativa en Android TV, Fire TV (desde Fire OS 5) y teléfonos/tabletas Android** — una sola app, dos modos de navegación (D-pad de mando y táctil), API 21+, pensada para dispositivos de recursos limitados (desde ~1 GB de RAM).

**La aplicación no incluye, aloja, descarga, distribuye ni selecciona contenido.** Solo reproduce lo que el propio usuario añade: sus listas M3U/M3U8/EPG, sus enlaces y sus credenciales/proveedores.

Además, **no es un vector de intrusión** (spec §6-F8): no escucha en puertos ni abre servidores locales, no ejecuta código remoto, solo emite peticiones salientes, cifra credenciales en reposo y usa TLS por defecto. La distinción se materializa en tres sitios:

1. **Descripción de publicación** (texto listo en §6.8).
2. **Primera pantalla sin listas**: "Esta aplicación no incluye contenido. Añade tu propia lista para empezar."
3. **Cláusula de spec**: ninguna pantalla NAVEGA, propone, ordena ni completa fuentes; solo lee y muestra lo importado.

TiviMate es **referencia funcional** (directo + guía + favoritos + secciones), no de diseño ni de alcance obligatorio.

Por qué existe: los reproductores actuales arrastran RAM, arranque y jank en sticks de gama baja. Objetivo: app pequeña, de arranque rápido y reproducción estable.

## 2. Objetivos / no-objetivos / anti-objetivos

**Objetivos (v1)**
- Directo fluido con listas grandes (≥10 000 canales) navegables con mando.
- EPG XMLTV básico por canal (ahora/siguiente + ficha de canal con programación).
- Favoritos, búsqueda indexada, gestión de varias listas, ajustes.
- Funcionamiento nativo en móvil/tableta Android: núcleo compartido, layouts táctiles propios (clases de UI distintas, lógica compartida).
- **Seguridad desde el diseño** (F8): mínima superficie de ataque; no es punto de entrada al disco, red interna ni cuentas del dispositivo.
- Motor de reproducción abstraído (`PlaybackEngine`), Media3 como implementación v1.
- Métricas duras de rendimiento (§6.7) verificables con adb.

**No-objetivos (fuera de v1, decidido)**
- Catálogo/contenido propio, scraping, recomendaciones.
- Guía tipo grid multi-canal (llega en v0.3+ si hay demanda; v1 lleva guía por canal).
- Vista previa de vídeo en las listas (requiere 2.º decodificador; post-v1 como opción desactivada por defecto vía PiP).
- Secciones de películas/series (v0.4, heurístico de grupos; las listas reales no traen metadatos garantizados).
- Catchup/rec, DVR, PiP estable, Chromecast.
- iOS, Windows, Linux, web (este proyecto es Android; el núcleo `core` es JVM puro por si hay derivados).
- Publicación en store de Amazon (v1 = sideload; se evaluará luego).
- **Motores P2P incrustados** (ver §4).

**Anti-objetivos (nunca)**
- Redistribuir motores Ace Stream o componentes P2P de terceros (ToS) — ver §4.
- Funciones que sumen RAM o arranque sin valor demostrado en dispositivo real.

## 3. Decisiones cerradas

| Decisión | Elección | Alternativa descartada y por qué |
|---|---|---|
| UI | Vistas XML + RecyclerView con patrones Leanback | Compose TV: más runtime, RAM y arranque frío en 1 GB |
| minSdk | **21** (decisión final del usuario tras revisar su hardware: su Fire TV propio es antiguo) — desugaring de `java.time` vía coreLibraryDesugaring; resto de stack (Media3, Room, OkHttp, Coil, WorkManager) soporta API 21 | 28: era el borrador inicial; dejaba fuera el propio stick del usuario |
| Idioma/lenguaje | Kotlin, Gradle Kotlin DSL, module único (paquetes como frontera) | multi-module: coste de build/config sin beneficio a esta escala |
| Reproductor | Media3/ExoPlayer (hls + dash + smoothstreaming + core) | libVLC o Fire MP: menos control de buffer/errores, más peso |
| EPG/XMLTV | Parser SAX propio por streaming + Room | librerías XMLTV: inexistentes/mantidas a medias; DOM explota en RAM |
| M3U | Parser propio por streaming (líneas, attr list) | librerías que materializan la lista entera en RAM |
| Datos | Room (listas, canales, EPG, favoritos, settings KV) | SQLite manual: menos garantías de migración |
| Red | OkHttp (timeouts cortos, cache HTTP, reintentos limitados) | Retrofit solo si crece; innecesario en v0.1 |
| Imágenes | Coil, cache disco máx. 96 MB, decode al tamaño del target | Glide: ok pero más configuración; nada de arreglos gigantes sin downsample |
| Trabajo en fondo | WorkManager: **solo EPG** se refresca cada 6 h (con backoff). La importación de listas M3U es **manual** en v0.1; si una reimportación trae 0 canales, NO se destruyen los datos existentes (se marca error y se conservan los canales actuales) | Service propio: más batería/código |
| DI | Manual (AppContainer) | Hilt: codegen que no paga a esta escala |
| Distribución | APK sideload, launcher LEANBACK, banner | Play/apto para Fire Store: fuera de alcance v1 |
| Tema | TV oscuro de alto contraste: fondo #0B0E14, superficie #131722, acento único, zoom/scaling en focus 1.0→1.1, safe area overscan 5% | — |
| Idiomas | ES por defecto + EN desde M0 (`values/` + `values-en/`); hacerlo tarde es re-trabajo | solo ES: más barato, pero bloquea publicación bilingüe |
| Móvil/tableta | Mismos ViewModels/Repos/Engine; **layouts alternativos táctiles** (qualifiers `sw600dp`, orientación), navegación con touch + barra inferior; focus abstraído en un helper (`FocusAdapter`) que degenera en click normal en móvil | dos apps separadas: duplicación total de lógica |
| Seguridad | Ver F8: sin puertos/servidores, sin código remoto, TLS por defecto con cleartext opt-in por ajuste, esquemas en allowlist, componentes no exportados, credenciales con Keystore + AES-GCM, sin WebView, R8 + firma release con SHA-256 publicado | política de red laxa o cleartext abierto: vector de MITM/intrusión |

## 4. AceStream — evaluación y veredicto

**Opciones consideradas**

1. **Incluir motor propio** (fork P2P + jlibtorrent): +20–40 MB de librerías nativas, cientos de MB de RAM al hacer torrent, riesgo de ToS/legales alto. **Descartada**: el usuario no puede incumplir términos de servicio y es inviable en el dispositivo objetivo.
2. **Delegar a un engine instalado por el usuario** (vía API local documentada, p. ej. 127.0.0.1:6878 + HLS local): el APK queda intacto, la RAM del P2P vive fuera de la app, y no redistribuimos nada de Ace Stream. **Seleccionada como camino futuro**, con dos salvaguardas:
   - Se **revalidan los términos actuales de Ace Stream** justo antes de implementar ese hito; si esos términos prohibieran el uso de la API local por terceros, el hito se cancela.
   - La app nunca autoinstala ni redirige a descargas del engine: solo **detecta** si responde en el puerto local y muestra estado claro ("engine no detectado").
3. **No soportar en absoluto**: modelo más simple, pero pierde la función pedida sin compensación.

**Veredicto v1:** en v0.1 solo existe la **abstracción** `PlaybackEngine`; `acestream://` se detecta en el parser y la UI lo marca como canal no soportado aún, con mensaje claro. La delegación (opción 2) es candidata a **M3** pendiente de revisión ToS.

**Coste/limitación honesta:** incluso delegando, el propio engine consume muchos recursos en el dispositivo (descarga P2P + transcodificación parcial); en un stick de 1 GB el resultado puede ser precario. Si llega a implementarse, esa limitación se comunica en la UI.

## 5. Arquitectura

```
ui (Activity única + fragments)      ← focus, leanback, overlays player
   ⤵ view-models/fragments observan repos
data (Room + OkHttp)                 ← ListsRepo, ChannelsRepo, EpgRepo, FavouritesRepo, SettingsRepo
   ⤵
core (parsers M3U/XMLTV, normalización, tiempo)
engine (PlaybackEngine + Media3Engine)
```

Reglas de frontera: `ui` no toca parsers/red; `engine` no conoce Room; `core` es puro JVM (testable sin emulador).

**Esquema Room (v0.1)**

- `lists(id, name, source_url, epg_url?, user_agent?, last_imported_at, status)`
- `channels(rowid, list_id, tvg_id?, display_name, norm_name, group_name, logo_url?, url, position)` + índices `(list_id, group_name)`, `(tvg_id)`, `(norm_name)`
- `favourites(list_id, channel_key)` — PK compuesta; clave de canal: `tvg-id` si existe, si no `hash(norm_name + group)`; sobrevive reimportaciones de la MISMA lista. Los favoritos son por canal-instancia de cada lista (dos listas con el mismo tvg-id = dos favoritos independientes); el zapping navega dentro del punto de entrada (desde Favoritos navega el rail de Favoritos).
- `epg_programs(rowid, tvg_id, start_epoch, end_epoch, title, description)` + índice `(tvg_id, start_epoch)`; ventana insertada: `[now−12h, now+60h]`; poda por WorkManager.
- `settings(key, value)`

**Flujo de datos clave:** importación M3U = descarga en stream por líneas → parse → inserción por lotes (transacción cada ~2 000 filas) → la UI observa con `PagedList`/cursor lazy. **Nunca abrimos la lista completa en RAM.**

**Pantallas v0.1:** Home (secciones: Favoritos / Todos los grupos / Guía / Búsqueda / Listas / Ajustes — "Recientes" no entra en v0.1), Lista de canales (rail de grupos a la izq. + grid a la dcha.), Player a pantalla completa (SurfaceView, overlay de info con timeout 5 s, zapping con arriba/abajo, favorito con pulsación larga de OK), Ficha/Guía de canal (programa actual y siguientes por canal), Búsqueda, Listas (añadir/editar/borrar/refrescar), Ajustes (buffer, UA por lista, limpiar datos, versión + disclaimer). En móvil/tableta: misma información con navegación táctil (grid puro con pestañas de grupos, botones overlay del player, tarjetas clicables), sin rail obligatorio.

**Mando:** D-pad estándar + BACK cierra overlays; long-press OK = favorito; focus siempre visible con scale + borde; ningún elemento navegable sin estado de focus.

## 6. Especificación v0.1 — funciones con criterios de aceptación

### F1. Importar y gestionar listas
- Añadir por **URL** (http/https) o **archivo** (USB/storage). Formato: M3U/M3U8 con `#EXTM3U` + atributos `tvg-id`, `tvg-name`, `tvg-logo`, `group-title`; header `x-tvg-url`/`url-tvg` reconocido.
- Opcional por lista: **User-Agent personalizada** (algunos proveedores la exigen).
- **Portales Xtream Codes (v0.1)**: alta con host + usuario + contraseña; la app construye las URLs estándar del portal (`get.php?...&type=m3u_plus` para la playlist, `xmltv.php` para la EPG) y alimenta la **misma pipeline** de M3U/XMLTV. Una petición por importación (sin polling); credenciales **cifradas en reposo (F8)**; errores de credenciales/bloqueo del portal con mensaje claro.
- Hasta 10 listas; reimportación actualiza en sitio (upsert), conserva favoritos y ajustes.
- Líneas corruptas o atributos ausentes **no detienen** la importación: se registran en el log de import (visible) y se continúa.
- **Aceptación:** 10 000 canales importados ≤ 5 s en el dispositivo de prueba (sin contar el tiempo de descarga); lista malformada no crashea; cancelar la importación funciona y no deja filas a medias; flujo Xtream: credenciales válidas → canales y EPG del portal importados; credenciales inválidas → error específico, sin baja de datos previos.

**Salvaguardas de importación:** una reimportación que llegue vacía o con menos del 10% de canales no sustituye la lista activa; se guarda el error y se mantiene lo anterior.

### F2. Estructura y navegación
- Secciones por `group-title`, listas de canales en grid con logos (o iniciales/generado si no hay logo).
- **Aceptación:** navegar 10 000 canales con scroll acelerado en 60 s sin jank perceptible (o frame budget ≥95% dentro de deadline, vía `dumpsys gfxinfo`).

### F3. Reproducción (Media3)
- Protocolos: HLS (`m3u8`), DASH (`mpd`), MP4/MKV progresivos, MPEG-TS por HTTP.
- rtmp, UDP multicast y formatos "pipe": **no soportados en v0.1**, con error claro y accionable.
- Buffer conservador por defecto, ajustado a poca RAM (p. ej. back-buffer 8 s, mínimo 15 s, máximo 30 s; configurable en Ajustes).
- Zapping: arriba/abajo cambia al canal siguiente/anterior de la lista actual (no todas las listas de golpe).
- **Aceptación:** primer frame ≤ 2,5 s en un canal HLS de 10 Mbps con servidor saludable; 30 min de reproducción continuada sin OOM/ANR ni degradación (PSS estable ±10%); error de stream muestra mensaje y opción de reintentar.

### F4. Favoritos
- Toggle con long-press OK en canal o ficha; secciones Favoritos primero en Home y como rail.
- **Aceptación:** favorito sobrevive a reimport de la lista (misma lista reimportada = favoritos intactos).

### F5. Búsqueda
- Por texto sobre `norm_name` (lowercase, acentos/símbolos normalizados), indexada.
- **Aceptación:** resultados ≤ ~200 ms para 10k canales; navegación D-pad completa.

### F6. EPG XMLTV básico
- Origen: EPG/`url-tvg` de la lista (uno por lista) o `xmltv.php` del portal Xtream; mapeo por `tvg-id` exacto; fallback léxico por nombre normalizado (marcado como "aproximado" en la UI).
- Ingesta por streaming SAX con ventana temporal (ahora−12h…+60h), lotes a DB; refresco manual + WorkManager cada 6 h (backoff ante error).
- Visualización: tira "Ahora/Siguiente" en el canal (donde exista), ficha de canal con programación del día, y overlay del Player con programa actual.
- **Aceptación:** XMLTV con 50 000 programas (~50 MB) ingierte en ≤30 s sin ANR y con la RAM de la app estable (la memoria vive en el parser por streaming + DB, no en objetos); guía visible en menos de 1 s tras abrir la ficha (consulta indexada); la poda automática mantiene ≤ ~5 días de datos.

### F7. Ajustes y "buenas formas"
- Limpiar datos (EPG/logos/todo), refresh de listas, lapso de buffer, UA, "acerca de" con disclaimer y versión.
- Sin ads, sin telemetría, sin permisos de red más allá de INTERNET; sin ACCESS_NETWORK_STATE innecesario.

### F8. Seguridad — modelo de amenazas y controles (requisito duro)

Postura: la app **no es punto de entrada** para pagar nada, ni hacia otras apps del dispositivo, ni hacia la red local. En Fire TV, el dinero (cuenta Amazon) vive fuera del sandbox de cualquier app: la cuenta está protegida por el sandbox de Android y nuestras defensas garantizan que el riesgo no crece por usar esta app.

| Amenaza | Control |
|---|---|
| Lista maliciosa descargada (payload enorme/zip-bomb, agotar memoria) | Límite de tamaño por archivo: M3U ≤ 30 MB, XMLTV ≤ 120 MB con ventana temporal aplicada en el parser (fuera de ventana no se inserta); timeouts de red cortos (15 s conectar, 30 s leer); aborto limpio de la descarga al superar el límite |
| URLs peligrosas dentro de listas (`file://`, `intent://`, `javascript:`, `content://`, redirect a LAN interna) | **Allowlist de esquemas**: media y logos solo `http(s)://`; el resto de esquemas se rechaza antes de tocar red o decoder; ninguna URL de lista despierta Activity/Service alguno |
| Tráfico en claro (MITM) | TLS por defecto (`networkSecurityConfig`); **cleartext HTTP solo si el usuario lo activa en Ajustes** con advertencia explícita (mayor frecuencia en listas IPTV — decisión consciente, por defecto OFF) |
| Render de texto malicioso (EPG descripciones, nombres de canal) | **Sin WebView**. Siempre TextView texto plano: entidades decodificadas, sin `Html.fromHtml` con links ni `movement` — nada es clickeable desde texto (sin `autoLink`, sin `movementMethod`) |
| Intrusión/injection por componentes | Todo `android:exported="false"` salvo la única launcher Activity; **sin deep links ni custom schemes públicos**; sin `allowBackup` (secretos y DB fuera de backups del sistema); `fullBackupContent` explícito |
| Credenciales Xtream en reposo | AES-256-GCM con clave en **Android Keystore** (EncryptedSharedPreferences); jamás en logs ni en `savedInstanceState`; en pantalla de ajustes se muestra solo máscara |
| Código remoto / plugins | **Cero carga dinámica de código** (sin DexClassLoader, sin descarga de código/config ejecutable); la EPG solo son datos |
| Permisos excesivos | `INTERNET` único permiso; sin ACCESS_NETWORK_STATE, sin storage writers (SAF para archivos), sin notificaciones v0.1 |
| Escucha local de puertos / SSRF hacia LAN | La app **no abre ServerSocket/puertos**; solo salidas; la delegación de AceStream (futuro M3) se limitará explícitamente a 127.0.0.1:6878 |
| APK alterado por terceros | Distribución con **SHA-256 por release publicado** en el repo; R8 en release; `debuggable=false`; logs `Log.v/d` eliminados por R8 |
| Exposición de datos a terceros | Sin telemetría/analytics/crashlytics; sin ads; URLs del usuario nunca enviadas fuera de su flujo de reproducción/importación |

**Aceptación (observable):** `aapt`/`apkanalyzer dump` del APK muestra: permisos = [`android.permission.INTERNET`], todos los componentes con exported=false salvo launcher, `usesCleartextTraffic` inexistente (networkSecurityConfig activo) y ninguna referencia a WebView/DexClassLoader en el código R8; importar una lista con `intent://` o `file://` la rechaza con error y no abre nada; con el ajuste cleartext OFF, una URL `http://` falla con mensaje claro y con ON funciona; credenciales guardadas ilegibles por adb (`run-as cat` muestra blob cifrado).

### 6.7 Criterios de rendimiento (duros y medibles)


| Criterio | Objetivo | Cómo se mide |
|---|---|---|
| Arranque en frío | ≤ 2,0 s hasta Home | `adb shell am start -W -n com.tvplayer.app/.MainActivity` (TotalTime) |
| RAM durante reproducción | ≤ 150 MB PSS total | `adb shell dumpsys meminfo com.tvplayer.app` a los 5 min de reproducción |
| Estabilidad PSS en 30 min | ±10% | dumpsys repetido 3× |
| Jank navegando lista grande | ≤5% frames fuera de deadline | `dumpsys gfxinfo com.tvplayer.app reset` + 60 s scroll + lectura |
| Jank scroll táctil (móvil/tableta) | mismo objetivo | mismo `gfxinfo` con gesto de scroll, emulador/dispositivo móvil |
| Zapping (primer frame) | ≤ 2,5 s | cronómetro sobre logcat (tags Media3/audio) |
| Import M3U 10k | ≤ 5 s | log de import (t0→t1 del servicio) |
| Ingesta EPG 50k prog. | ≤ 30 s, sin ANR | log + `logcat -d -b events \| grep anr` |
| Tamaño APK | ≤ 15 MB (R8) | compilación release + `ls -la` del APK |

Dispositivo de referencia: **el Fire TV antiguo del propio usuario** (Fire OS 5/6) — la máquina más lenta y además la real de destino; si durante M1 se detecta que solo hay Fire OS 6+, los criterios se miden igualmente ahí y se registra la versión exacta de Fire OS (Ajustes → Mi Fire TV → Acerca de). Todos los criterios de volumen se miden con datos **sintéticos** del repo; nada del proveedor real se empaqueta ni redistribuye.

Método de pruebas:
1. **`tools/` generador de fixtures sintéticos en repo**: M3U de 10 000 canales (grupos, logos, atributos raros, duplicados) y XMLTV de 50 000 programas; se sirven por HTTP en la LAN desde el PC o se importan como archivo, de forma reproducible.
2. **Streams de prueba de vídeo**: streams HLS de muestra (gratuitos, de proveedores de test) para cronometrar zapping y estabilidad; donde no haya disponibilidad, ese criterio queda "best-effort" y se marca así en el registro de resultados.

### 6.8 Texto de publicación (listo para pegar, ES/EN)
> ES: "TVPlayer es un reproductor de listas multimedia para Android TV, Fire TV y móvil/tableta Android. **No incluye, aloja ni distribuye contenido**: reproduce los canales y vídeos de las listas M3U/EPG que tú añades (proveedor o playlist propia)."
> EN: "TVPlayer is a playlist player for Android TV, Fire TV and Android phones/tablets. **It includes, hosts or distributes no content**: it plays the channels and videos from the M3U/EPG lists you add (your own provider or playlist)."

## 7. Verificación en dispositivos reales
Script `scripts/perf_check.sh` (bash, corre con adb desde Windows/mac/Linux) que ejecuta: arranque `am start -W`, `dumpsys meminfo`, `gfxinfo` con navegación de 60 s, `logcat -b events` para ANR, y volcado de resultados en tabla. Además, prueba manual: reproducción de 30 min con el stick conectado a la red Wi-Fi real del hogar.

## 8. Hitos

- **M0 — andamiaje** (≤1 día): proyecto Gradle compila, tema/base dual TV/táctil, Room + parsers con unit tests JVM (fixtures pequeños), clave LEANBACK en manifest, app abre Home vacía con disclaimer (TV y móvil).
- **M1 — núcleo reproductor**: F1–F5 completos (incluido portal Xtream), fixtures sintéticos + generador, APK sideloadable; orden interno: primero flujo TV con D-pad, al cierre la variante táctil; primer pase de criterios de rendimiento sobre el stick real del usuario.
- **M2 — EPG básico**: F6 XMLTV + ficha + overlay + WorkManager + poda (TV y móvil); segundo pase de criterios.
- **M3 — post-v1 (candidato)**: VOD heurístico por grupos (`CINE/MOVIES/SERIES/PELIS…`) con marcador explícito de "sin metadatos garantizados"; soporte AceStream por delegación (tras validar ToS); preview PiP opt-in (desactivada por defecto); grid EPG si hay demanda.

## 9. Riesgos y limitaciones declaradas

1. **EPG depende de la lista**: sin `tvg-id`/XMLTV, la guía no existe; el fallback por nombre es aproximado y se marca así. No inventamos metadatos.
2. **Sin vista previa v1**: coste de un 2.º decodificador en 1 GB es real (tearing, OOM en sticks); post-v1 PiP opt-in.
3. **VOD**: clasificación por heurística de nombres de grupo; las listas no prometen metadatos (sin TMDB por defecto; opt-in posterior posible).
4. **rtmp/udp**: no soportados en v0.1 (error claro); añadir rtmp luego cuesta ~4 MB (Netty) — solo si las listas del usuario lo exigen.
5. **AceStream**: delegación pendiente de ToS y viable solo con recursos ajustados; el engine consume fuera de la app.
6. **minSdk 21** amplía compatibilidad (incluye el stick antiguo del usuario) pero exige desugaring y tolerancia a Fire OS 5: los criterios de rendimiento medidos ahí son el peor caso real.
7. Aviso legal de plataforma: las stores exigen que las apps de este tipo no distribuyan contenido; la app ya es reproductora pura, pero la descripción publicada debe mantener la aclaración exacta del §6.8.
8. **Seguridad residual** honesta: F8 protege la app como superficie de ataque, pero no protege de un dispositivo ya comprometido (malware ajeno sideload en el mismo Fire TV), de un portal que entregue un stream con exploits de codec (esa barrera es del sistema Android, no de la app), ni del cleartext que el usuario activa a conciencia para su proveedor.
