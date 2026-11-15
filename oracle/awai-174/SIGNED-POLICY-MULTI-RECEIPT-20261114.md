# Signed multi-row policy basis and Forest receipts, 2026-11-14

A hosted fixture now composes the previously separate experiments. It opens the `signed-basis` ref from `SIGNED-POLICY-FOREST-MULTI-20261114.md`, restores the Prolly checkpoint and policy from that physical root, and binds the exact signed-basis envelope CID, physical root and policy CID into the execution manifest's `:location/manifest`. Before governed evaluation, the driver invokes the existing signed-basis verifier with a location callback that checks this binding, fixture issuer signature, checkpoint and policy. It verifies an actual Dango capability, passes the actual admitted Datalog clauses to the recovered three-row source and applies the recovered policy filter. The allowed query returns three rows, a limit-2 query is refused with no receipt ref, and a wrong-resource Dango decision is denied before evaluation with a denial receipt.

For each allow/deny receipt, a private bounded transaction overlay stages the request and manifest plus causal receipt blocks. Its intermediate Kotobase storage acknowledgement is private and does **not** mean the overlay is durable; the host returns the acknowledgement only after Forest publication and conditional receipt-ref CAS, persistent readback, and verification. A semantic callback before ref publication verifies request/manifest content IDs, manifest/request binding, Ed25519 signatures and the signed basis/location/policy. A fresh JVM with no writer executable recovers both receipts by refs, verifies the entire bundle, and rejects withheld request bytes, changed basis location, changed policy and changed basis signature. The stored public key and receipt CIDs in `ids.edn` are a fixture discovery sidecar, not a trust anchor.

```clojure
{:mode :write, :signed-basis :verified, :policy :recovered, :rows 3, :overflow :refused, :dango-denial :receipted, :receipts :forest-ref, :pass true}
{:overflow-ref :absent, :mode :read, :missing-request :refused, :allow :verified, :policy :recovered, :pass true, :deny :verified, :signed-basis :verified, :wrong-basis-policy-signature :refused}
```

This is a hosted oracle composition, not an Awai Otehon service entry or Native execution. The issuer and Dango roots are fixed fixture keys, the execution signing key is ephemeral, nonce/time are fixtures, encryption is identity, and the sidecar has no authenticated or rollback-resistant root. The basis ref and receipt refs are on separate local stores; there is no cross-store atomicity or independent witness. Source is a single physical Forest file; each receipt is one bounded Forest group. A source power cut, OS cache loss, independent-node failure, GiB-scale query and production credential provenance remain untested. The statically declared output limit does not constrain intermediate work. The staging area was retained and used about 130 MiB (two private pinned tool copies), versus the earlier direct receipt experiment's 389 MiB; different fixtures and a single uncontrolled run are not a performance benchmark.

On macOS ARM, OpenJDK 21.0.1 / Clojure 1.12.5; source SHA-256 `02c788bc8a402d0931701337e284ad449035d4f56ce7682e3fe3afa09b41b4e7`; generated IDs/public-key sidecar SHA-256 `04f5afdc9681e67bd9ddf2e9d526612c9f11abbb79ff024b90af5a0d82d546de`. No downloads. Tool and dependency overlays follow `SIGNED-POLICY-FOREST-MULTI-20261114.md`. The `write` command exclusively creates `MULTI_FOREST_OUTPUT/integrated-receipts`; preserve existing outputs and use a fresh copy of the signed basis fixture for a new write.

```sh
# Set task_kotobase, task_cp and FOREST_TOOL as in GOVERNED-FOREST-MULTIROW-20261114.md.
export KOTOBASE_ORACLE_ROOT="$task_kotobase"
export MULTI_FOREST_OUTPUT="$task_kotobase/.agent-output/multi-policy-signed-01"
java -cp "$task_cp" clojure.main "$task_kotobase/oracle/awai-174/signed_policy_multi_receipt.clj" write
FOREST_TOOL=/nonexistent/no-forest-writer java -cp "$task_cp" clojure.main "$task_kotobase/oracle/awai-174/signed_policy_multi_receipt.clj" read
```
