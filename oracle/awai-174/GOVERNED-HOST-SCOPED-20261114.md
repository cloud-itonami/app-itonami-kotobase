# Hosted scoped authority composition, 2026-11-14

`kotobase.governed-host/read-registry-signed-authority!` is a synchronous JVM-only library entry. It constructs the versioned scoped issuer-attestation verifier and exact decision-commit authenticator from explicit host inputs, then calls the registry-bound linked read. Host option keys are exact; callers cannot provide an alternative authority-verifier callback through this entry. It does not select a registry, issuer policy, authenticated identity, clock, current revocation state, rollback witness, storage or Datalog policy; those remain host obligations. It is not yet called by an Awai Otehon service and is not a Native fallback.

On macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5, `governed_host_scoped_memory.clj` used the recovered Forest/Prolly basis, real causal-trust algorithm, Ed25519 test issuer, scoped in-memory registry and in-memory nonce/receipt stores. It verified the successful linked read, query-policy denial receipt, missing key, rejected issuer, attempted verifier override, bad link signature and replay refusal. Output:

```clojure
{:host-composition :verified, :signed-authority :scoped, :allow :verified, :deny :receipted, :override :refused, :replay :refused, :backend :memory, :pass true}
```

The classpath/environment follows `GOVERNED-SCOPED-AUTHORITY-20261114.md`, adding the source copied as `.cljc` into a leading task-local `governed-host` classpath directory. Local JVM reference regression across authority attestation, governed execution, governed read and execution keys passed 28 tests / 200 assertions / 0 failures. No dependencies downloaded or Forest writes. SHA-256: source `0be45945de8878a71bc3316d43cbb09c541ca76ba7c1ff4c36789ef04843ff91`; probe `d6dc18b99317e49caa0725d344d62ad13c4dace8259acf612da78bb9b58c2185`. This is not production issuer trust, persistent receipt for this entry, restart, Native or CI evidence.
