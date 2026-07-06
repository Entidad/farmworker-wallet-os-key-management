# argon2 derivation equivalence harness (2.0.1 → 4.0.0)

Proves that upgrading `react-native-argon2` from **2.0.1** to **4.0.0** does **not** change
the seed-phrase → key derivation. If it did, re-provisioned keys after the sensitive-info
migration would silently be the *wrong* keys — catastrophic for a wallet.

## Why two phases (not a side-by-side diff)

Both versions ship the **same** native identifiers (pod `RNArgon2`, Android package
`com.poowf.argon2`, class `RNArgon2Module`, JS `NativeModules.RNArgon2`), so they **cannot
co-exist in one app build**. Instead we capture a golden from the current app and assert the
upgraded app reproduces it. argon2 is deterministic given a provided salt, so captures are
reproducible across builds/devices — the two-phase compare is valid.

## Files

| File | Runs where | Purpose |
| --- | --- | --- |
| `vectors.json` | reference | The fixed test vectors + rationale. Documentation of record. |
| `jsa_argon2_equivalence_probe.js` | on-device (Mendix action) | Runs the vectors through the installed argon2, returns a JSON string. Vectors are embedded so it's self-contained. |
| `argon2Probe.js` | on-device (plain RN) | Same logic as a framework-agnostic function, if you run it in a standalone RN app instead of Mendix. |
| `compare-argon2.mjs` | dev laptop (Node) | Diffs a golden capture vs a candidate capture. Exit 0 = safe, 1 = drift. |

## Procedure

### Phase 1 — capture the golden on the CURRENT app (argon2 2.0.1)

Do this on the existing Studio Pro 10.24 app **before** upgrading anything.

1. Add `jsa_argon2_equivalence_probe.js` as a JS action in the KeyManagement module
   (add it in Studio Pro so the action interface is generated; it takes no params, returns String).
2. Call it from a throwaway nanoflow behind a button; bind the returned String to a text area
   (or log it). Run the app on a **real iOS device** and a **real Android device** (not just
   simulators — Secure Enclave / StrongBox and biometric-backed paths differ on real hardware).
3. Copy each device's JSON output to your laptop:
   - `golden-ios-2.0.1.json`
   - `golden-android-2.0.1.json`

> Keep these goldens in version control. They are the ground truth of your current production
> derivation.

### Phase 2 — capture the candidate on the UPGRADED app (argon2 4.0.0)

After the Studio Pro 11.12 / RN 0.84.1 / argon2 4.0.0 environment is stood up, with the
**identical** vectors:

1. Run the same probe on the same two physical devices.
2. Save:
   - `candidate-ios-4.0.0.json`
   - `candidate-android-4.0.0.json`

### Phase 3 — diff on your laptop

```sh
node compare-argon2.mjs golden-ios-2.0.1.json     candidate-ios-4.0.0.json
node compare-argon2.mjs golden-android-2.0.1.json candidate-android-4.0.0.json
```

- **exit 0 / RESULT: SAFE** on both platforms → derivation is stable; the seed-phrase
  re-provisioning plan is sound.
- **exit 1 / RESULT: STOP** on either platform → a must-match vector drifted. Do **not** ship.
  iOS is the likely offender (CatCrypto → Argon2Swift engine swap); check the argon2 version
  (0x10 vs 0x13) and parameter mapping before going further.

## Read the CONTROL line

`CONTROL-hex-salt-footgun` is **expected to diverge** — that's its pass condition. It documents
why you must never set `saltEncoding: 'hex'` during the upgrade: 2.0.1 consumes the salt as its
64 utf8 bytes, 4.0.0 would decode it to 32 hex bytes, changing every derived key. If this control
ever *matches*, something is off and it deserves investigation.
