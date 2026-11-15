# Governed read through Otehon publication adapter, 2026-11-14

The hosted registry-bound persistent fixture now uses Otehon
`receipt-publication-adapter` from awai-os commit `6d83521` instead of
constructing its publication/ref callbacks itself. The host builder stages
the request, manifest and receipt commit; its verifier checks the signed
bundle and reads the candidate receipt from persisted blocks before CAS.
Fixture-scoped Ed25519 registry, persisted frontier/nonce, recovered Prolly
policy and Datalog returned three rows; empty registry and nonce replay were
refused. A separate JVM with no writer recovered and verified the receipt and
signed basis.

In a different new store, withholding the verifier's commit target caused
`verifier target missing`; fresh read-only processes found no receipt ref and
did find the nonce ref. This records the nonce/receipt transaction boundary,
not an atomic transaction between them. Host: macOS ARM OpenJDK 21.0.1 /
Clojure 1.12.5. No download occurred. Script SHA-256:
`5f4138a2cd2e330a933f62c57d44724b04133d86e855cfda86a2d830536e3625`.
The retained `run-governed-adapter.sh` and `run-governed-adapter-readonly.sh`
wrappers follow the environment in `GOVERNED-PORTS-REGISTRY-20261114.md`.

The adapter is product source, but builder, verifier, registry and identity
here are fixture code; the pre-CAS check covers the blocks read for receipt
recovery, not arbitrary DAG closure. This host run is not a two-Pack, crash,
OS-reboot, Native, independently trusted or production qualification result.
