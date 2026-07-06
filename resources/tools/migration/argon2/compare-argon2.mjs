#!/usr/bin/env node
// Diff two argon2 probe captures and report whether the derivation is stable.
//
// Runs in any plain Node environment - it only compares strings, no native argon2.
//
// Usage:
//   node compare-argon2.mjs <golden.json> <candidate.json>
//
//   golden.json     = probe output captured on the CURRENT app  (react-native-argon2 2.0.1)
//   candidate.json  = probe output captured on the UPGRADED app (react-native-argon2 4.0.0)
//
// Each file is the JSON array produced by jsa_argon2_equivalence_probe / runArgon2Probe:
//   [ { "id": "...", "ok": true, "rawHash": "...", "encodedHash": "..." }, ... ]
//
// Exit code 0 = all mustMatch vectors identical (safe to proceed).
// Exit code 1 = at least one mustMatch vector drifted (STOP - keys would change).

import { readFileSync } from "node:fs";

function load(path) {
	try {
		return JSON.parse(readFileSync(path, "utf8"));
	} catch (e) {
		console.error(`ERROR: could not read/parse ${path}: ${e.message}`);
		process.exit(2);
	}
}

const [, , goldenPath, candidatePath] = process.argv;
if (!goldenPath || !candidatePath) {
	console.error("usage: node compare-argon2.mjs <golden.json> <candidate.json>");
	process.exit(2);
}

const golden = load(goldenPath);
const candidate = load(candidatePath);

const byId = (arr) => new Map(arr.map((r) => [r.id, r]));
const g = byId(golden);
const c = byId(candidate);
const ids = [...new Set([...g.keys(), ...c.keys()])].sort();

let failures = 0;
let controlsOk = 0;
const line = "-".repeat(72);
console.log(line);
console.log(`argon2 derivation equivalence: ${goldenPath}  vs  ${candidatePath}`);
console.log(line);

for (const id of ids) {
	const isControl = id.startsWith("CONTROL-");
	const a = g.get(id);
	const b = c.get(id);

	if (!a || !b) {
		console.log(`MISSING  ${id}  (golden=${!!a} candidate=${!!b})`);
		if (!isControl) failures++;
		continue;
	}
	if (!a.ok || !b.ok) {
		console.log(`ERRORED  ${id}  golden.ok=${a.ok} candidate.ok=${b.ok}`);
		console.log(`         golden.error=${a.error ?? "-"} | candidate.error=${b.error ?? "-"}`);
		if (!isControl) failures++;
		continue;
	}

	const rawSame = a.rawHash === b.rawHash;
	const encSame = a.encodedHash === b.encodedHash;

	if (isControl) {
		// Controls are expected to DIVERGE; a difference is the pass condition.
		if (!rawSame || !encSame) {
			console.log(`CONTROL  ${id}  diverged as expected (footgun confirmed)`);
			controlsOk++;
		} else {
			console.log(`CONTROL  ${id}  WARNING: outputs matched but were expected to differ - investigate`);
		}
		continue;
	}

	if (rawSame && encSame) {
		console.log(`PASS     ${id}`);
	} else {
		failures++;
		console.log(`FAIL     ${id}  ${!rawSame ? "rawHash DIFFERS " : ""}${!encSame ? "encodedHash DIFFERS" : ""}`);
		if (!rawSame) {
			console.log(`           golden.rawHash    = ${a.rawHash}`);
			console.log(`           candidate.rawHash = ${b.rawHash}`);
		}
		if (!encSame) {
			console.log(`           golden.encodedHash    = ${a.encodedHash}`);
			console.log(`           candidate.encodedHash = ${b.encodedHash}`);
		}
	}
}

console.log(line);
if (failures === 0) {
	console.log(`RESULT: SAFE - all must-match vectors identical. Controls diverged as expected: ${controlsOk}.`);
	console.log("Derivation is stable across the upgrade; re-provisioned keys will match.");
	process.exit(0);
} else {
	console.log(`RESULT: STOP - ${failures} must-match vector(s) drifted. Re-provisioned keys would NOT match production.`);
	console.log("Do not ship the argon2 upgrade until this is resolved (check argon2 version 0x10 vs 0x13, params, salt encoding).");
	process.exit(1);
}
