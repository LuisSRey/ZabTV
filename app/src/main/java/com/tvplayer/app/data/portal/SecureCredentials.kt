package com.tvplayer.app.data.portal

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Credenciales de portal Xtream (Task 12). La lógica de claves vive sobre un
 * SharedPreferences inyectado (testeable en Robolectric); en producción se
 * construye cifrado con Keystore vía [createEncrypted].
 *
 * Claves por lista: ("u_"+listId, "p_"+listId). El user/pass nunca viajan a
 * logs: esta clase solo los entrega al constructor de URLs.
 *
 * Ruling (ledger): el Keystore real (AndroidKeyStore) no existe en JVM, así que
 * el blob cifrado no es ejercitable en unit tests; se verifica en dispositivo
 * (acceptance M1, Task 16). Los tests cubren roundtrip, aislamiento por lista
 * y limpieza sobre prefs real en disco.
 */
class SecureCredentials(private val prefs: SharedPreferences) {

    fun save(listId: Long, username: String, password: String) {
        prefs.edit()
            .putString(key(listId, "u"), username)
            .putString(key(listId, "p"), password)
            .apply()
    }

    /** null si esa lista no tiene el par completo guardado. */
    fun load(listId: Long): Pair<String, String>? {
        val user = prefs.getString(key(listId, "u"), null) ?: return null
        val pass = prefs.getString(key(listId, "p"), null) ?: return null
        return user to pass
    }

    fun clear(listId: Long) {
        prefs.edit().remove(key(listId, "u")).remove(key(listId, "p")).apply()
    }

    private fun key(listId: Long, prefix: String) = "${prefix}_$listId"

    companion object {
        const val FILE = "portal_creds"

        /** Constructor de producción: EncryptedSharedPreferences con Keystore. */
        fun createEncrypted(appContext: Context): SecureCredentials {
            val masterKey = MasterKey.Builder(appContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                appContext,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return SecureCredentials(prefs)
        }
    }
}
