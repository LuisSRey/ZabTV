package com.tvplayer.app.data.portal

import android.content.Context
import com.tvplayer.app.core.UrlPolicer
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.ListEntity
import com.tvplayer.app.data.repo.ImportResult
import com.tvplayer.app.data.repo.ImportService
import com.tvplayer.app.data.repo.ImportSource
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Portal Xtream, fase Live (guía S2): auth contra `player_api.php` con cap de
 * 2 MB + importación de la playlist que SOLO conserva Live (los grupos
 * VOD/MOVIES/SERIES se podan tras importar; nunca tocan el player).
 *
 * Transporte: el [client] es SIEMPRE el OkHttp compartido de AppContainer
 * (mismo CleartextGate en [0] que import/EPG/logos/playback). Sin segundo
 * cliente, sin credenciales en logs ni en mensajes (spec F8).
 */
class XtreamRepository(
    private val db: AppDatabase,
    private val client: OkHttpClient,
    private val appContext: Context,
    private val creds: SecureCredentials,
    private val maxAuthBytes: Long = DEFAULT_MAX_AUTH_BYTES,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        /** Cap del auth JSON: la respuesta es KB; 2 MB corta zip-bombs (spec F8). */
        const val DEFAULT_MAX_AUTH_BYTES = 2_000_000L
    }

    /** `player_api.php?username=U&password=P` sobre el host normalizado. */
    fun authUrl(host: String, username: String, password: String): String {
        val base = XtreamLogin.normalizeHost(host)
        val enc = { v: String -> URLEncoder.encode(v, Charsets.UTF_8.name()) }
        return "$base/player_api.php?username=${enc(username)}&password=${enc(password)}"
    }

    /**
     * Auth contra el portal. Nunca lanza por credenciales malas (devuelve
     * `authenticated=false`); lanza [XtreamException] — SIN credenciales en el
     * mensaje — ante red rota, cap excedido o JSON ilegible.
     */
    suspend fun checkAuth(host: String, username: String, password: String): XtreamAuth =
        withContext(ioDispatcher) {
            val url = authUrl(host, username, password)
            UrlPolicer.requireAllowedHttpUrl(url)
            val body = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.code == 401 || resp.code == 403) {
                        resp.body?.close()
                        return@withContext XtreamAuth(authenticated = false, status = "HTTP ${resp.code}")
                    }
                    if (!resp.isSuccessful) {
                        resp.body?.close()
                        throw XtreamException("portal no responde (HTTP ${resp.code})")
                    }
                    val raw = resp.body ?: throw XtreamException("portal sin respuesta")
                    if (raw.contentLength() > maxAuthBytes) {
                        throw XtreamException("respuesta de auth mayor que el límite (${raw.contentLength()} bytes)")
                    }
                    readCapped(raw.byteStream())
                }
            }.getOrElse { e ->
                if (e is XtreamException) throw e
                throw XtreamException("portal inaccesible (${e.javaClass.simpleName})")
            }
            parseAuth(body)
        }

    /**
     * Importa un portal: auth primero (creds inválidas → rejected SIN tocar
     * DB), guarda creds cifradas ANTES de red, importa la playlist y poda
     * VOD/MOVIES/SERIES. La EPG la refresca el caller (worker 6 h + auto tras
     * import, como las M3U).
     */
    suspend fun importPortal(
        listId: Long?,
        name: String,
        host: String,
        username: String,
        password: String,
        userAgent: String? = null,
        importService: ImportService = ImportService(appContext, db, client),
    ): ImportResult {
        val t0 = System.currentTimeMillis()
        val auth = try {
            checkAuth(host, username, password)
        } catch (e: IllegalArgumentException) {
            return ImportResult(
                listId = listId ?: -1, saved = 0, skipped = 0, errorLines = 0,
                log = listOf("host de portal inválido"), durationMs = System.currentTimeMillis() - t0,
                rejected = true, message = "host de portal inválido",
            )
        } catch (e: XtreamException) {
            return ImportResult(
                listId = listId ?: -1, saved = 0, skipped = 0, errorLines = 0,
                log = listOf(e.message.orEmpty()), durationMs = System.currentTimeMillis() - t0,
                rejected = true, message = e.message,
            )
        }
        if (!auth.authenticated) {
            val prev = listId?.let { db.channelDao().countForList(it) } ?: 0
            return ImportResult(
                listId = listId ?: -1, saved = prev, skipped = 0, errorLines = 0,
                log = listOf("credenciales inválidas o portal inaccesible"),
                durationMs = System.currentTimeMillis() - t0,
                rejected = true, message = "credenciales inválidas o portal inaccesible",
            )
        }
        val playlistUrl = XtreamLogin.playlistUrl(host, username, password)
        val epgUrl = XtreamLogin.epgUrl(host, username, password)
        val targetId = listId ?: db.listDao().upsert(
            ListEntity(name = name.ifBlank { host }, sourceUrl = "", epgUrl = epgUrl, portalHost = host, status = "importing"),
        )
        if (listId != null) {
            val old = db.listDao().get(targetId)
            if (old != null) {
                db.listDao().upsert(
                    old.copy(
                        name = name.ifBlank { old.name },
                        epgUrl = epgUrl, portalHost = host, status = "importing",
                    ),
                )
            }
        }
        // Credenciales cifradas ANTES de cualquier red (nunca a logs ni Toasts).
        creds.save(targetId, username, password)
        val result = importService.import(targetId, ImportSource.HttpSource(playlistUrl, userAgent))
        if (result.rejected) return result
        // Solo Live en la guía (S6): VOD/MOVIES/SERIES fuera de DB y del player.
        val vodRemoved = db.channelDao().deleteVodGroups(targetId)
        val line = "solo Live: $vodRemoved VOD excluidos"
        return result.copy(log = result.log + line, message = "${result.message} · $line")
    }

    /**
     * Reimportación manual de una lista de portal (S6): relee host + creds
     * guardadas y delega en [importPortal]. Listas M3U nunca pasan por aquí.
     */
    suspend fun reimport(
        listId: Long,
        userAgent: String? = null,
        importService: ImportService = ImportService(appContext, db, client),
    ): ImportResult {
        val entity = db.listDao().get(listId)
            ?: return ImportResult(listId, 0, 0, 0, listOf("lista inexistente"), 0, rejected = true, message = "lista inexistente")
        val host = entity.portalHost
            ?: return ImportResult(listId, 0, 0, 0, listOf("no es una lista de portal"), 0, rejected = true, message = "no es una lista de portal")
        val pair = creds.load(listId)
            ?: return ImportResult(listId, 0, 0, 0, listOf("sin credenciales guardadas"), 0, rejected = true, message = "sin credenciales guardadas")
        return importPortal(listId, entity.name, host, pair.first, pair.second, userAgent, importService)
    }

    // ---------- internas ----------

    private fun readCapped(stream: java.io.InputStream): String {
        stream.use { src ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8_192)
            var total = 0L
            while (true) {
                val n = src.read(buf)
                if (n <= 0) break
                total += n
                if (total > maxAuthBytes) {
                    throw XtreamException("respuesta de auth mayor que el límite ($total bytes)")
                }
                out.write(buf, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    private fun parseAuth(body: String): XtreamAuth {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            throw XtreamException("respuesta de auth ilegible")
        }
        val info = json.optJSONObject("user_info")
        val raw = info?.let {
            if (it.has("auth")) it.opt("auth")?.toString().orEmpty() else ""
        }.orEmpty()
        val ok = raw == "1" || raw.equals("true", ignoreCase = true)
        val status = info?.optString("status")?.takeIf { it.isNotBlank() }
        return XtreamAuth(authenticated = ok, status = status)
    }
}

/** Resultado del auth: `false` = credenciales malas o 401/403, sin excepción. */
data class XtreamAuth(val authenticated: Boolean, val status: String? = null)

/** Fallo de transporte/límite/formato del portal. Jamás lleva credenciales. */
class XtreamException(message: String) : RuntimeException(message)
