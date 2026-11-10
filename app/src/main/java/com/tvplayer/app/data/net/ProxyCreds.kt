package com.tvplayer.app.data.net

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
        fun createEncrypted(appContext: Context): ProxyCreds {
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
            return ProxyCreds(prefs)
        }
    }
}
