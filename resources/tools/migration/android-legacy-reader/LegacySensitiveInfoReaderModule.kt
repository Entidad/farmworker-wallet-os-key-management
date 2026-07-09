package fwos.keymanagement.migration

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * One-shot migration reader for values written by react-native-sensitive-info 6.0.0-alpha.9
 * on Android, via its NON-biometric path (touchID:false).
 *
 * Replicates exactly alpha.9's `decrypt()`:
 *   - value read from SharedPreferences(prefsName, MODE_PRIVATE).getString(key)  -> Base64 string
 *   - AES/GCM/NoPadding, key = AndroidKeyStore alias "MySharedPreferenceKeyAlias" (AES SecretKey),
 *     GCMParameterSpec(128, FIXED_IV = {0,1,2,3,4,5,6,7,8,9,0,1})
 *   - Base64.decode -> Cipher.doFinal -> UTF-8 plaintext
 *
 * The AES key is hardware-backed and non-exportable, so this decrypt MUST run in native code
 * that invokes the Keystore; it cannot be done in JS. Reads only; never writes to the old store.
 *
 * Legacy note: on pre-API-23 devices alpha.9 used RSA/ECB/PKCS1Padding under the same alias.
 * That path is intentionally omitted (production is modern Android); add it only if needed.
 */
class LegacySensitiveInfoReaderModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "MySharedPreferenceKeyAlias"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private val FIXED_IV = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1)
        private const val DEFAULT_PREFS = "shared_preferences"
    }

    override fun getName(): String = "LegacySensitiveInfoReader"

    /**
     * Resolves the decrypted plaintext for [key], or null when there is nothing to migrate
     * (entry absent, or the old Keystore alias is gone after an uninstall/reinstall).
     * Rejects only on an actual decrypt failure.
     */
    @ReactMethod
    fun readLegacy(prefsName: String?, key: String, promise: Promise) {
        try {
            val name = if (prefsName.isNullOrEmpty()) DEFAULT_PREFS else prefsName
            val prefs: SharedPreferences =
                reactApplicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            val stored = prefs.getString(key, null)
            if (stored.isNullOrEmpty()) {
                promise.resolve(null) // nothing stored under this key
                return
            }

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                promise.resolve(null) // old key destroyed (fresh install / reinstall) -> unrecoverable
                return
            }

            val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                ?: run {
                    // Alias exists but isn't an AES SecretKey (would be the pre-API-23 RSA path).
                    promise.reject(
                        "LEGACY_KEY_TYPE",
                        "Keystore alias $KEY_ALIAS is not an AES SecretKey (legacy RSA path not supported)."
                    )
                    return
                }

            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, FIXED_IV))
            val plain = cipher.doFinal(Base64.decode(stored, Base64.DEFAULT))
            promise.resolve(String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            promise.reject("LEGACY_READ_FAILED", e.message, e)
        }
    }

    /** Optional: remove the old entry after a confirmed successful migration. */
    @ReactMethod
    fun deleteLegacy(prefsName: String?, key: String, promise: Promise) {
        try {
            val name = if (prefsName.isNullOrEmpty()) DEFAULT_PREFS else prefsName
            reactApplicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().remove(key).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("LEGACY_DELETE_FAILED", e.message, e)
        }
    }
}
