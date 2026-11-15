# Hosted canonical receipt callbacks, 2026-11-14

`kotobase.hosted-receipt-publication/callbacks` supplies synchronous hosted
builder and verifier functions for Otehon's explicit receipt publication
adapter. The builder uses `peer/commit!` on provisional ports; the verifier
requires a host-authenticated bundle and reads the exact receipt, stream and
basis through a host-supplied persisted-block database before ref CAS. The
host owns staging, encryption, verifier, trust, read-only database and all
closure semantics. These callbacks do not provide Native execution or a
distributed transaction.

On macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5, a hosted Forest fixture used
these product callbacks together with awai-os `6d83521`,
`read-registry-persistent!`, a persisted frontier and nonce, fixture Ed25519
registry, Prolly policy and Datalog. Three rows were returned, unregistered
key and replay refused, and a fresh JVM without a writer verified the receipt
and basis. A separate store injected a verifier read failure: it left no
receipt ref, while the nonce ref remained. The initial source had an unmatched
delimiter and did not compile; the corrected run passed. The local staged JVM
unit oracle also passed 1 test / 4 assertions for invalid configuration,
invalid builder input and refusal before readback. Test imports were adapted
to clojure.test as described in `PUBLISHED-RECEIPT-SINK-20261114.md`.

Source SHA-256 `1bdb58357a9609d924f8ee6c2416f8c97fda80ff80d9ec1f80f17328a11287ad`;
fixture SHA-256 `8414baf686f6e59223c31083cb6f76c539b8ccc3f7c07b8ce298e88e73e8bdfa`.
The retained `run-governed-hosted.sh` and `run-governed-hosted-readonly.sh`
wrappers follow `GOVERNED-PORTS-ADAPTER-20261114.md`; the staged product source
was first on the classpath. No dependency download occurred.

Registry, identity, keys, authority, clock, staging and bundle verifier remain
fixtures. The verifier covers receipt readback, not arbitrary DAG closure.
This is not a two-Pack, crash/OS-reboot, Native or production qualification.
Nonce and receipt remain separate operations and failed publication can
consume a nonce.
