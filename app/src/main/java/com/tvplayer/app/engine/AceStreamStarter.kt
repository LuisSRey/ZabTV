package com.tvplayer.app.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.acestream.engine.service.v0.IAceStreamEngine
import org.acestream.engine.service.v0.IAceStreamEngineCallback
import org.acestream.engine.service.v0.IStartEngineResponse

/**
 * Arranque del motor por la vía oficial (servicio bindeado, mismo protocolo
 * que acestream/android-service-client, MIT): bindea, registra callback,
 * arranca y espera al puerto HTTP. NO abre ninguna UI del motor, así que no
 * hay robo de foreground ni muerte por "remove task".
 *
 * Si el proxy ya escucha, el caller ni llama aquí (fast-path en el Fragment).
 */
object AceStreamStarter {

    const val SERVICE_ACTION = "org.acestream.engine.service.v0.IAceStreamEngine"

    /**
     * Candidatos a motor, AceServe primero (preferencia del usuario). La
     * elección final es por versionCode: si hay varios, gana el más nuevo
     * (igual que el ServiceClient oficial).
     */
    const val ACESERVE_PACKAGE = "org.free.aceserve"

    val KNOWN_PACKAGES = listOf(
        ACESERVE_PACKAGE,
        "org.acestream.media",
        "org.acestream.media.atv",
        "org.acestream.core",
        "org.acestream.core.atv",
        "org.acestream.engine",
        "org.acestream.node",
        "org.acestream.node.web",
    )

    /** Pura y testeable: AceServe primero si está; el resto por mayor versión. */
    fun orderCandidates(versions: Map<String, Long>): List<String> {
        val installed = versions.filter { it.value >= 0 }.keys
        val rest = installed.filter { it != ACESERVE_PACKAGE }
            .sortedByDescending { versions[it] }
        return listOfNotNull(ACESERVE_PACKAGE.takeIf { it in installed }) + rest
    }

    /** Pura y testeable: elige el candidato con mayor versión. */
    fun selectBest(candidates: List<String>, versionOf: (String) -> Long): String? {
        var best: String? = null
        var bestVer = -1L
        for (pkg in candidates.distinct()) {
            val v = try {
                versionOf(pkg)
            } catch (_: Exception) {
                -1L
            }
            if (v > bestVer) {
                bestVer = v
                best = pkg
            }
        }
        return best?.takeIf { bestVer >= 0 }
    }

    /** Orden de intento: AceServe primero si está; si no, el resto por versión. */
    fun orderedEnginePackages(pm: PackageManager): List<String> {
        val dynamic = try {
            pm.queryIntentServices(Intent(SERVICE_ACTION), PackageManager.MATCH_DEFAULT_ONLY)
                ?.map { it.serviceInfo.packageName }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        val versions = (KNOWN_PACKAGES + dynamic).distinct().associateWith { pkg ->
            try {
                val info = pm.getPackageInfo(pkg, 0)
                if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            } catch (_: Exception) {
                -1L
            }
        }
        return orderCandidates(versions)
    }

    fun selectEnginePackage(pm: PackageManager): String? =
        orderedEnginePackages(pm).firstOrNull()

    /**
     * Subconjunto bindeable: solo los que resuelven el intent del servicio.
     * AceServe no lo expone (verificado en dispositivo): va por UI manual.
     */
    fun servicePackages(pm: PackageManager): List<String> {
        val resolvers = try {
            pm.queryIntentServices(Intent(SERVICE_ACTION), PackageManager.MATCH_DEFAULT_ONLY)
                ?.map { it.serviceInfo.packageName }.orEmpty().toSet()
        } catch (_: Exception) {
            emptySet()
        }
        return orderedEnginePackages(pm).filter { it in resolvers }
    }

    /**
     * Bindea, arranca y espera el puerto HTTP del motor (defecto 6878).
     * Prueba los candidatos en orden (AceServe primero); devuelve el puerto
     * (>0) o 0 si ninguno fue posible. Siempre desbindea al salir.
     */
    suspend fun startAndWait(context: Context, timeoutMs: Long = 60_000): Int =
        withContext(Dispatchers.IO) {
            val pkgs = servicePackages(context.packageManager).take(3)
            if (pkgs.isEmpty()) return@withContext 0
            val deadline = System.currentTimeMillis() + timeoutMs
            for (pkg in pkgs) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 2_000) break
                // Nunca más de 25 s por candidato: si AceServe no expone el
                // servicio, se pasa al siguiente sin agotar el presupuesto.
                val port = tryStart(context, pkg, minOf(remaining, 25_000))
                if (port > 0) return@withContext port
            }
            0
        }

    private suspend fun tryStart(context: Context, pkg: String, timeoutMs: Long): Int =
        withContext(Dispatchers.IO) {
            val announced = AtomicInteger(0)
            var service: IAceStreamEngine? = null
            val callback = object : IAceStreamEngineCallback.Stub() {
                override fun onReady(listenPort: Int) {
                    announced.set(listenPort)
                }
                override fun onUnpacking() {}
                override fun onStarting() {}
                override fun onStopped() {}
                override fun onWaitForNetworkConnection() {}
                override fun onPlaylistUpdated() {}
                override fun onEPGUpdated() {}
                override fun onRestartPlayer() {}
                override fun onSettingsUpdated() {}
                override fun onAuthUpdated() {}
            }
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    try {
                        val svc = IAceStreamEngine.Stub.asInterface(binder)
                        service = svc
                        svc.registerCallbackExt(callback, true)
                        svc.startEngineWithCallback(object : IStartEngineResponse.Stub() {
                            override fun onResult(success: Boolean) {
                                if (!success) announced.set(-1)
                            }
                        })
                    } catch (_: Exception) {
                        announced.set(-1)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                }
            }
            val bound = try {
                context.bindService(Intent(SERVICE_ACTION).setPackage(pkg), conn, Context.BIND_AUTO_CREATE)
            } catch (_: Exception) {
                false
            }
            if (!bound) return@withContext 0
            try {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val a = announced.get()
                    if (a == -1) return@withContext 0
                    if (a > 0) return@withContext a
                    val port = try {
                        service?.httpApiPort ?: 0
                    } catch (_: Exception) {
                        0
                    }
                    if (port > 0) return@withContext port
                    delay(1_000)
                }
                0
            } finally {
                try {
                    service?.unregisterCallback(callback)
                } catch (_: Exception) {
                }
                try {
                    context.unbindService(conn)
                } catch (_: Exception) {
                }
            }
        }
}
