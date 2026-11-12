package com.tvplayer.app.data.net

import android.content.Context
import android.content.SharedPreferences

/**
 * Credenciales del proxy (S1). Mismo patrón que SecureCredentials (Task 12):
 * lógica sobre un SharedPreferences inyectado (testeable en Robolectric);
 * en producción, cifrado con Keystore vía [createEncrypted].
 *
 * El usuario/pass NUNCA viajan a logs: esta clase solo los entrega al
 * Authenticator del OkHttp compartido.
 */
class ProxyCreds(private val prefs: SharedPreferences) {

    fun save(username: String, password: String) {
        prefs.edit()
            .putString(KEY_USER, username)
            .putString(KEY_PASS, password)
            .apply()
    }

    /** null si no hay par completo guardado. */
    fun load(): Pair<String, String>? {
        val user = prefs.getString(KEY_USER, null) ?: return null
        val pass = prefs.getString(KEY_PASS, null) ?: return null
        return user to pass
    }

    fun clear() {
        prefs.edit().remove(KEY_USER).remove(KEY_PASS).apply()
    }

    companion object {
        const val FILE = "proxy_creds"
        private const val KEY_USER = "proxy_user"
        private const val KEY_PASS = "proxy_pass"

        /** Constructor de producción: EncryptedSharedPreferences con Keystore. */
        // W4: MasterKey/EncryptedSharedPreferences.create de security-crypto 1.1.0
        // están deprecated sin sustituto que conserve el mismo Keystore + esquemas;
        // se mantienen a propósito (sin migración, sin tocar esquemas/alias/FILE) y
        // el aviso se suprime solo en esta función.
        @Suppress("DEPRECATION")
        fun createEncrypted(appContext: Context): ProxyCreds {
            val masterKey = androidx.security.crypto.MasterKey.Builder(appContext, androidx.security.crypto.MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                appContext,
                FILE,
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return ProxyCreds(prefs)
        }
    }
}
