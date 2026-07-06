// Framework-agnostic argon2 probe.
//
// Runs a set of fixed vectors through whatever version of `react-native-argon2`
// is installed in the current app, and returns a plain-JS result array that is
// safe to JSON.stringify, copy off the device, and diff with compare-argon2.mjs.
//
// This same function runs unchanged on the 2.0.1 app (to capture goldens) and on
// the 4.0.0 app (to produce the candidate) - argon2 is deterministic given a
// provided salt, so the outputs are reproducible across builds and devices.
//
// Usage (any RN context):
//   import argon2 from 'react-native-argon2';
//   import vectors from './vectors.json';
//   const results = await runArgon2Probe(argon2, vectors);
//   console.log(JSON.stringify({ platform: Platform.OS, results }, null, 2));

/**
 * @param {(password:string, salt:string, options:object)=>Promise<{rawHash:string, encodedHash:string}>} argon2
 * @param {{mustMatch?:Array, controls_expectedToDiverge?:Array}} vectors
 * @returns {Promise<Array<{id:string, ok:boolean, rawHash?:string, encodedHash?:string, error?:string}>>}
 */
export async function runArgon2Probe(argon2, vectors) {
	const all = [
		...((vectors && vectors.mustMatch) || []),
		...((vectors && vectors.controls_expectedToDiverge) || []),
	];
	const out = [];
	for (const v of all) {
		try {
			const { rawHash, encodedHash } = await argon2(v.password, v.salt, v.options || {});
			out.push({ id: v.id, ok: true, rawHash, encodedHash });
		} catch (e) {
			out.push({ id: v.id, ok: false, error: String(e && e.message ? e.message : e) });
		}
	}
	return out;
}
