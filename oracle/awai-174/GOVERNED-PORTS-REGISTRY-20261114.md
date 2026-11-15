# Registry-bound persistent governed read, 2026-11-14

The hosted Forest/Prolly fixture now invokes
`governed-read/read-registry-persistent!`: the registry selects an Ed25519
fixture key by issuer role, tenant and epoch, checks the issuer/key binding,
and verifies the signed logical basis, physical checkpoint and manifest
location. A persisted frontier and nonce are used; the policy comes from the
recovered Prolly basis. The actual Datalog query returned three rows and the
Otehon ports publisher committed the receipt. A repeated nonce was refused
without a second evaluation or publication. An empty registry specifically
failed with `:no-key-registered` before evaluation or publication.

On macOS ARM, OpenJDK 21.0.1 / Clojure 1.12.5, a fresh JVM with the writer
absent recovered the receipt ref and verified receipt signature and signed
basis. Script SHA-256:
`85d39c1ea65e678a638d29abc574d027a6d3259a5ac36e6ff07bc9f1443db33e`.
The retained `run-governed-registry.sh` and `run-governed-registry-readonly.sh`
wrappers and classpath build on `GOVERNED-PORTS-PERSISTENT-20261114.md`;
`GOVERNED_PORTS_LABEL` selects a fresh task-owned store. No download occurred.

The registry, principal, authority decision, issuer, keys and clock are
fixtures, and frontier initialization is TOFU. They are not an independently
authenticated production trust root or rollback witness. Fresh-process
readback uses the saved test public key and does not itself rerun registry
admission or nonce consumption. Receipt and nonce remain separate operations;
the prior failure test demonstrates the nonce can be consumed without a
receipt. This is hosted local execution, not a product host adapter, general
DAG closure verification, two-Pack, OS-reboot, Native or production evidence.
