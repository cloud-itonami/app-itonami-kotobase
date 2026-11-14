# Persisted frontier and nonce with Forest receipt publication, 2026-11-14

The hosted fixture calls `governed-read/read-persistent!` using a Forest-backed
frontier and nonce store, then the explicit Otehon ports publication sink.
The signed basis and policy come from the existing Prolly dataset. A host
verifier checks the signature, checkpoint, location and policy binding; a
fixture TOFU initialization establishes the frontier. An actual Datalog
query returned three rows. A second call with the same nonce was refused
before the evaluator or receipt publisher ran again. A fresh JVM with a
nonexistent writer recovered the receipt ref and verified its signature and
the signed basis.

A separate store forced the publication verifier to refuse after nonce
consumption. A fresh read-only JVM found no receipt ref and did find the
scoped nonce ref. This demonstrates the actual separate-operation boundary:
failure can consume a nonce without publishing a receipt. It does not prove
fresh-process replay by invoking the nonce consumer, or validate the marker
contents in this negative read. An initial trial was refused because its
verifier returned nil after successful checks; it was fixed to return literal
true. All trial stores and staging artifacts are retained.

The script is `governed_ports_persistent.clj`, SHA-256
`8394358baba705701f4f453f982a47baa1482c6cb27b7d853702b8b5781ddc50`.
The retained scratch wrappers `run-governed-persistent.sh` and
`run-governed-persistent-readonly.sh` use the classpath and environment from
`GOVERNED-PORTS-READ-20261114.md`; `GOVERNED_PORTS_LABEL` selects a fresh
store and `GOVERNED_REJECT=1` forces failure. `nonce-present` and `no-ref`
read the corresponding refs without a writer.

This is hosted macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5, with fixture
principal, authority decision, keys and clock. It uses `read-persistent!`, not
the registry-bound entry. TOFU is not an independent anti-rollback witness.
The pre-CAS check verifies the receipt read path rather than every possible
DAG reference. The run is not two-Pack, OS reboot, Native or production
qualification, and nonce and receipt publication are not atomic together.
