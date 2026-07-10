# Farmworker Wallet OS — Key Management

A [Mendix](https://www.mendix.com/) module that securely stores a user's private keys on their
own device — in the browser and in native mobile apps (iOS and Android) — and gates access behind
platform biometrics.

It is the key-custody component of Farmworker Wallet OS: the wallet's private keys live in the
device's hardware-backed secure storage (iOS Keychain / Android Keystore) rather than on a server,
and can be re-derived from the user's seed phrase.

---

## What it does

- **Device-local key storage.** Secrets are written to the iOS Keychain / Android Keystore on
  native, and to `localStorage` on the web, through one cross-platform action layer.
- **Biometric gating (optional).** Entries can require Face ID / Touch ID / fingerprint via a
  configurable access-control policy.
- **Seed-derived keys.** The wallet's keys are derived from a seed phrase with Argon2, so they can
  be reconstructed on a new device or after a reset.
- **Object storage over the keychain (`kcorm`).** A small "keychain ORM" persists whole Mendix
  objects (and their references) as a single JSON tree inside secure storage, so modeling logic can
  read/write objects to the secure enclave much like a database.

## How it works

The module is Mendix low-code (`KeyManagement.mpr`) plus JavaScript actions under
[`javascriptsource/keymanagement/actions`](javascriptsource/keymanagement/actions). Two layers sit
on top of the native secure-storage library:

| Layer | Actions | Purpose |
| --- | --- | --- |
| **Unified keychain API** | `jsa_setItem`, `jsa_getItem`, `jsa_hasItem`, `jsa_deleteItem`, `jsa_getAllItems`, `jsa_deleteAllItems`, `jsa_isSensorAvailable`, `jsa_hasEnrolledFingerprints`, `jsa_getSupportedSecurityLevels` | Thin cross-platform wrappers over `react-native-sensitive-info` 6.1.x |
| **`kcorm` object store** | `jsa_kcorm_put` / `get_one` / `get_all` / `query` / `rm` (+ `_recursive` variants) | Serialize Mendix objects to/from a single JSON tree in secure storage |

Native crypto is provided by two React Native modules: `react-native-sensitive-info` (Keychain /
Keystore access) and `react-native-argon2` (the seed → key derivation). On the web, the same
actions fall back to `localStorage`.

## Project structure

| Path | Contents |
| --- | --- |
| `KeyManagement.mpr` | The Mendix project (open in Studio Pro) |
| `javascriptsource/keymanagement/` | JS actions (keychain layer, `kcorm`) + their npm dependencies |
| `javasource/` | Generated Java proxies + custom Java actions |
| `resources/nativeTemplate/` | The Mendix native mobile template used to build the iOS/Android apps |
| `resources/tools/migration/` | One-off migration tooling (argon2 equivalence harness, Android legacy-storage reader) |
| `widgets/`, `theme*/`, `nativemobile/` | Bundled Mendix Marketplace UI/widget content |

---

## Prerequisites

- **Mendix Studio Pro 11.12** — download from the [Marketplace](https://marketplace.mendix.com/link/studiopro/).
- **Node** — the version pinned in [`resources/nativeTemplate/.nvmrc`](resources/nativeTemplate/.nvmrc) (currently **Node 24**). Use `nvm`.
- **Xcode** (iOS builds) and/or **Android Studio** (Android builds).
- Native template **v19.1.2** / **React Native 0.84.1** with the New Architecture enabled (shipped with the template in this repo).

## Quick start (run locally in the browser)

1. Clone this repository and open a terminal at the project root.
2. Install the JS-action dependencies:
   ```sh
   cd javascriptsource/keymanagement
   yarn install
   ```
3. Open `KeyManagement.mpr` in **Studio Pro 11.12**.
4. Click **Run → Run Locally**, then **View** to open the app in your browser.
5. Log in with a seeded demo user, e.g. `demo_user`

## Build & run the native mobile app

From the native template:

```sh
cd resources/nativeTemplate

# Use the pinned Node version
nvm install    # installs the version from .nvmrc (Node 24)
nvm use

# Install dependencies and generate the native projects
npm install --legacy-peer-deps
npm run configure
```

### iOS (Xcode)

```sh
cd ios
pod install --repo-update
```
Open `ios/*.xcworkspace` in Xcode and run on a simulator or device.

### Android (Android Studio)

Open `resources/nativeTemplate/android` in Android Studio, let Gradle sync, then run on an emulator
or device.

> Run the Mendix app locally (step above) first; the native app connects to that running instance.

## Native dependency stack

| Dependency | Version | Role |
| --- | --- | --- |
| `react-native-sensitive-info` | `6.1.4` | Keychain / Keystore secure storage (Nitro module) |
| `react-native-nitro-modules` | `0.35.6` | Runtime required by sensitive-info 6.1.x |
| `react-native-argon2` | `4.0.0` | Argon2 seed → key derivation |
| `json-query` | `^2.2.2` | Query language for `jsa_kcorm_query` |

These are declared in [`javascriptsource/keymanagement/package.json`](javascriptsource/keymanagement/package.json)
and mirrored in the module's `nativeDependencies` manifest so the Mendix native build bundles them.

## Data migration (upgrading from older builds)

The keychain layer was migrated from `react-native-sensitive-info@6.0.0-alpha.9` (legacy bridge) to
`6.1.4` (Nitro). The storage formats are not wire-compatible on Android, so upgrading users' data is
handled deliberately:

- **iOS** — 6.1.4 reads entries written by the old library directly; no migration needed.
- **Android** — a one-shot native reader in
  [`resources/tools/migration/android-legacy-reader/`](resources/tools/migration/android-legacy-reader)
  decrypts values written by the old library (auto-detecting the biometric and non-biometric formats)
  so they can be re-stored via 6.1.4. See that folder's README for integration steps.
- Because the keys are seed-derivable, re-provisioning from the seed phrase on first launch is the
  fallback path when on-device data can't be recovered.

The argon2 derivation was verified byte-identical across the upgrade using the harness in
[`resources/tools/migration/argon2/`](resources/tools/migration/argon2).

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

See [LICENSE](LICENSE).
