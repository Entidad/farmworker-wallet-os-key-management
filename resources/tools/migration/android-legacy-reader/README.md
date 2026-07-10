# Android legacy seed migration bridge

Reads the **seed phrase** that the old `react-native-sensitive-info@6.0.0-alpha.9` wrote to
Android SharedPreferences (non-biometric path), so the upgraded app can re-store it via 6.1.4
and re-derive keyA/keyB. **Android only** — on iOS, 6.1.4 already reads the old keychain data
directly, so no bridge is needed there. The kcorm demo data is out of scope.

## Why native code is required
The old value is AES-encrypted with a **hardware-backed, non-exportable** AndroidKeyStore key
(`MySharedPreferenceKeyAlias`). You can only *use* that key through a Keystore `Cipher` call in
native code — the key bytes can never reach JS. So the decrypt lives in a small Kotlin module.

## Exact format being decrypted (alpha.9 non-biometric path)
| | |
|---|---|
| Store | `getSharedPreferences(prefsName, MODE_PRIVATE).getString(key)` → Base64 string |
| Key | AndroidKeyStore alias `MySharedPreferenceKeyAlias` (AES `SecretKey`) |
| Cipher | `AES/GCM/NoPadding`, `GCMParameterSpec(128, FIXED_IV={0,1,2,3,4,5,6,7,8,9,0,1})` |
| Decode | `Base64.decode` → `Cipher.doFinal` → UTF-8 plaintext |

## Files
| File | Role |
|---|---|
| `LegacySensitiveInfoReaderModule.java` | The native reader (`readLegacy`, `deleteLegacy`) |
| `LegacyMigrationPackage.java` | ReactPackage registering the module |
| `jsa_legacy_android_read.js` | Mendix JS action wrapping the native call (Android-guarded) |

## Integration into the Mendix native template

1. Copy both `.java` files into the native template's Android source tree at the path that
   **matches the package** `fwos.keymanagement.migration` (Java requires package == directory):
   `resources/nativeTemplate/android/app/src/main/java/fwos/keymanagement/migration/`
   (These are Java to match the Java `MainApplication`. The app module does support Kotlin, so a
   `.kt` version also works — but keep only ONE version of each class to avoid a duplicate-class error.)
2. Register the package in the app's Java `MainApplication.java`, inside `getPackages()`:
   - add the import at the top: `import fwos.keymanagement.migration.LegacyMigrationPackage;`
   - add before `return packages;`: `packages.add(new LegacyMigrationPackage());`
     (Java requires `new` — omitting it makes the compiler read `LegacyMigrationPackage()` as a
     missing method call, which is what raises the errors.)
3. Create the `jsa_legacy_android_read` JS action in Studio Pro with this interface, then paste the
   import + USER CODE from `jsa_legacy_android_read.js`:
   - `key` : String
   - `sharedPreferencesName` : String (optional)
   - returns String
4. Rebuild the Android native app.

> Pass the **exact** `key` and `sharedPreferencesName` the old app used to store the seed. If the
> old write used the default prefs file, leave `sharedPreferencesName` empty → `"shared_preferences"`.

## One-time migration flow (compose in a startup microflow)

On first launch after the upgrade:

1. `jsa_hasItem(seedKey, service)` on the **new** 6.1.4 store.
   - `true` → already migrated, skip.
2. Else `jsa_legacy_android_read(oldSeedKey, oldPrefsName)`:
   - returns the decrypted seed → continue.
   - returns `null` → nothing to migrate (fresh install, or key gone): fall through to normal
     onboarding (user enters their seed manually).
3. With the recovered seed: `jsa_setItem(seedKey, seed, service, accessControl, …)` to persist it
   in the new store (choose the `accessControl` you want going forward), and/or re-derive keyA/keyB
   via `jsa_argon2` and store those.
4. Optional: `deleteLegacy(oldPrefsName, oldSeedKey)` to remove the stale old entry.

## Preconditions & caveats
- Works only for an **in-place app update** (same Android package name) — the old Keystore key
  survives updates but is destroyed on uninstall/reinstall (in which case the SharedPreferences are
  gone too, so there's nothing to recover — the `null` path handles this).
- Reader is **read-only** against the old store; it never writes there.
- Non-biometric path only. If any production seed was written with `touchID:true`, it uses a
  different alias (`MyAesKeyAlias`), `AES/CBC/PKCS7Padding`, a random IV stored as
  `Base64(IV)]Base64(cipher)`, and requires a BiometricPrompt — tell me and I'll add that variant.
