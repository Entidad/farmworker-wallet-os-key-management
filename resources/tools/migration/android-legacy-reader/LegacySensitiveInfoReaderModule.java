package fwos.keymanagement.migration;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * One-shot migration reader for values written by react-native-sensitive-info 6.0.0-alpha.9
 * on Android, via its NON-biometric path (touchID:false).
 *
 * Replicates exactly alpha.9's decrypt():
 *   - value read from SharedPreferences(prefsName, MODE_PRIVATE).getString(key)  -> Base64 string
 *   - AES/GCM/NoPadding, key = AndroidKeyStore alias "MySharedPreferenceKeyAlias" (AES SecretKey),
 *     GCMParameterSpec(128, FIXED_IV = {0,1,2,3,4,5,6,7,8,9,0,1})
 *   - Base64.decode -> Cipher.doFinal -> UTF-8 plaintext
 *
 * The AES key is hardware-backed and non-exportable, so this decrypt MUST run in native code that
 * invokes the Keystore; it cannot be done in JS. Reads only; never writes to the old store.
 *
 * Legacy note: on pre-API-23 devices alpha.9 used RSA/ECB/PKCS1Padding under the same alias.
 * That path is intentionally omitted (production is modern Android); add it only if needed.
 */
public class LegacySensitiveInfoReaderModule extends ReactContextBaseJavaModule {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "MySharedPreferenceKeyAlias";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final byte[] FIXED_IV = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1};
    private static final String DEFAULT_PREFS = "shared_preferences";

    public LegacySensitiveInfoReaderModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "LegacySensitiveInfoReader";
    }

    /**
     * Resolves the decrypted plaintext for key, or null when there is nothing to migrate
     * (entry absent, or the old Keystore alias is gone after an uninstall/reinstall).
     * Rejects only on an actual decrypt failure.
     */
    @ReactMethod
    public void readLegacy(String prefsName, String key, Promise promise) {
        try {
            String name = (prefsName == null || prefsName.isEmpty()) ? DEFAULT_PREFS : prefsName;
            SharedPreferences prefs =
                    getReactApplicationContext().getSharedPreferences(name, Context.MODE_PRIVATE);
            String stored = prefs.getString(key, null);
            if (stored == null || stored.isEmpty()) {
                promise.resolve(null); // nothing stored under this key
                return;
            }

            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                promise.resolve(null); // old key destroyed (fresh install / reinstall) -> unrecoverable
                return;
            }

            Key k = keyStore.getKey(KEY_ALIAS, null);
            if (!(k instanceof SecretKey)) {
                // Alias exists but isn't an AES SecretKey (would be the pre-API-23 RSA path).
                promise.reject("LEGACY_KEY_TYPE",
                        "Keystore alias " + KEY_ALIAS + " is not an AES SecretKey (legacy RSA path not supported).");
                return;
            }
            SecretKey secretKey = (SecretKey) k;

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, FIXED_IV));
            byte[] plain = cipher.doFinal(Base64.decode(stored, Base64.DEFAULT));
            promise.resolve(new String(plain, StandardCharsets.UTF_8));
        } catch (Exception e) {
            promise.reject("LEGACY_READ_FAILED", e.getMessage(), e);
        }
    }

    /** Optional: remove the old entry after a confirmed successful migration. */
    @ReactMethod
    public void deleteLegacy(String prefsName, String key, Promise promise) {
        try {
            String name = (prefsName == null || prefsName.isEmpty()) ? DEFAULT_PREFS : prefsName;
            getReactApplicationContext().getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit().remove(key).apply();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("LEGACY_DELETE_FAILED", e.getMessage(), e);
        }
    }
}
